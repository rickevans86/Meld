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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
) {
    val songId get() = RADIO_ID_PREFIX + id

    /** "Australia · news, talk · AAC 64k" */
    val details: String
        get() = listOfNotNull(
            country?.takeIf { it.isNotBlank() },
            tags?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(3)?.joinToString(", ")?.takeIf { it.isNotEmpty() },
            listOfNotNull(codec?.takeIf { it.isNotBlank() && it != "UNKNOWN" }, bitrate.takeIf { it > 0 }?.let { "${it}k" })
                .joinToString(" ").takeIf { it.isNotEmpty() },
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
    private val known = ConcurrentHashMap<String, RadioStation>()

    private val _favourites = MutableStateFlow(loadFavourites())
    val favourites = _favourites.asStateFlow()

    /** The song title the playing station last announced (ICY metadata), keyed by its song id. */
    private val _nowPlaying = MutableStateFlow<Pair<String, String>?>(null)
    val nowPlaying = _nowPlaying.asStateFlow()

    init {
        _favourites.value.forEach { known[it.songId] = it }
    }

    /** Country used for "Popular near you": the phone's region, or Australia. */
    val homeCountry: String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "AU"

    suspend fun search(name: String): List<RadioStation> =
        query("/json/stations/search", "name" to name, "limit" to "80")

    suspend fun popular(countryCode: String = homeCountry): List<RadioStation> =
        query("/json/stations/search", "countrycode" to countryCode, "limit" to "100")

    suspend fun byTag(tag: String, countryCode: String? = null): List<RadioStation> =
        query(
            "/json/stations/search",
            *listOfNotNull("tag" to tag, countryCode?.let { "countrycode" to it }, "limit" to "100").toTypedArray(),
        )

    fun isFavourite(station: RadioStation) = _favourites.value.any { it.id == station.id }

    fun setFavourite(station: RadioStation, favourite: Boolean) {
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
    suspend fun addCustom(name: String, link: String): RadioStation = withContext(Dispatchers.IO) {
        val url = streamFromPlaylist(link.trim()) ?: link.trim()
        if (url.toHttpUrlOrNull() == null) throw IOException("That doesn't look like a stream link.")
        val station = RadioStation(
            id = "custom-" + java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(16),
            name = name.trim().ifEmpty { url.toHttpUrlOrNull()?.host ?: "Radio stream" },
            streamUrl = url,
            hls = url.contains(".m3u8", ignoreCase = true),
        )
        known[station.songId] = station
        setFavourite(station, true)
        station
    }

    /** For .pls and .m3u playlist links, the first stream inside; null for anything else. */
    private fun streamFromPlaylist(link: String): String? {
        val path = link.substringBefore('?').lowercase()
        if (!path.endsWith(".pls") && !(path.endsWith(".m3u") && !path.endsWith(".m3u8"))) return null
        return runCatching {
            val body = http.newCall(Request.Builder().url(link).header("User-Agent", USER_AGENT).build()).execute().use {
                if (!it.isSuccessful) return null
                it.body?.string().orEmpty()
            }
            body.lineSequence()
                .map { it.trim().substringAfter('=', it.trim()).trim() }
                .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }
        }.getOrNull()
    }

    fun setNowPlaying(songId: String, title: String?) {
        _nowPlaying.value = title?.takeIf { it.isNotBlank() }?.let { songId to it }
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

    private fun loadFavourites(): List<RadioStation> = runCatching {
        if (favouritesFile.exists()) {
            json.decodeFromString(ListSerializer(RadioStation.serializer()), favouritesFile.readText())
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val TAG = "RadioDirectory"
        const val ALBUM_NAME = "Internet radio"
        private const val USER_AGENT = "Wavelength/1.0"
        private val SERVERS = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://at1.api.radio-browser.info",
            "https://de2.api.radio-browser.info",
        )
    }
}
