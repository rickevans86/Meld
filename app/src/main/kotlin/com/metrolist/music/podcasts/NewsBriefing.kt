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
    /** Stable key saved in settings: "apple:<id>" for built-in outlets, "feed:<url>" for followed podcasts. */
    val key: String,
    val name: String,
    val region: String,
    val artworkUrl: String? = null,
)

data class NewsSourceChoice(val source: NewsSource, val enabled: Boolean)

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
            val available = BUILT_IN + followed.map {
                NewsSource("feed:${it.feedUrl}", it.title, "Podcasts you follow", it.artworkUrl)
            }
            val enabledKeys = (saved ?: DEFAULT_SOURCES.joinToString(SEPARATOR))
                .split(SEPARATOR).filter { it.isNotBlank() }
            val enabled = enabledKeys.mapNotNull { key -> available.firstOrNull { it.key == key } }
            enabled.map { NewsSourceChoice(it, true) } +
                available.filter { it !in enabled }.map { NewsSourceChoice(it, false) }
        }

    suspend fun setEnabled(keysInOrder: List<String>) {
        context.dataStore.edit { it[NewsSourcesKey] = keysInOrder.joinToString(SEPARATOR) }
    }

    /**
     * The newest bulletin from each enabled source, in order, ready to play from the start.
     * Bulletins older than two days are skipped so a quiet feed doesn't play stale news.
     */
    suspend fun latestBulletins(sources: List<NewsSource>): List<Song> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MS
        val bulletins = coroutineScope {
            sources.map { source ->
                async {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching {
                            val feedUrl = feedUrlFor(source) ?: return@runCatching null
                            val feed = directory.feed(PodcastShow(feedUrl, source.name))
                            val newest = feed.episodes.firstOrNull()
                                ?.takeIf { (it.publishedAt ?: System.currentTimeMillis()) >= cutoff }
                                ?: return@runCatching null
                            directory.prepare(feed.copy(episodes = listOf(newest))).firstOrNull()
                        }.onFailure { Timber.tag(TAG).w(it, "No bulletin from ${source.name}") }.getOrNull()
                    }
                }
            }.awaitAll()
        }.filterNotNull()
        // Each briefing should start every bulletin from the beginning
        bulletins.forEach { database.updatePlaybackPosition(it.id, null) }
        bulletins
    }

    private fun feedUrlFor(source: NewsSource): String? {
        if (source.key.startsWith("feed:")) return source.key.removePrefix("feed:")
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

        val BUILT_IN = listOf(
            NewsSource("apple:1668721389", "ABC News Top Stories", "Australia"),
            NewsSource("apple:1527948302", "SBS News Updates", "Australia"),
            NewsSource("apple:135067274", "BBC Global News", "International"),
            NewsSource("apple:121493675", "NPR News Now", "International"),
        )

        private val DEFAULT_SOURCES = listOf("apple:1668721389", "apple:1527948302", "apple:135067274")

        /** Used only if Apple's directory can't be reached. */
        private val FALLBACK_FEEDS = mapOf(
            "1668721389" to "https://www.abc.net.au/feeds/101858056/podcast.xml",
            "135067274" to "https://podcasts.files.bbci.co.uk/p02nq0gn.rss",
            "121493675" to "https://feeds.npr.org/500005/podcast.xml",
        )
    }
}
