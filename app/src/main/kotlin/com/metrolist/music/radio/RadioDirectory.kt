/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.radio

import android.content.Context
import com.metrolist.music.constants.RADIO_ID_PREFIX
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val favicon: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val tags: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
    val hls: Boolean = false,
    /** Where it came from when not Radio Browser, e.g. "TuneIn" or "Added by you". */
    val source: String? = null,
) {
    val songId get() = RADIO_ID_PREFIX + id

    /** "Australia · news, talk · AAC 64k" */
    val details: String
        get() = listOfNotNull(
            country?.takeIf { it.isNotBlank() },
            tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)?.joinToString(", ")?.takeIf { it.isNotEmpty() },
            listOfNotNull(codec?.takeIf { it.isNotBlank() && it != "UNKNOWN" }, bitrate.takeIf { it > 0 }?.let { "${it}k" })
                .joinToString(" ").takeIf { it.isNotEmpty() },
            source,
        ).joinToString(" · ")
}

/**
 * Internet radio from the free community Radio Browser directory (radio-browser.info):
 * searching stations, favourites, and turning stations into songs the player streams live.
 */
@Singleton
class RadioDirectory
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val favouritesFile = File(context.filesDir, "radio_favourites.json")
    private val starredFile = File(context.filesDir, "radio_starred.json")
    private val known = ConcurrentHashMap<String, RadioStation>()
    private val resolved = ConcurrentHashMap<String, Pair<String, Boolean>>()

    init {
        starredIds.value = runCatching {
            if (starredFile.exists()) json.decodeFromString(ListSerializer(String.serializer()), starredFile.readText()).toSet() else emptySet()
        }.getOrDefault(emptySet())
    }

    private val _favourites = MutableStateFlow(loadFavourites())

    /**
     * Your saved stations (the library), with the ones you've hearted first. Android Auto and the
     * Radio tab both show them in this order.
     */
    val favourites: kotlinx.coroutines.flow.StateFlow<List<RadioStation>> =
        kotlinx.coroutines.flow.combine(_favourites, starredIds) { list, starred -> sortStarred(list, starred) }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, sortStarred(_favourites.value, starredIds.value))

    private fun sortStarred(list: List<RadioStation>, starred: Set<String>) =
        list.filter { it.id in starred } + list.filterNot { it.id in starred }

    /** True when the station is hearted (shown at the top of your stations). */
    fun isStarred(stationId: String) = stationId in starredIds.value

    /** Hearts a station (and saves it to your stations), or takes the heart off. */
    fun setStarred(station: RadioStation, starred: Boolean) {
        if (starred && !isFavourite(station)) setFavourite(station, true)
        val ids = if (starred) starredIds.value + station.id else starredIds.value - station.id
        starredIds.value = ids
        runCatching { starredFile.writeText(json.encodeToString(ListSerializer(String.serializer()), ids.toList())) }
            .onFailure { Timber.tag(TAG).w(it, "Could not save radio hearts") }
    }

    /** True when the station with this song id is in your stations. */
    fun isSavedSong(songId: String) = _favourites.value.any { it.songId == songId }

    /** "Add to / Remove from my stations" in the player menu, for the playing station. */
    fun toggleSavedSong(songId: String) {
        val station = _favourites.value.firstOrNull { it.songId == songId } ?: known[songId] ?: return
        setFavourite(station, !isFavourite(station))
    }

    /** The heart in the player and Android Auto: [songId] is the playing station's song id. */
    fun toggleStarred(songId: String) {
        val id = songId.removePrefix(RADIO_ID_PREFIX)
        val station = _favourites.value.firstOrNull { it.id == id } ?: known[songId] ?: return
        setStarred(station, !isStarred(id))
    }

    /** The song title the playing station last announced (ICY metadata), keyed by its song id. */
    private val _nowPlaying = MutableStateFlow<Pair<String, String>?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    init {
        _favourites.value.forEach { known[it.songId] = it }
    }

    /** Country used for "Popular near you": the phone's region, or Australia. */
    val homeCountry: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "AU"

    /**
     * Searches every directory at once: Radio Browser by name, region (state) and genre tag, plus
     * TuneIn (which also matches cities). Name matches come first; duplicates are dropped.
     */
    suspend fun search(text: String): List<RadioStation> = coroutineScope {
        val byName = async { runCatching { query("/json/stations/search", "name" to text, "limit" to "60") }.getOrNull() }
        val byState = async { runCatching { query("/json/stations/search", "state" to text, "limit" to "40") }.getOrNull() }
        val byTag = async { runCatching { query("/json/stations/search", "tag" to text.lowercase(Locale.ROOT), "limit" to "40") }.getOrNull() }
        val tuneIn = async { runCatching { tuneInSearch(text) }.onFailure { Timber.tag(TAG).w(it, "TuneIn search failed") }.getOrNull() }
        val results = listOf(byName.await(), tuneIn.await(), byState.await(), byTag.await())
        if (results.all { it == null }) throw IOException("Radio directories unavailable")
        val seen = HashSet<String>()
        results.flatMap { it.orEmpty() }.filter { seen.add(normalize(it.name)) }
    }

    /** TuneIn's public station search (the same one many open-source radio apps use). */
    private suspend fun tuneInSearch(text: String): List<RadioStation> = withContext(Dispatchers.IO) {
        val url = "$TUNEIN/Search.ashx".toHttpUrl().newBuilder()
            .addQueryParameter("render", "json")
            .addQueryParameter("types", "station")
            .addQueryParameter("query", text)
            .build()
        val body = http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use {
            if (!it.isSuccessful) throw IOException("TuneIn error ${it.code}")
            it.body?.string().orEmpty()
        }
        val items = (json.parseToJsonElement(body) as? JsonObject)?.get("body") as? kotlinx.serialization.json.JsonArray
            ?: return@withContext emptyList()
        flatten(items)
            .filter { it.string("type") == "audio" && it.string("guide_id")?.startsWith("s") == true }
            .mapNotNull { item ->
                val guideId = item.string("guide_id") ?: return@mapNotNull null
                RadioStation(
                    id = "tunein-$guideId",
                    name = item.string("text")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null,
                    streamUrl = "$TUNEIN/Tune.ashx?id=$guideId",
                    favicon = item.string("image")?.takeIf { it.startsWith("http") }?.replaceFirst("http://", "https://"),
                    tags = item.string("subtext"),
                    codec = item.string("formats")?.uppercase(Locale.ROOT),
                    bitrate = item.string("bitrate")?.toIntOrNull() ?: 0,
                    source = "TuneIn",
                )
            }
            .onEach { known[it.songId] = it }
    }

    private fun flatten(items: kotlinx.serialization.json.JsonArray): List<JsonObject> =
        items.mapNotNull { it as? JsonObject }.flatMap { item ->
            val children = item["children"] as? kotlinx.serialization.json.JsonArray
            if (children != null) flatten(children) else listOf(item)
        }

    private fun normalize(name: String) = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    suspend fun popular(countryCode: String = homeCountry): List<RadioStation> =
        query("/json/stations/search", "countrycode" to countryCode, "limit" to "100")

    suspend fun byTag(tag: String, countryCode: String? = null): List<RadioStation> =
        query(
            "/json/stations/search",
            *listOfNotNull("tag" to tag, countryCode?.let { "countrycode" to it }, "limit" to "100").toTypedArray(),
        )

    fun isFavourite(station: RadioStation) = _favourites.value.any { it.id == station.id }

    fun setFavourite(station: RadioStation, favourite: Boolean) {
        // Removed from your stations: its heart goes too
        if (!favourite && station.id in starredIds.value) setStarred(station, false)
        val others = _favourites.value.filterNot { it.id == station.id }
        _favourites.value = if (favourite) others + station else others
        runCatching {
            favouritesFile.writeText(json.encodeToString(ListSerializer(RadioStation.serializer()), _favourites.value))
        }.onFailure { Timber.tag(TAG).w(it, "Could not save radio favourites") }
    }

    /** Saves stations as songs (artist = country, album = "Internet radio") so they can be queued. */
    suspend fun prepare(stations: List<RadioStation>): List<Song> = withContext(Dispatchers.IO) {
        val ids = stations.map { station ->
            known[station.songId] = station
            val existing = database.getSongById(station.songId)?.song
            with(database) {
                if (existing == null) {
                    upsert(
                        SongEntity(
                            id = station.songId,
                            title = station.name,
                            duration = -1,
                            thumbnailUrl = station.favicon,
                            albumName = ALBUM_NAME,
                            localPath = station.streamUrl,
                        ),
                    )
                    val artistName = station.country?.takeIf { it.isNotBlank() } ?: ALBUM_NAME
                    val artistId = artistByName(artistName)?.id ?: ArtistEntity(
                        id = ArtistEntity.generateArtistId(),
                        name = artistName,
                    ).also { insert(it) }.id
                    insert(SongArtistMap(songId = station.songId, artistId = artistId, position = 0))
                } else if (existing.localPath != station.streamUrl || existing.thumbnailUrl != station.favicon) {
                    upsert(existing.copy(localPath = station.streamUrl, thumbnailUrl = station.favicon, title = station.name))
                }
            }
            station.songId
        }
        val byId = database.getSongsByIds(ids).associateBy { it.id }
        ids.mapNotNull(byId::get)
    }

    /** Where to stream a station from, and whether it's HLS; falls back to the saved song. */
    fun stream(songId: String): Pair<String, Boolean>? {
        resolved[songId]?.let { return it }
        known[songId]?.let { return it.streamUrl to it.hls }
        val url = kotlinx.coroutines.runBlocking(Dispatchers.IO) { database.getSongById(songId)?.song?.localPath }
            ?: return null
        return url to url.contains(".m3u8", ignoreCase = true)
    }

    /** Radio Browser counts plays to rank stations; it asks apps to report them. */
    fun reportPlay(songId: String) {
        val id = songId.removePrefix(RADIO_ID_PREFIX)
        scope.launch {
            runCatching {
                http.newCall(Request.Builder().url("${SERVERS.first()}/json/url/$id").header("User-Agent", USER_AGENT).build())
                    .execute().close()
            }
        }
    }

    /**
     * Adds a station from a pasted stream link (e.g. found on StreamURL.link or FMSTREAM) and saves it
     * to favourites. Playlist links (.pls / .m3u) are opened to find the actual stream.
     */
    suspend fun addCustom(name: String, link: String, favourite: Boolean = true): RadioStation = withContext(Dispatchers.IO) {
        val url = runCatching { resolveStream(link.trim()) }.getOrNull() ?: link.trim()
        if (url.toHttpUrlOrNull() == null) throw IOException("That doesn't look like a stream link.")
        val station = RadioStation(
            id = "custom-" + java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16),
            name = name.trim().ifEmpty { url.toHttpUrlOrNull()?.host ?: "Radio stream" },
            streamUrl = url,
            hls = url.contains(".m3u8", ignoreCase = true),
            source = "Added by you",
        )
        known[station.songId] = station
        if (favourite) setFavourite(station, true)
        station
    }

    /**
     * Before playing a station picked from a list: works out its real stream (TuneIn and playlist
     * links point at a list of streams), so the player knows if it's HLS. Others resolve while loading.
     */
    suspend fun resolveForPlay(station: RadioStation) = withContext(Dispatchers.IO) {
        if (!needsResolving(station.streamUrl)) return@withContext
        runCatching { resolveStream(station.streamUrl) }.getOrNull()?.let { url ->
            resolved[station.songId] = url to (station.hls || url.contains(".m3u8", ignoreCase = true))
        }
    }

    fun needsResolving(url: String): Boolean {
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return url.contains("radiotime.com/Tune.ashx", ignoreCase = true) ||
            path.endsWith(".pls") || path.endsWith(".asx") || (path.endsWith(".m3u") && !path.endsWith(".m3u8"))
    }

    /** Turns a TuneIn or playlist link into a playable stream URL (blocking; call off the main thread). */
    fun resolveStream(link: String): String {
        var url = link
        if (url.contains("radiotime.com/Tune.ashx", ignoreCase = true)) {
            val tuneUrl = url.toHttpUrl().newBuilder().setQueryParameter("render", "json").build()
            val body = http.newCall(Request.Builder().url(tuneUrl).header("User-Agent", USER_AGENT).build()).execute().use {
                if (!it.isSuccessful) throw IOException("TuneIn error ${it.code}")
                it.body?.string().orEmpty()
            }
            val streams = ((json.parseToJsonElement(body) as? JsonObject)?.get("body") as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { it as? JsonObject }.orEmpty()
            val best = streams.firstOrNull { it.string("is_direct") == "true" } ?: streams.firstOrNull()
            url = best?.string("url") ?: throw IOException("TuneIn has no stream for this station")
        }
        return streamFromPlaylist(url) ?: url
    }

    /** For .pls, .m3u and .asx playlist links, the first stream inside; null for anything else. */
    private fun streamFromPlaylist(link: String): String? {
        val path = link.substringBefore('?').lowercase()
        if (!path.endsWith(".pls") && !path.endsWith(".asx") && !(path.endsWith(".m3u") && !path.endsWith(".m3u8"))) return null
        return runCatching {
            val body = http.newCall(Request.Builder().url(link).header("User-Agent", USER_AGENT).build()).execute().use {
                if (!it.isSuccessful) return null
                it.body?.string().orEmpty()
            }
            body.lineSequence()
                .map { line -> line.trim().let { Regex("https?://[^\\s\"'<>]+").find(it)?.value.orEmpty() } }
                .firstOrNull { it.isNotEmpty() }
        }.getOrNull()
    }

    fun setNowPlaying(songId: String, title: String?) {
        _nowPlaying.value = title?.takeIf { it.isNotBlank() }?.let { songId to it }
        nowPlayingTitle.value = _nowPlaying.value
    }

    private suspend fun query(path: String, vararg params: Pair<String, String>): List<RadioStation> =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for (server in SERVERS) {
                try {
                    val url = "$server$path".toHttpUrl().newBuilder().apply {
                        params.forEach { (k, v) -> addQueryParameter(k, v) }
                        addQueryParameter("hidebroken", "true")
                        addQueryParameter("order", "clickcount")
                        addQueryParameter("reverse", "true")
                    }.build()
                    val body = http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use {
                        if (!it.isSuccessful) throw IOException("Radio directory error ${it.code}")
                        it.body?.string().orEmpty()
                    }
                    return@withContext json.parseToJsonElement(body).jsonArray
                        .mapNotNull { (it as? JsonObject)?.toStation() }
                        .distinctBy { it.id }
                        .onEach { known[it.songId] = it }
                } catch (e: Exception) {
                    lastError = e
                    Timber.tag(TAG).w(e, "Radio server $server failed")
                }
            }
            throw lastError ?: IOException("Radio directory unavailable")
        }

    private fun JsonObject.toStation(): RadioStation? {
        val id = string("stationuuid") ?: return null
        val url = string("url_resolved")?.takeIf { it.isNotBlank() } ?: string("url") ?: return null
        return RadioStation(
            id = id,
            name = string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            streamUrl = url,
            favicon = string("favicon")?.takeIf { it.startsWith("http") },
            country = string("country"),
            countryCode = string("countrycode"),
            tags = string("tags"),
            codec = string("codec"),
            bitrate = (get("bitrate") as? JsonPrimitive)?.intOrNull ?: 0,
            hls = (get("hls") as? JsonPrimitive)?.intOrNull == 1,
        )
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    /**
     * Favourite stations as an M3U playlist, which most radio and media apps (VLC, Kodi, RadioDroid…)
     * can open. Each station also gets a "#EXTWAVELENGTH" line holding all its details, which other apps
     * ignore, so importing back into Wavelength keeps logos, country and tags.
     */
    fun exportM3u(): String = buildString {
        append("#EXTM3U\n")
        _favourites.value.forEach { station ->
            val name = station.name.replace(",", " ").replace("\n", " ")
            append("#EXTWAVELENGTH:").append(json.encodeToString(RadioStation.serializer(), station)).append('\n')
            append("#EXTINF:-1")
            station.favicon?.takeIf { it.isNotBlank() }?.let { append(" tvg-logo=\"").append(it.replace("\"", "")).append('"') }
            station.country?.takeIf { it.isNotBlank() }?.let { append(" group-title=\"").append(it.replace("\"", "")).append('"') }
            append(',').append(name).append('\n')
            append(station.streamUrl).append('\n')
        }
    }

    /** Result of a radio import: stations added to favourites, and ones skipped as already there. */
    data class StationImport(val added: Int, val skipped: Int)

    /**
     * Adds the stations in an M3U or PLS file to favourites. A station already in favourites (same id
     * or same stream address, ignoring http/https and a trailing slash) is skipped, as are repeats.
     */
    fun importPlaylist(text: String): StationImport {
        val found = mutableListOf<RadioStation>()
        val lines = text.lines().map { it.trim() }
        if (lines.any { it.equals("[playlist]", ignoreCase = true) }) {
            // PLS: File1=…, Title1=…
            val files = mutableMapOf<String, String>()
            val titles = mutableMapOf<String, String>()
            lines.forEach { line ->
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=', "").trim()
                when {
                    key.startsWith("File", ignoreCase = true) -> files[key.drop(4)] = value
                    key.startsWith("Title", ignoreCase = true) -> titles[key.drop(5)] = value
                }
            }
            files.toSortedMap(compareBy { it.toIntOrNull() ?: 0 }).forEach { (n, url) -> stationFor(titles[n], url, null, null)?.let(found::add) }
        } else {
            var saved: RadioStation? = null
            var name: String? = null
            var logo: String? = null
            var group: String? = null
            lines.forEach { line ->
                when {
                    line.startsWith("#EXTWAVELENGTH:") ->
                        saved = runCatching { json.decodeFromString(RadioStation.serializer(), line.removePrefix("#EXTWAVELENGTH:")) }.getOrNull()
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        name = line.substringAfterLast(',').trim().takeIf { it.isNotEmpty() }
                        logo = Regex("tvg-logo=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                        group = Regex("group-title=\"([^\"]*)\"").find(line)?.groupValues?.get(1)
                    }
                    line.isEmpty() || line.startsWith("#") -> Unit
                    else -> {
                        val station = saved?.takeIf { it.streamUrl == line } ?: stationFor(name, line, logo, group)
                        station?.let(found::add)
                        saved = null
                        name = null
                        logo = null
                        group = null
                    }
                }
            }
        }
        fun urlKey(url: String) = url.lowercase(Locale.ROOT).substringAfter("://").trimEnd('/')
        val ids = _favourites.value.map { it.id }.toMutableSet()
        val urls = _favourites.value.map { urlKey(it.streamUrl) }.toMutableSet()
        val added = mutableListOf<RadioStation>()
        var skipped = 0
        found.forEach { station ->
            if (station.id in ids || urlKey(station.streamUrl) in urls) {
                skipped++
            } else {
                ids += station.id
                urls += urlKey(station.streamUrl)
                added += station
                known[station.songId] = station
            }
        }
        if (added.isNotEmpty()) {
            _favourites.value = _favourites.value + added
            runCatching {
                favouritesFile.writeText(json.encodeToString(ListSerializer(RadioStation.serializer()), _favourites.value))
            }.onFailure { Timber.w(it, "Could not save radio favourites") }
        }
        return StationImport(added.size, skipped)
    }

    /** A station from another app's playlist entry, with the same id addCustom would give it. */
    private fun stationFor(name: String?, url: String, logo: String?, country: String?): RadioStation? {
        if (url.toHttpUrlOrNull() == null) return null
        return RadioStation(
            id = "custom-" + java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16),
            name = name?.takeIf { it.isNotBlank() } ?: url.toHttpUrlOrNull()?.host ?: "Radio stream",
            streamUrl = url,
            favicon = logo?.takeIf { it.isNotBlank() },
            country = country?.takeIf { it.isNotBlank() },
            hls = url.contains(".m3u8", ignoreCase = true),
            source = "Imported",
        )
    }

    private fun loadFavourites(): List<RadioStation> = runCatching {
        if (favouritesFile.exists()) {
            json.decodeFromString(ListSerializer(RadioStation.serializer()), favouritesFile.readText())
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())

    companion object {
        /** Ids of hearted stations; shared so the player screens can show the heart. */
        val starredIds = MutableStateFlow<Set<String>>(emptySet())

        /** The same "now playing" as [nowPlaying], for screens that can't inject this class. */
        val nowPlayingTitle = MutableStateFlow<Pair<String, String>?>(null)

        private const val TAG = "RadioDirectory"
        const val ALBUM_NAME = "Internet radio"
        private const val USER_AGENT = "Wavelength/1.0"
        private const val TUNEIN = "https://opml.radiotime.com"
        private val SERVERS = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
        )
    }
}
