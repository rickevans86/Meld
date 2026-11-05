/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.NewsSourcesKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A news outlet whose short audio bulletins are published as a podcast feed. */
data class NewsSource(
    /**
     * Stable key saved in settings: "apple:<id>" for outlets with a known Apple Podcasts id,
     * "search:<name>::<publisher>" for ones looked up by name, "feed:<url>" for followed podcasts.
     */
    val key: String,
    val name: String,
    val region: String,
    val artworkUrl: String? = null,
)

data class NewsSourceChoice(val source: NewsSource, val enabled: Boolean)

/** How news sources are listed and played: in the app, on Android Auto and in "Play the news". */
enum class NewsSort { YOUR_ORDER, LATEST, NAME, REGION }

val NewsSortKey = androidx.datastore.preferences.core.stringPreferencesKey("newsSort")
val NewsSortReversedKey = androidx.datastore.preferences.core.booleanPreferencesKey("newsSortReversed")

/** Whether a source can be played right now. */
enum class SourceStatus {
    /** Has a bulletin from the last two days. */
    PLAYABLE,
    /** Has a public feed, but nothing new in the last two days, so the briefing skips it for now. */
    QUIET,
    /** No public podcast feed (made only for Google News, or not found). */
    NO_FEED,
}

/** A source's newest bulletin, ready to play. */
data class Bulletin(val song: Song, val publishedAt: Long?, val artworkUrl: String?)

/**
 * Plays the latest bulletin from each chosen outlet back to back, like a car's news briefing.
 * Built-in outlets are looked up by Apple Podcasts id so a moved feed keeps working.
 */
@Singleton
class NewsBriefing
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    private val directory: PodcastDirectory,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val feedUrls = ConcurrentHashMap<String, String>()

    /** All sources in the user's order, enabled ones first; followed podcasts can be switched on too. */
    val choices: Flow<List<NewsSourceChoice>> =
        combine(context.dataStore.data.map { it[NewsSourcesKey] }, directory.followed) { saved, followed ->
            val available = BUILT_IN + CATALOGUE + followed.map {
                NewsSource("feed:${it.feedUrl}", it.title, "Podcasts you follow", it.artworkUrl)
            }.distinctBy { it.key }
            val enabledKeys = (saved ?: DEFAULT_SOURCES.joinToString(SEPARATOR))
                .split(SEPARATOR).filter { it.isNotBlank() }
            val enabled = enabledKeys.mapNotNull { key -> available.firstOrNull { it.key == key } }
            enabled.map { NewsSourceChoice(it, true) } +
                available.filter { it !in enabled }.map { NewsSourceChoice(it, false) }
        }

    val sortSetting: Flow<Pair<NewsSort, Boolean>> = context.dataStore.data.map { prefs ->
        (prefs[NewsSortKey]?.let { name -> NewsSort.entries.firstOrNull { it.name == name } } ?: NewsSort.YOUR_ORDER) to
            (prefs[NewsSortReversedKey] ?: false)
    }

    suspend fun setSort(sort: NewsSort) = context.dataStore.edit { it[NewsSortKey] = sort.name }

    suspend fun setSortReversed(reversed: Boolean) = context.dataStore.edit { it[NewsSortReversedKey] = reversed }

    /** When each source last published, remembered from earlier loads (for "Latest bulletin"). */
    private val latestFile = java.io.File(context.filesDir, "news_latest.json")
    private val latestSerializer = kotlinx.serialization.builtins.MapSerializer(
        kotlinx.serialization.serializer<String>(),
        kotlinx.serialization.serializer<Long>(),
    )
    private val latestPublished = ConcurrentHashMap<String, Long>(
        runCatching { json.decodeFromString(latestSerializer, latestFile.readText()) }.getOrDefault(emptyMap()),
    )

    /** Last check of every source, so the News sources screen can mark ones that can't be played. */
    private val statusFile = java.io.File(context.filesDir, "news_status.json")
    private val statusSerializer = kotlinx.serialization.builtins.MapSerializer(
        kotlinx.serialization.serializer<String>(),
        kotlinx.serialization.serializer<String>(),
    )
    private val statusChecked = ConcurrentHashMap<String, Long>()
    private val _statuses = kotlinx.coroutines.flow.MutableStateFlow<Map<String, SourceStatus>>(
        runCatching { json.decodeFromString(statusSerializer, statusFile.readText()) }.getOrDefault(emptyMap())
            .mapNotNull { (key, value) ->
                val (name, at) = value.split('|').let { it[0] to it.getOrNull(1)?.toLongOrNull() }
                val status = SourceStatus.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
                statusChecked[key] = at ?: 0L
                key to status
            }.toMap(),
    )
    val statuses: kotlinx.coroutines.flow.StateFlow<Map<String, SourceStatus>> = _statuses

    private fun saveStatus(key: String, status: SourceStatus) {
        statusChecked[key] = System.currentTimeMillis()
        _statuses.value = _statuses.value + (key to status)
        runCatching {
            statusFile.writeText(
                json.encodeToString(statusSerializer, _statuses.value.mapValues { (k, v) -> "${v.name}|${statusChecked[k] ?: 0L}" }),
            )
        }
    }

    /**
     * Checks one source the way the briefing plays it: finds its feed and looks for a bulletin from the
     * last two days. Returns null when it couldn't be checked (no connection), so it isn't marked wrongly.
     */
    suspend fun checkSource(source: NewsSource): SourceStatus? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            val feedUrl = try {
                feedUrlFor(source)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return@withTimeoutOrNull null
            }
            if (feedUrl == null) {
                // An Apple id that won't look up means the directory couldn't be reached, not a missing feed
                return@withTimeoutOrNull if (source.key.startsWith("search:")) SourceStatus.NO_FEED else null
            }
            val newest = runCatching { directory.feed(PodcastShow(feedUrl, source.name)).episodes.firstOrNull() }
                .getOrElse { return@withTimeoutOrNull null }
            rememberPublished(source.key, newest?.publishedAt)
            val recent = newest != null && (newest.publishedAt ?: System.currentTimeMillis()) >= System.currentTimeMillis() - MAX_AGE_MS
            if (recent) SourceStatus.PLAYABLE else SourceStatus.QUIET
        }?.also { saveStatus(source.key, it) }
    }

    /** Checks every source that hasn't been checked recently, a few at a time; [onProgress] gets (done, total). */
    suspend fun checkSources(sources: List<NewsSource>, force: Boolean = false, onProgress: (Int, Int) -> Unit = { _, _ -> }) {
        val now = System.currentTimeMillis()
        val due = sources.filter { force || now - (statusChecked[it.key] ?: 0L) > STATUS_MAX_AGE_MS }
        if (due.isEmpty()) return
        val gate = kotlinx.coroutines.sync.Semaphore(6)
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        onProgress(0, due.size)
        coroutineScope {
            due.map { source ->
                async {
                    gate.acquire()
                    try {
                        checkSource(source)
                    } finally {
                        gate.release()
                        onProgress(done.incrementAndGet(), due.size)
                    }
                }
            }.awaitAll()
        }
    }

    private fun rememberPublished(key: String, at: Long?) {
        if (at == null || latestPublished[key] == at) return
        latestPublished[key] = at
        runCatching { latestFile.writeText(json.encodeToString(latestSerializer, latestPublished.toMap())) }
    }

    /**
     * Orders sources: your order (as set on News sources), newest bulletin first, A–Z, or by region.
     * [latest] adds publish times known right now on top of the remembered ones.
     */
    fun sortSources(
        sources: List<NewsSource>,
        sort: NewsSort,
        reversed: Boolean,
        latest: Map<String, Long?> = emptyMap(),
    ): List<NewsSource> {
        val sorted = when (sort) {
            NewsSort.YOUR_ORDER -> sources
            NewsSort.NAME -> sources.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            NewsSort.LATEST -> sources.sortedByDescending { latest[it.key] ?: latestPublished[it.key] ?: Long.MIN_VALUE }
            NewsSort.REGION -> sources.sortedWith(
                compareBy<NewsSource> { CATEGORIES.indexOf(it.region).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
        }
        return if (reversed) sorted.reversed() else sorted
    }

    /** Enabled sources in the chosen order; Android Auto uses this so it matches the app. */
    suspend fun sortedEnabledSources(): List<NewsSource> {
        val (sort, reversed) = sortSetting.first()
        return sortSources(choices.first().filter { it.enabled }.map { it.source }, sort, reversed)
    }

    /**
     * Your chosen news sources, in order, as OPML (the podcast list format), so other podcast apps can
     * open it too. Each outline also carries Wavelength's own key and category for restoring here.
     */
    suspend fun exportOpml(): String {
        fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val sources = choices.first().filter { it.enabled }.map { it.source }
        val feeds = coroutineScope {
            sources.map { source ->
                async { withTimeoutOrNull(10_000) { runCatching { feedUrlFor(source) }.getOrNull() } }
            }.awaitAll()
        }
        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
            append("<opml version=\"1.0\">\n  <head>\n    <title>Wavelength News Sources</title>\n  </head>\n  <body>\n")
            append("    <outline text=\"news\">\n")
            sources.zip(feeds).forEach { (source, feed) ->
                append("      <outline type=\"rss\" text=\"").append(esc(source.name)).append('"')
                feed?.let { append(" xmlUrl=\"").append(esc(it)).append('"') }
                append(" wavelengthKey=\"").append(esc(source.key)).append('"')
                append(" category=\"").append(esc(source.region)).append("\" />\n")
            }
            append("    </outline>\n  </body>\n</opml>\n")
        }
    }

    /** Result of a news import: sources switched on, and ones skipped because they were already on. */
    data class NewsImport(val added: Int, val skipped: Int)

    /**
     * Switches on the sources in a news OPML file, added after your current ones in the file's order.
     * Sources already on are skipped. A feed Wavelength doesn't list (e.g. from another app) is followed
     * as a podcast and switched on under "Podcasts you follow".
     */
    suspend fun importOpml(input: java.io.InputStream): NewsImport {
        val parser = android.util.Xml.newPullParser().apply {
            setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        data class Entry(val key: String?, val url: String?, val name: String?)
        val entries = mutableListOf<Entry>()
        while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != org.xmlpull.v1.XmlPullParser.START_TAG || !parser.name.equals("outline", ignoreCase = true)) continue
            val attrs = (0 until parser.attributeCount).associate { parser.getAttributeName(it).lowercase(java.util.Locale.ROOT) to parser.getAttributeValue(it) }
            val key = attrs["wavelengthkey"]?.trim()?.takeIf { it.isNotEmpty() }
            val url = attrs["xmlurl"]?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }
            if (key == null && url == null) continue
            entries += Entry(key, url, (attrs["text"] ?: attrs["title"])?.trim())
        }
        val available = choices.first().map { it.source.key }.toSet()
        val enabled = choices.first().filter { it.enabled }.map { it.source.key }.toMutableList()
        var added = 0
        var skipped = 0
        entries.forEach { entry ->
            val key = when {
                entry.key != null && (entry.key in available || entry.key.startsWith("feed:")) -> entry.key
                entry.url != null -> "feed:${entry.url}"
                else -> null
            } ?: return@forEach
            if (key in enabled) {
                skipped++
                return@forEach
            }
            if (key.startsWith("feed:")) {
                val url = key.removePrefix("feed:")
                if (!directory.isFollowed(url)) directory.setFollowed(PodcastShow(url, entry.name ?: url), true)
            }
            enabled += key
            added++
        }
        if (added > 0) setEnabled(enabled)
        return NewsImport(added, skipped)
    }

    suspend fun setEnabled(keysInOrder: List<String>) {
        context.dataStore.edit { it[NewsSourcesKey] = keysInOrder.joinToString(SEPARATOR) }
    }

    /**
     * The newest bulletin from each enabled source, in order, ready to play from the start.
     * Bulletins older than two days are skipped so a quiet feed doesn't play stale news.
     */
    suspend fun latestBulletins(sources: List<NewsSource>): List<Song> = withContext(Dispatchers.IO) {
        val bulletins = coroutineScope {
            sources.map { source -> async { latestBulletin(source)?.song } }.awaitAll()
        }.filterNotNull()
        // Each briefing should start every bulletin from the beginning
        bulletins.forEach { database.updatePlaybackPosition(it.id, null) }
        bulletins
    }

    /** The newest bulletin from one source with when it was published, or null if there's no recent one. */
    suspend fun latestBulletin(source: NewsSource): Bulletin? = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            runCatching {
                val feedUrl = feedUrlFor(source) ?: return@runCatching null
                val feed = directory.feed(PodcastShow(feedUrl, source.name))
                val newest = feed.episodes.firstOrNull()
                    ?.takeIf { (it.publishedAt ?: System.currentTimeMillis()) >= cutoff }
                    ?: return@runCatching null
                rememberPublished(source.key, newest.publishedAt)
                // Label the bulletin with the source's name (as in News sources), not the feed's publisher
                directory.prepare(feed.copy(show = feed.show.copy(author = source.name), episodes = listOf(newest))).firstOrNull()
                    ?.let { Bulletin(it, newest.publishedAt, feed.show.artworkUrl) }
            }.onFailure { Timber.tag(TAG).w(it, "No bulletin from ${source.name}") }.getOrNull()
        }
    }

    /** True if the source publishes a public feed this app can play. */
    suspend fun isAvailable(source: NewsSource): Boolean =
        withContext(Dispatchers.IO) { runCatching { feedUrlFor(source) != null }.getOrDefault(false) }

    suspend fun restart(songId: String) = withContext(Dispatchers.IO) { database.updatePlaybackPosition(songId, null) }

    private suspend fun feedUrlFor(source: NewsSource): String? {
        if (source.key.startsWith("feed:")) return source.key.removePrefix("feed:")
        if (source.key.startsWith("search:")) {
            feedUrls[source.key]?.let { return it }
            val (name, publisher) = source.key.removePrefix("search:").split("::").let { it[0] to it.getOrNull(1) }
            return directory.findFeed(name, publisher)?.feedUrl?.also { feedUrls[source.key] = it }
        }
        val appleId = source.key.removePrefix("apple:")
        feedUrls[appleId]?.let { return it }
        val looked = runCatching {
            http.newCall(Request.Builder().url("https://itunes.apple.com/lookup?id=$appleId").build()).execute().use {
                if (!it.isSuccessful) return@use null
                val results = json.parseToJsonElement(it.body?.string().orEmpty()).jsonObject["results"]?.jsonArray
                (results?.firstOrNull() as? JsonObject)?.get("feedUrl")?.let { url -> (url as? JsonPrimitive)?.contentOrNull }
            }
        }.getOrNull()
        val url = looked ?: FALLBACK_FEEDS[appleId]
        if (url != null) feedUrls[appleId] = url
        return url
    }

    companion object {
        private const val TAG = "NewsBriefing"
        private const val SEPARATOR = "\n"
        private const val MAX_AGE_MS = 2 * 24 * 60 * 60 * 1000L
        private const val SOURCE_TIMEOUT_MS = 20_000L
        private const val STATUS_MAX_AGE_MS = 6 * 60 * 60 * 1000L

        val BUILT_IN = listOf(
            NewsSource("apple:1303342739", "7NEWS", "Australia"),
            NewsSource("search:From the Newsroom::news.com.au", "news.com.au From the Newsroom", "Australia"),
            NewsSource("search:Global's Newsroom::Global", "Global's Newsroom", "International"),
            NewsSource("apple:1461999702", "7am", "Australia"),
            NewsSource("apple:1668721389", "ABC News Top Stories", "Australia"),
            NewsSource("apple:1527948302", "SBS News Updates", "Australia"),
            NewsSource("apple:73329715", "SBS News In Depth", "Australia"),
            NewsSource("apple:1128428812", "Daily Tech Headlines", "Technology"),
            NewsSource("search:Engadget::Engadget", "Engadget", "Technology"),
            NewsSource("apple:135067274", "BBC Global News", "International"),
            NewsSource("apple:121493675", "NPR News Now", "International"),
        )

        /**
         * The rest of Google News' audio line-up, found by name in the public podcast directory.
         * Some of these are made only for Google and have no public feed; those are flagged when ticked.
         */
        private val CATALOGUE_NAMES = listOf(
            "The Intelligence from The Economist" to "International",
            "Reuters TV (U.S.)" to "International",
            "BBC News Summary" to "International",
            "DW Newsbrief" to "International",
            "Sky News" to "International",
            "Reuters 5-minute News Briefing (Global)" to "International",
            "NPR World" to "International",
            "CBC News World Report" to "International",
            "CNN 5 Things" to "International",
            "NHK WORLD-Japan News" to "International",
            "KBS WORLD Radio News" to "International",
            "RNZ Headlines" to "International",
            "Post Reports" to "International",
            "POLITICO Playbook Audio Briefing" to "International",
            "The Audio Long Read" to "International",
            "Star News" to "International",
            "ABC NEWS Briefing" to "Australia",
            "SBS News" to "Australia",
            "News24" to "Australia",
            "7NEWS Just In" to "Australia",
            "Nine News Bulletin" to "Australia",
            "NOVA News" to "Australia",
            "The Quicky News Headlines" to "Australia",
            "The Quicky" to "Australia",
            "Sunrise" to "Australia",
            "Please Explain" to "Australia",
            "Full Story" to "Australia",
            "The Front" to "Australia",
            "Your News Now" to "Australia",
            "Smooth News" to "Australia",
            "The Morning Show" to "Australia",
            "Ben Fordham Live" to "Australia",
            "The Ray Hadley Morning Show - News Highlights" to "Australia",
            "Drive with Tom Elliott - News Highlights" to "Australia",
            "3AW Mornings with Neil Mitchell - News Highlights" to "Australia",
            "3AW Breakfast - News Highlights" to "Australia",
            "3AW Afternoons with Dee Dee - News Highlights" to "Australia",
            "Drive with Jim Wilson - News Highlights" to "Australia",
            "Perth Live with Oliver Peterson - News Highlights" to "Australia",
            "6PR Breakfast with Gareth Parker - News Highlights" to "Australia",
            "6PR Mornings - News Highlights" to "Australia",
            "4BC Breakfast with Neil Breen - News Highlights" to "Australia",
            "4BC Afternoons - News Highlights" to "Australia",
            "Nights with John Stanley - News Highlights" to "Australia",
            "Australia Overnight (Weekends) - News Highlights" to "Australia",
            "Weekend Breakfast with Darren James" to "Australia",
            "Weekends with Peter Fegan - News Highlights" to "Australia",
            "News Desk by The West Australian" to "Australia",
            "ABC Kids News" to "Australia",
            "The Issue" to "Australia",
            "ABC NEWS Melbourne Briefing" to "Australian local",
            "ABC NEWS Sydney Briefing" to "Australian local",
            "ABC NEWS Brisbane Briefing" to "Australian local",
            "ABC NEWS Perth Briefing" to "Australian local",
            "ABC NEWS Adelaide Briefing" to "Australian local",
            "ABC NEWS Canberra Briefing" to "Australian local",
            "ABC NEWS Hobart Briefing" to "Australian local",
            "ABC NEWS Darwin Briefing" to "Australian local",
            "Triple M Melbourne News" to "Australian local",
            "Triple M Sydney News" to "Australian local",
            "Triple M Brisbane News" to "Australian local",
            "Triple M Perth News" to "Australian local",
            "Triple M Adelaide News" to "Australian local",
            "Triple M Newcastle News" to "Australian local",
            "Triple M Hobart News" to "Australian local",
            "Triple M Gold Coast News" to "Australian local",
            "Triple M Central Coast News" to "Australian local",
            "Triple M Townsville News" to "Australian local",
            "LiSTNR Melbourne News" to "Australian local",
            "LiSTNR Sydney News" to "Australian local",
            "LiSTNR Perth News" to "Australian local",
            "Fox FM Melbourne News" to "Australian local",
            "2Day FM Sydney News" to "Australian local",
            "Hit105 Brisbane News" to "Australian local",
            "Mix 94.5 Perth News" to "Australian local",
            "SAFM Adelaide News" to "Australian local",
            "Hit Newcastle News" to "Australian local",
            "Hit Central Coast News" to "Australian local",
            "Hit Gold Coast News" to "Australian local",
            "Hit Townsville News" to "Australian local",
            "Hit Hobart News" to "Australian local",
            "2CC News" to "Australian local",
            "Power FM News" to "Australian local",
            "Reuters Morning Bid" to "Business",
            "Bloomberg Market Minute" to "Business",
            "CNBC Markets Now" to "Business",
            "CommSec Market Update" to "Business",
            "CommSec Bulletin" to "Business",
            "Fear and Greed Business Headlines" to "Business",
            "Money News Market Wrap" to "Business",
            "2GB Money News - News Highlights" to "Business",
            "Business News by The West Australian" to "Business",
            "Front Row Entrepreneur" to "Business",
            "Transport Topics" to "Business",
            "WSJ Tech News Briefing" to "Technology",
            "TechCrunch" to "Technology",
            "CNBC Tech Check" to "Technology",
            "TechLinked" to "Technology",
            "Samsung Newsroom Global" to "Technology",
            "AI Automation Minute" to "Technology",
            "AI News You Should Know About" to "Technology",
            "SciTech Brief" to "Technology",
            "SCIENCE by SciTech Brief" to "Technology",
            "Let Me Google That" to "Technology",
            "The Instagram Stories" to "Technology",
            "Press Start Daily Gaming News" to "Technology",
            "IGN" to "Technology",
            "7NEWS Sport" to "Sport",
            "7NEWS Sport Headlines" to "Sport",
            "ABC NEWS Sport Briefing" to "Sport",
            "Wide World of Sports Update" to "Sport",
            "Wide World of Sports 6PR" to "Sport",
            "4BC Wide World of Sports - News Highlights" to "Sport",
            "Wide World of Sports (Sydney) - News Highlights" to "Sport",
            "Sports Update by The West Australian" to "Sport",
            "Sportsday - News Highlights" to "Sport",
            "3AW Football" to "Sport",
            "The Leadoff" to "Sport",
            "Entertainment Now" to "Entertainment & lifestyle",
            "7Life Latest Entertainment News" to "Entertainment & lifestyle",
            "The Spill" to "Entertainment & lifestyle",
            "Bollywood Hungama" to "Entertainment & lifestyle",
            "Healthy-ish" to "Entertainment & lifestyle",
            "The House of Wellness - News Highlights" to "Entertainment & lifestyle",
            "The Space" to "Entertainment & lifestyle",
        )

        val CATEGORIES = listOf("Australia", "Australian local", "International", "Business", "Technology", "Sport", "Entertainment & lifestyle")

        private val CATALOGUE: List<NewsSource> = run {
            val builtInNames = BUILT_IN.flatMap { listOf(it.name.lowercase(), it.name.lowercase().removePrefix("the ")) }.toSet()
            CATALOGUE_NAMES
                .filter { (name, _) -> name.lowercase() !in builtInNames }
                .map { (name, category) -> NewsSource("search:$name::", name, category) }
        }

        /** Mirrors a typical Android Auto news line-up: local first, then world and tech. */
        private val DEFAULT_SOURCES = listOf(
            "apple:1303342739",
            "search:Global's Newsroom::Global",
            "apple:1461999702",
            "apple:1668721389",
            "apple:1527948302",
            "apple:73329715",
            "apple:1128428812",
        )

        /** Used only if Apple's directory can't be reached. */
        private val FALLBACK_FEEDS = mapOf(
            "1668721389" to "https://www.abc.net.au/feeds/101858056/podcast.xml",
            "135067274" to "https://podcasts.files.bbci.co.uk/p02nq0gn.rss",
            "121493675" to "https://feeds.npr.org/500005/podcast.xml",
        )
    }
}
