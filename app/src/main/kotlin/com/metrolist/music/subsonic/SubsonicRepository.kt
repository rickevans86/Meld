/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.subsonic

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.metrolist.music.constants.SUBSONIC_ID_PREFIX
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SubsonicSong(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val coverArt: String?,
    val duration: Int,
    val track: Int?,
    val year: Int?,
    val starred: Boolean,
)

data class SubsonicAlbum(
    val id: String,
    val name: String,
    val artist: String?,
    val coverArt: String?,
    val songCount: Int,
    val year: Int?,
)

data class SubsonicArtist(val id: String, val name: String, val coverArt: String?, val albumCount: Int)

data class SubsonicPlaylist(val id: String, val name: String, val songCount: Int, val coverArt: String?)

/** One entry in a server folder: a sub-folder, or a song. */
data class SubsonicFolderEntry(val id: String, val name: String, val coverArt: String?, val song: SubsonicSong?) {
    val isFolder get() = song == null
}

data class SubsonicSearch(
    val artists: List<SubsonicArtist>,
    val albums: List<SubsonicAlbum>,
    val songs: List<SubsonicSong>,
)

class SubsonicException(message: String) : IOException(message)

/**
 * Music from a Navidrome server or any other Subsonic-compatible one (Airsonic, Gonic, ...):
 * sign-in, browsing, search, streaming, play reporting and stars, using the Subsonic REST API.
 */
@Singleton
class SubsonicRepository
@Inject
constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) {
    private val authStore = SubsonicAuthStore(context)

    /** Song id -> "Navidrome - …" folders it's saved into (its album and playlists, as browsed). */
    private val foldersFile = java.io.File(context.filesDir, "subsonic_folders.json")
    private val folderSerializer = kotlinx.serialization.builtins.MapSerializer(
        kotlinx.serialization.serializer<String>(),
        kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),
    )
    private val folders: java.util.concurrent.ConcurrentHashMap<String, Set<String>> = java.util.concurrent.ConcurrentHashMap(
        runCatching { Json.decodeFromString(folderSerializer, foldersFile.readText()).mapValues { it.value.toSet() } }
            .getOrDefault(emptyMap()),
    )
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val random = SecureRandom()

    private val _session = MutableStateFlow(authStore.load())
    val session = _session.asStateFlow()

    /** Checks the server and password, then remembers them. Throws with a readable message on failure. */
    suspend fun signIn(server: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val trimmed = server.trim().trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        if (withScheme.toHttpUrlOrNull() == null) throw SubsonicException("That server address doesn't look right.")
        val candidate = SubsonicSession(withScheme, username.trim(), password)
        call(candidate, "ping")
        authStore.save(candidate)
        _session.value = candidate
    }

    fun signOut() {
        authStore.clear()
        _session.value = null
    }

    suspend fun albums(type: String, offset: Int = 0): List<SubsonicAlbum> =
        call("getAlbumList2", "type" to type, "size" to "60", "offset" to offset.toString())
            .obj("albumList2").list("album").map { it.toAlbum() }

    suspend fun artists(): List<SubsonicArtist> =
        call("getArtists").obj("artists").list("index").flatMap { it.list("artist") }.map { it.toArtist() }

    suspend fun artistAlbums(id: String): List<SubsonicAlbum> =
        call("getArtist", "id" to id).obj("artist").list("album").map { it.toAlbum() }

    suspend fun albumSongs(id: String): List<SubsonicSong> {
        val album = call("getAlbum", "id" to id).obj("album")
        val songs = album.list("song").map { it.toSong() }
        album.string("name")?.let { rememberFolder(songs, "$FOLDER_PREFIX$it") }
        return songs
    }

    suspend fun playlists(): List<SubsonicPlaylist> =
        call("getPlaylists").obj("playlists").list("playlist").map {
            SubsonicPlaylist(
                id = it.string("id").orEmpty(),
                name = it.string("name").orEmpty(),
                songCount = it.int("songCount") ?: 0,
                coverArt = it.string("coverArt"),
            )
        }

    suspend fun playlistSongs(id: String): List<SubsonicSong> {
        val playlist = call("getPlaylist", "id" to id).obj("playlist")
        val songs = playlist.list("entry").map { it.toSong() }
        playlist.string("name")?.let { rememberFolder(songs, "$FOLDER_PREFIX$it") }
        return songs
    }

    /**
     * Folders a downloaded server song is saved into, like "YouTube - …" and "Spotify - …":
     * "Navidrome - <album>" and "Navidrome - <playlist>" for albums and playlists it was opened from.
     */
    fun exportFolders(songId: String): Set<String> = folders[songId].orEmpty()

    private fun rememberFolder(songs: List<SubsonicSong>, folder: String) {
        var changed = false
        songs.forEach { song ->
            val id = SUBSONIC_ID_PREFIX + song.id
            val current = folders[id].orEmpty()
            if (folder !in current) {
                folders[id] = current + folder
                changed = true
            }
        }
        if (changed) {
            runCatching {
                foldersFile.writeText(json.encodeToString(folderSerializer, folders.mapValues { it.value.toList() }))
            }.onFailure { Timber.tag(TAG).w(it, "Could not save Navidrome folders") }
        }
    }

    /**
     * The server's top-level folders. With one music library this is its folders straight away;
     * with several, each library is listed first (ids start with "lib~").
     */
    suspend fun rootFolders(): List<SubsonicFolderEntry> {
        val libraries = call("getMusicFolders").obj("musicFolders").list("musicFolder")
        if (libraries.size > 1) {
            return libraries.map { SubsonicFolderEntry(LIBRARY_PREFIX + it.string("id").orEmpty(), it.string("name") ?: "Music", null, null) }
        }
        return libraryFolders(libraries.firstOrNull()?.string("id"))
    }

    /** A folder's contents: sub-folders first, then songs. Songs here export to "Navidrome - <folder>". */
    suspend fun folder(id: String): Pair<String, List<SubsonicFolderEntry>> {
        if (id.startsWith(LIBRARY_PREFIX)) return "" to libraryFolders(id.removePrefix(LIBRARY_PREFIX))
        val directory = call("getMusicDirectory", "id" to id).obj("directory")
        val name = directory.string("name").orEmpty()
        val children = directory.list("child")
        val entries = children.map { child ->
            if (child["isDir"]?.let { (it as? JsonPrimitive)?.contentOrNull } == "true") {
                SubsonicFolderEntry(child.string("id").orEmpty(), child.string("title") ?: child.string("name") ?: "Folder", child.string("coverArt"), null)
            } else {
                val song = child.toSong()
                SubsonicFolderEntry(song.id, song.title, song.coverArt, song)
            }
        }
        if (name.isNotBlank()) rememberFolder(entries.mapNotNull { it.song }, "$FOLDER_PREFIX$name")
        return name to entries.sortedWith(compareBy<SubsonicFolderEntry> { !it.isFolder })
    }

    private suspend fun libraryFolders(libraryId: String?): List<SubsonicFolderEntry> {
        val indexes = call("getIndexes", *listOfNotNull(libraryId?.let { "musicFolderId" to it }).toTypedArray()).obj("indexes")
        val folders = indexes.list("index").flatMap { it.list("artist") }.map {
            SubsonicFolderEntry(it.string("id").orEmpty(), it.string("name") ?: "Folder", it.string("coverArt"), null)
        }
        val songs = indexes.list("child").map { child -> child.toSong().let { SubsonicFolderEntry(it.id, it.title, it.coverArt, it) } }
        return folders + songs
    }

    suspend fun starredSongs(): List<SubsonicSong> =
        call("getStarred2").obj("starred2").list("song").map { it.toSong() }

    suspend fun search(query: String): SubsonicSearch {
        val result = call(
            "search3",
            "query" to query,
            "artistCount" to "10",
            "albumCount" to "20",
            "songCount" to "50",
        ).obj("searchResult3")
        return SubsonicSearch(
            artists = result.list("artist").map { it.toArtist() },
            albums = result.list("album").map { it.toAlbum() },
            songs = result.list("song").map { it.toSong() },
        )
    }

    /** Cover art URL. Uses a fixed salt so the URL stays the same and images can be cached. */
    fun coverUrl(coverArt: String?, size: Int = 512): String? {
        val current = _session.value ?: return null
        if (coverArt.isNullOrBlank()) return null
        return endpoint(current, "getCoverArt", stableSalt(current), "id" to coverArt, "size" to size.toString()).toString()
    }

    /** Stream address for a song, built fresh each time. [original] skips server transcoding (for downloads). */
    fun streamUri(songId: String, original: Boolean = false): Uri? {
        val current = _session.value ?: return null
        val id = songId.removePrefix(SUBSONIC_ID_PREFIX)
        val params = if (original) arrayOf("id" to id, "format" to "raw") else arrayOf("id" to id)
        return endpoint(current, "stream", newSalt(), *params).toString().toUri()
    }

    /** Tells the server what's playing ([submission] false) or that a song was played (true). */
    fun scrobble(songId: String, submission: Boolean) {
        if (_session.value == null) return
        scope.launch {
            runCatching {
                call("scrobble", "id" to songId.removePrefix(SUBSONIC_ID_PREFIX), "submission" to submission.toString())
            }.onFailure { Timber.tag(TAG).w(it, "Scrobble failed") }
        }
    }

    /** Stars or unstars a song on the server in the background. */
    fun starLater(songId: String, starred: Boolean) {
        if (_session.value == null) return
        scope.launch {
            runCatching { call(if (starred) "star" else "unstar", "id" to songId.removePrefix(SUBSONIC_ID_PREFIX)) }
                .onFailure { Timber.tag(TAG).w(it, "Star failed") }
        }
    }

    /** Saves server songs so they can be queued, liked and downloaded; keeps the server's stars. */
    suspend fun prepare(songs: List<SubsonicSong>): List<Song> = withContext(Dispatchers.IO) {
        val ids = songs.distinctBy { it.id }.map { item ->
            val songId = SUBSONIC_ID_PREFIX + item.id
            val existing = database.getSongById(songId)?.song
            val cover = coverUrl(item.coverArt)
            with(database) {
                if (existing == null) {
                    upsert(
                        SongEntity(
                            id = songId,
                            title = item.title,
                            duration = item.duration,
                            thumbnailUrl = cover,
                            albumName = item.album,
                            year = item.year,
                            liked = item.starred,
                            likedDate = if (item.starred) LocalDateTime.now() else null,
                        ),
                    )
                    item.artist?.takeIf { it.isNotBlank() }?.let { name ->
                        val artistId = artistByName(name)?.id ?: ArtistEntity(
                            id = ArtistEntity.generateArtistId(),
                            name = name,
                        ).also { insert(it) }.id
                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                    }
                } else if (existing.thumbnailUrl != cover || existing.liked != item.starred) {
                    upsert(
                        existing.copy(
                            thumbnailUrl = cover,
                            liked = item.starred,
                            likedDate = if (item.starred) existing.likedDate ?: LocalDateTime.now() else null,
                        ),
                    )
                }
            }
            songId
        }
        val byId = database.getSongsByIds(ids).associateBy { it.id }
        ids.mapNotNull(byId::get)
    }

    private suspend fun call(endpoint: String, vararg params: Pair<String, String>): JsonObject {
        val current = _session.value ?: throw SubsonicException("Not signed in to a server.")
        return call(current, endpoint, *params)
    }

    private suspend fun call(current: SubsonicSession, endpoint: String, vararg params: Pair<String, String>): JsonObject =
        withContext(Dispatchers.IO) {
            val url = endpoint(current, endpoint, newSalt(), *params)
            val body = try {
                http.newCall(Request.Builder().url(url).build()).execute().use {
                    if (!it.isSuccessful) throw SubsonicException("The server replied with error ${it.code}.")
                    it.body?.string().orEmpty()
                }
            } catch (e: SubsonicException) {
                throw e
            } catch (e: IOException) {
                throw SubsonicException("Couldn't reach the server. Check the address and your connection.")
            }
            val response = runCatching { json.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject }
                .getOrNull() ?: throw SubsonicException("That doesn't look like a Navidrome or Subsonic server.")
            if (response.string("status") != "ok") {
                val error = response.obj("error")
                val message = when (error.int("code")) {
                    40 -> "Wrong username or password."
                    else -> error.string("message") ?: "The server reported an error."
                }
                throw SubsonicException(message)
            }
            response
        }

    private fun endpoint(current: SubsonicSession, name: String, salt: String, vararg params: Pair<String, String>): HttpUrl =
        "${current.serverUrl}/rest/$name.view".toHttpUrlOrNull()!!.newBuilder().apply {
            addQueryParameter("u", current.username)
            addQueryParameter("t", md5(current.password + salt))
            addQueryParameter("s", salt)
            addQueryParameter("v", API_VERSION)
            addQueryParameter("c", CLIENT_NAME)
            addQueryParameter("f", "json")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()

    private fun newSalt(): String = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    private fun stableSalt(current: SubsonicSession) = md5(current.serverUrl + current.username).take(12)

    private fun md5(value: String) =
        MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? = (get(key) as? JsonPrimitive)?.intOrNull

    private fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())

    /** Subsonic servers send one item as an object and several as an array; accept both. */
    private fun JsonObject.list(key: String): List<JsonObject> = when (val value = get(key)) {
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        is JsonObject -> listOf(value)
        else -> emptyList()
    }

    private fun JsonObject.toSong() = SubsonicSong(
        id = string("id").orEmpty(),
        title = string("title") ?: "Unknown",
        artist = string("artist"),
        album = string("album"),
        albumId = string("albumId"),
        coverArt = string("coverArt"),
        duration = int("duration") ?: -1,
        track = int("track"),
        year = int("year"),
        starred = get("starred") != null,
    )

    private fun JsonObject.toAlbum() = SubsonicAlbum(
        id = string("id").orEmpty(),
        name = string("name") ?: string("title") ?: "Unknown album",
        artist = string("artist"),
        coverArt = string("coverArt"),
        songCount = int("songCount") ?: 0,
        year = int("year"),
    )

    private fun JsonObject.toArtist() = SubsonicArtist(
        id = string("id").orEmpty(),
        name = string("name") ?: "Unknown artist",
        coverArt = string("coverArt"),
        albumCount = int("albumCount") ?: 0,
    )

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun subsonicRepository(): SubsonicRepository
    }

    companion object {
        private const val TAG = "Subsonic"
        private const val API_VERSION = "1.16.1"
        private const val CLIENT_NAME = "Wavelength"
        const val FOLDER_PREFIX = "Navidrome - "
        const val LIBRARY_PREFIX = "lib~"

        /** For places Hilt can't inject into, like the song menu. */
        fun from(context: Context): SubsonicRepository =
            EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).subsonicRepository()
    }
}
