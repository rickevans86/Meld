/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.mediaserver

import android.content.Context
import android.net.Uri
import com.metrolist.music.constants.MEDIA_SERVER_ID_PREFIX
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class MediaServerKind { JELLYFIN, PLEX }

data class MediaServerSession(
    val kind: MediaServerKind,
    val serverUrl: String,
    val serverName: String,
    val username: String,
    val userId: String,
    val token: String,
)

data class MsSong(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val duration: Int,
    val track: Int?,
    val year: Int?,
    val coverUrl: String?,
    val favourite: Boolean,
    /** Plex only: the file part to stream. */
    val partKey: String? = null,
)

data class MsAlbum(val id: String, val title: String, val artist: String?, val year: Int?, val coverUrl: String?)

data class MsArtist(val id: String, val name: String, val coverUrl: String?)

data class MsPlaylist(val id: String, val name: String, val songCount: Int, val coverUrl: String?)

/** A Plex server the account can use. */
data class PlexServer(val name: String, val token: String, val connections: List<String>)

/** Plex sign-in code: the user enters [code] at plex.tv/link. */
data class PlexPin(val id: Long, val code: String)

class MediaServerException(message: String) : IOException(message)

/**
 * Jellyfin (and Emby) or Plex music, one server at a time. Songs are saved with ids
 * "mediaserver:<item id>" and stream straight from the server; downloads go into
 * "Jellyfin - …" / "Plex - …" folders.
 */
@Singleton
class MediaServerRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val authStore = MediaServerAuthStore(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private val _session = MutableStateFlow(authStore.load())
    val session = _session.asStateFlow()

    /** Plex file parts by song id, so Plex songs can stream without another lookup. */
    private val partsFile = File(context.filesDir, "media_server_parts.json")
    private val partSerializer = MapSerializer(String.serializer(), String.serializer())
    private val parts = ConcurrentHashMap<String, String>(
        runCatching { json.decodeFromString(partSerializer, partsFile.readText()) }.getOrDefault(emptyMap()),
    )

    /** Download folders by song id: the album or playlist each song was opened from. */
    private val foldersFile = File(context.filesDir, "media_server_folders.json")
    private val folderSerializer = MapSerializer(String.serializer(), kotlinx.serialization.builtins.ListSerializer(String.serializer()))
    private val folders = ConcurrentHashMap<String, List<String>>(
        runCatching { json.decodeFromString(folderSerializer, foldersFile.readText()) }.getOrDefault(emptyMap()),
    )

    val folderPrefix: String
        get() = when (_session.value?.kind) {
            MediaServerKind.PLEX -> "Plex - "
            else -> "Jellyfin - "
        }

    // ── Sign in ──────────────────────────────────────────────────────────────────

    /** Jellyfin or Emby: server address, username and password; keeps only the access token. */
    suspend fun signInJellyfin(server: String, username: String, password: String) = withContext(Dispatchers.IO) {
        val base = normalise(server)
        val body = buildJsonObject {
            put("Username", username.trim())
            put("Pw", password)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$base/Users/AuthenticateByName")
            .header("Authorization", jellyfinAuthHeader(null))
            .header("X-Emby-Authorization", jellyfinAuthHeader(null))
            .post(body)
            .build()
        val result = http.newCall(request).execute().use { response ->
            if (response.code == 401) throw MediaServerException("Wrong username or password.")
            if (!response.isSuccessful) throw MediaServerException("The server said ${response.code}. Check the address.")
            json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject
        } ?: throw MediaServerException("That doesn't look like a Jellyfin server.")
        val token = result.string("AccessToken") ?: throw MediaServerException("The server didn't sign you in.")
        val user = result.obj("User")
        val serverName = runCatching { get(base, "/System/Info/Public", null).obj().string("ServerName") }.getOrNull()
        val session = MediaServerSession(
            kind = MediaServerKind.JELLYFIN,
            serverUrl = base,
            serverName = serverName ?: "Jellyfin",
            username = user.string("Name") ?: username,
            userId = user.string("Id") ?: throw MediaServerException("The server didn't return your user."),
            token = token,
        )
        authStore.save(session)
        _session.value = session
    }

    /** Plex step 1: a short code to enter at plex.tv/link (the password never goes through the app). */
    suspend fun plexStartPin(): PlexPin = withContext(Dispatchers.IO) {
        val request = plexRequest("https://plex.tv/api/v2/pins?strong=false", null)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw MediaServerException("Couldn't reach Plex (${response.code}).")
            json.parseToJsonElement(response.body?.string().orEmpty()) as JsonObject
        }
        PlexPin(
            id = (body["id"] as? JsonPrimitive)?.longOrNull ?: throw MediaServerException("Plex didn't give a code."),
            code = body.string("code") ?: throw MediaServerException("Plex didn't give a code."),
        )
    }

    /** Plex step 2: the account token once the code has been entered, or null while still waiting. */
    suspend fun plexCheckPin(pin: PlexPin): String? = withContext(Dispatchers.IO) {
        val request = plexRequest("https://plex.tv/api/v2/pins/${pin.id}", null).get().build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) throw MediaServerException("The code expired. Start again.")
            if (!response.isSuccessful) return@withContext null
            (json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject)?.string("authToken")?.takeIf { it.isNotBlank() }
        }
    }

    /** Plex step 3: servers this account can use. */
    suspend fun plexServers(accountToken: String): List<PlexServer> = withContext(Dispatchers.IO) {
        val request = plexRequest("https://plex.tv/api/v2/resources?includeHttps=1&includeRelay=1", accountToken).get().build()
        val list = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw MediaServerException("Couldn't list your Plex servers (${response.code}).")
            json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonArray
        } ?: JsonArray(emptyList())
        list.mapNotNull { element ->
            val device = element as? JsonObject ?: return@mapNotNull null
            if (device.string("provides")?.contains("server") != true) return@mapNotNull null
            val connections = (device["connections"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                // Direct connections first (local network, then remote), relay last
                .sortedWith(compareBy({ it.bool("relay") }, { !it.bool("local") }))
                .mapNotNull { it.string("uri") }
            PlexServer(
                name = device.string("name") ?: "Plex",
                token = device.string("accessToken") ?: accountToken,
                connections = connections,
            )
        }
    }

    /** Plex step 4: connects to a server, trying its addresses in turn. */
    suspend fun plexUseServer(server: PlexServer) = withContext(Dispatchers.IO) {
        val working = server.connections.firstOrNull { uri ->
            withTimeoutOrNull(6_000) {
                runCatching {
                    http.newCall(plexRequest("${uri.trimEnd('/')}/identity", server.token).get().build()).execute().use { it.isSuccessful }
                }.getOrDefault(false)
            } == true
        } ?: throw MediaServerException("Couldn't reach ${server.name} from here.")
        val session = MediaServerSession(
            kind = MediaServerKind.PLEX,
            serverUrl = working.trimEnd('/'),
            serverName = server.name,
            username = "",
            userId = "",
            token = server.token,
        )
        authStore.save(session)
        _session.value = session
    }

    fun signOut() {
        authStore.clear()
        _session.value = null
        plexSections = null
    }

    // ── Browsing ─────────────────────────────────────────────────────────────────

    suspend fun albums(): List<MsAlbum> = when (current().kind) {
        MediaServerKind.JELLYFIN -> jellyfinItems("IncludeItemTypes" to "MusicAlbum", "SortBy" to "SortName", "Limit" to "2000").map { it.toAlbum() }
        MediaServerKind.PLEX -> plexAcrossSections("type=9&sort=titleSort").map { it.toPlexAlbum() }
    }

    suspend fun artists(): List<MsArtist> = when (current().kind) {
        MediaServerKind.JELLYFIN -> {
            val s = current()
            get(s.serverUrl, "/Artists/AlbumArtists?UserId=${s.userId}&SortBy=SortName&Limit=2000", s.token).obj()
                .array("Items").map { it.obj().toArtist() }
        }
        MediaServerKind.PLEX -> plexAcrossSections("type=8&sort=titleSort").map { it.toPlexArtist() }
    }

    suspend fun artistAlbums(artistId: String): List<MsAlbum> = when (current().kind) {
        MediaServerKind.JELLYFIN -> jellyfinItems(
            "IncludeItemTypes" to "MusicAlbum",
            "AlbumArtistIds" to artistId,
            "SortBy" to "ProductionYear,SortName",
            "SortOrder" to "Descending",
        ).map { it.toAlbum() }
        MediaServerKind.PLEX -> plexMetadata("/library/metadata/$artistId/children").map { it.toPlexAlbum() }
    }

    suspend fun albumSongs(album: MsAlbum): List<MsSong> = albumSongs(album.id, album.title)

    /** An album's songs by id; [folderName] is its download folder (defaults to the songs' album name). */
    suspend fun albumSongs(albumId: String, folderName: String?): List<MsSong> {
        val songs = when (current().kind) {
            MediaServerKind.JELLYFIN -> jellyfinItems(
                "ParentId" to albumId,
                "IncludeItemTypes" to "Audio",
                "SortBy" to "ParentIndexNumber,IndexNumber,SortName",
            ).map { it.toSong() }
            MediaServerKind.PLEX -> plexMetadata("/library/metadata/$albumId/children").map { it.toPlexSong() }
        }
        rememberFolder(songs, folderName ?: songs.firstOrNull()?.album.orEmpty())
        return songs
    }

    suspend fun playlists(): List<MsPlaylist> = when (current().kind) {
        MediaServerKind.JELLYFIN -> jellyfinItems("IncludeItemTypes" to "Playlist", "SortBy" to "SortName")
            .filter { it.string("MediaType")?.equals("Audio", ignoreCase = true) != false }
            .map { item ->
                MsPlaylist(
                    id = item.string("Id").orEmpty(),
                    name = item.string("Name").orEmpty(),
                    songCount = (item["ChildCount"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0,
                    coverUrl = jellyfinImage(item.string("Id"), item.obj("ImageTags").string("Primary")),
                )
            }
        MediaServerKind.PLEX -> plexMetadata("/playlists?playlistType=audio").map { item ->
            MsPlaylist(
                id = item.string("ratingKey").orEmpty(),
                name = item.string("title").orEmpty(),
                songCount = item.string("leafCount")?.toIntOrNull() ?: 0,
                coverUrl = plexImage(item.string("composite") ?: item.string("thumb")),
            )
        }
    }

    suspend fun playlistSongs(playlist: MsPlaylist): List<MsSong> = playlistSongs(playlist.id, playlist.name)

    suspend fun playlistSongs(playlistId: String, folderName: String?): List<MsSong> {
        val s = current()
        val songs = when (s.kind) {
            MediaServerKind.JELLYFIN -> get(s.serverUrl, "/Playlists/$playlistId/Items?UserId=${s.userId}&Fields=PrimaryImageAspectRatio", s.token)
                .obj().array("Items").map { it.obj().toSong() }
            MediaServerKind.PLEX -> plexMetadata("/playlists/$playlistId/items").map { it.toPlexSong() }
        }
        folderName?.let { rememberFolder(songs, it) }
        return songs
    }

    /** Jellyfin favourites (Plex has no simple favourites list). */
    suspend fun favourites(): List<MsSong> = when (current().kind) {
        MediaServerKind.JELLYFIN -> jellyfinItems("IncludeItemTypes" to "Audio", "Filters" to "IsFavorite", "SortBy" to "SortName")
            .map { it.toSong() }
            .also { rememberFolder(it, "Favourites") }
        MediaServerKind.PLEX -> emptyList()
    }

    val hasFavourites: Boolean get() = _session.value?.kind == MediaServerKind.JELLYFIN

    data class SearchResults(val songs: List<MsSong>, val albums: List<MsAlbum>, val artists: List<MsArtist>)

    suspend fun search(query: String): SearchResults {
        val s = current()
        return when (s.kind) {
            MediaServerKind.JELLYFIN -> {
                val items = jellyfinItems(
                    "searchTerm" to query,
                    "IncludeItemTypes" to "Audio,MusicAlbum,MusicArtist",
                    "Limit" to "80",
                )
                SearchResults(
                    songs = items.filter { it.string("Type") == "Audio" }.map { it.toSong() },
                    albums = items.filter { it.string("Type") == "MusicAlbum" }.map { it.toAlbum() },
                    artists = items.filter { it.string("Type") == "MusicArtist" }.map { it.toArtist() },
                )
            }
            MediaServerKind.PLEX -> {
                val hubs = get(s.serverUrl, "/hubs/search?query=${Uri.encode(query)}&limit=30", s.token).obj()
                    .obj("MediaContainer").array("Hub").map { it.obj() }
                fun hub(type: String) = hubs.firstOrNull { it.string("type") == type }?.array("Metadata").orEmpty().map { it.obj() }
                SearchResults(
                    songs = hub("track").map { it.toPlexSong() },
                    albums = hub("album").map { it.toPlexAlbum() },
                    artists = hub("artist").map { it.toPlexArtist() },
                )
            }
        }
    }

    // ── Playback ─────────────────────────────────────────────────────────────────

    /** Where a saved song streams from (the original file), or null when signed out. */
    fun streamUri(songId: String): Uri? {
        val s = _session.value ?: return null
        val id = songId.removePrefix(MEDIA_SERVER_ID_PREFIX)
        return when (s.kind) {
            MediaServerKind.JELLYFIN ->
                Uri.parse("${s.serverUrl}/Audio/$id/stream?static=true&api_key=${Uri.encode(s.token)}&DeviceId=${Uri.encode(authStore.deviceId)}")
            MediaServerKind.PLEX -> {
                val part = parts[id] ?: runCatching {
                    // Not seen yet: look the track up (this runs on the player's loading thread)
                    kotlinx.coroutines.runBlocking { plexMetadata("/library/metadata/$id").firstOrNull()?.toPlexSong()?.partKey }
                }.getOrNull()?.also { parts[id] = it; saveParts() } ?: return null
                Uri.parse("${s.serverUrl}$part?X-Plex-Token=${Uri.encode(s.token)}")
            }
        }
    }

    /** Saves server songs in Wavelength (ids "mediaserver:<id>") so they can be played, liked and downloaded. */
    suspend fun prepare(songs: List<MsSong>): List<Song> = withContext(Dispatchers.IO) {
        var partsChanged = false
        val ids = songs.distinctBy { it.id }.map { item ->
            item.partKey?.let { if (parts[item.id] != it) { parts[item.id] = it; partsChanged = true } }
            val songId = MEDIA_SERVER_ID_PREFIX + item.id
            val existing = database.getSongById(songId)?.song
            with(database) {
                if (existing == null) {
                    upsert(
                        SongEntity(
                            id = songId,
                            title = item.title,
                            duration = item.duration,
                            thumbnailUrl = item.coverUrl,
                            albumName = item.album,
                            year = item.year,
                            liked = item.favourite,
                            likedDate = if (item.favourite) LocalDateTime.now() else null,
                        ),
                    )
                    item.artist?.takeIf { it.isNotBlank() }?.let { name ->
                        val artistId = artistByName(name)?.id ?: ArtistEntity(
                            id = ArtistEntity.generateArtistId(),
                            name = name,
                        ).also { insert(it) }.id
                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                    }
                } else if (existing.thumbnailUrl != item.coverUrl) {
                    upsert(existing.copy(thumbnailUrl = item.coverUrl))
                }
            }
            songId
        }
        if (partsChanged) saveParts()
        val byId = database.getSongsByIds(ids).associateBy { it.id }
        ids.mapNotNull(byId::get)
    }

    /** Download folders for a saved song: "Jellyfin - <album or playlist>" / "Plex - …". */
    fun exportFolders(songId: String): List<String> =
        folders[songId.removePrefix(MEDIA_SERVER_ID_PREFIX)].orEmpty().map { folderPrefix + it }

    private fun rememberFolder(songs: List<MsSong>, name: String) {
        if (name.isBlank()) return
        songs.forEach { song -> folders[song.id] = (folders[song.id].orEmpty() + name).distinct() }
        runCatching { foldersFile.writeText(json.encodeToString(folderSerializer, HashMap(folders))) }
    }

    private fun saveParts() {
        runCatching { partsFile.writeText(json.encodeToString(partSerializer, HashMap(parts))) }
    }

    // ── Jellyfin ─────────────────────────────────────────────────────────────────

    private fun jellyfinAuthHeader(token: String?): String =
        "MediaBrowser Client=\"Wavelength\", Device=\"Android\", DeviceId=\"${authStore.deviceId}\", Version=\"1.0\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")

    private suspend fun jellyfinItems(vararg params: Pair<String, String>): List<JsonObject> {
        val s = current()
        val query = (params.toList() + listOf("Recursive" to "true", "Fields" to "PrimaryImageAspectRatio,ChildCount"))
            .joinToString("&") { (k, v) -> "$k=${Uri.encode(v)}" }
        return get(s.serverUrl, "/Users/${s.userId}/Items?$query", s.token).obj().array("Items").map { it.obj() }
    }

    private fun jellyfinImage(id: String?, tag: String?): String? {
        val s = _session.value ?: return null
        if (id == null || tag == null) return null
        return "${s.serverUrl}/Items/$id/Images/Primary?maxHeight=500&quality=90&tag=$tag"
    }

    private fun JsonObject.toSong(): MsSong {
        val id = string("Id").orEmpty()
        val ownTag = obj("ImageTags").string("Primary")
        val cover = if (ownTag != null) jellyfinImage(id, ownTag) else jellyfinImage(string("AlbumId"), string("AlbumPrimaryImageTag"))
        return MsSong(
            id = id,
            title = string("Name").orEmpty(),
            artist = string("AlbumArtist") ?: array("Artists").firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull },
            album = string("Album"),
            albumId = string("AlbumId"),
            duration = ((get("RunTimeTicks") as? JsonPrimitive)?.longOrNull ?: 0L).div(10_000_000L).toInt(),
            track = string("IndexNumber")?.toIntOrNull(),
            year = string("ProductionYear")?.toIntOrNull(),
            coverUrl = cover,
            favourite = (obj("UserData")["IsFavorite"] as? JsonPrimitive)?.booleanOrNull == true,
        )
    }

    private fun JsonObject.toAlbum() = MsAlbum(
        id = string("Id").orEmpty(),
        title = string("Name").orEmpty(),
        artist = string("AlbumArtist"),
        year = string("ProductionYear")?.toIntOrNull(),
        coverUrl = jellyfinImage(string("Id"), obj("ImageTags").string("Primary")),
    )

    private fun JsonObject.toArtist() = MsArtist(
        id = string("Id").orEmpty(),
        name = string("Name").orEmpty(),
        coverUrl = jellyfinImage(string("Id"), obj("ImageTags").string("Primary")),
    )

    // ── Plex ─────────────────────────────────────────────────────────────────────

    @Volatile private var plexSections: List<String>? = null

    private fun plexRequest(url: String, token: String?): Request.Builder =
        Request.Builder().url(url)
            .header("Accept", "application/json")
            .header("X-Plex-Product", "Wavelength")
            .header("X-Plex-Client-Identifier", authStore.deviceId)
            .header("X-Plex-Device", "Android")
            .header("X-Plex-Platform", "Android")
            .apply { token?.let { header("X-Plex-Token", it) } }

    /** Music libraries on the Plex server. */
    private suspend fun plexMusicSections(): List<String> = plexSections ?: run {
        val s = current()
        get(s.serverUrl, "/library/sections", s.token).obj().obj("MediaContainer").array("Directory")
            .map { it.obj() }
            .filter { it.string("type") == "artist" }
            .mapNotNull { it.string("key") }
            .also { plexSections = it }
    }

    private suspend fun plexAcrossSections(query: String): List<JsonObject> = coroutineScope {
        plexMusicSections().map { key -> async { plexMetadata("/library/sections/$key/all?$query") } }.awaitAll().flatten()
    }

    private suspend fun plexMetadata(path: String): List<JsonObject> {
        val s = current()
        return get(s.serverUrl, path, s.token).obj().obj("MediaContainer").array("Metadata").map { it.obj() }
    }

    private fun plexImage(path: String?): String? {
        val s = _session.value ?: return null
        if (path.isNullOrBlank()) return null
        return "${s.serverUrl}/photo/:/transcode?width=500&height=500&minSize=1&url=${Uri.encode(path)}&X-Plex-Token=${Uri.encode(s.token)}"
    }

    private fun JsonObject.toPlexSong(): MsSong {
        val part = array("Media").firstOrNull()?.obj()?.array("Part")?.firstOrNull()?.obj()?.string("key")
        return MsSong(
            id = string("ratingKey").orEmpty(),
            title = string("title").orEmpty(),
            artist = string("originalTitle") ?: string("grandparentTitle"),
            album = string("parentTitle"),
            albumId = string("parentRatingKey"),
            duration = ((get("duration") as? JsonPrimitive)?.longOrNull ?: 0L).div(1000L).toInt(),
            track = string("index")?.toIntOrNull(),
            year = string("parentYear")?.toIntOrNull() ?: string("year")?.toIntOrNull(),
            coverUrl = plexImage(string("parentThumb") ?: string("thumb")),
            favourite = (string("userRating")?.toDoubleOrNull() ?: 0.0) >= 10.0,
            partKey = part,
        )
    }

    private fun JsonObject.toPlexAlbum() = MsAlbum(
        id = string("ratingKey").orEmpty(),
        title = string("title").orEmpty(),
        artist = string("parentTitle"),
        year = string("year")?.toIntOrNull(),
        coverUrl = plexImage(string("thumb")),
    )

    private fun JsonObject.toPlexArtist() = MsArtist(
        id = string("ratingKey").orEmpty(),
        name = string("title").orEmpty(),
        coverUrl = plexImage(string("thumb")),
    )

    // ── HTTP ─────────────────────────────────────────────────────────────────────

    private fun current(): MediaServerSession = _session.value ?: throw MediaServerException("Not signed in to a server.")

    private suspend fun get(base: String, path: String, token: String?): JsonElement = withContext(Dispatchers.IO) {
        val s = _session.value
        val builder = if (s?.kind == MediaServerKind.PLEX && base == s.serverUrl) {
            plexRequest("$base$path", token)
        } else {
            Request.Builder().url("$base$path")
                .header("Accept", "application/json")
                .apply {
                    token?.let {
                        header("X-Emby-Token", it)
                        header("Authorization", jellyfinAuthHeader(it))
                    }
                }
        }
        http.newCall(builder.get().build()).execute().use { response ->
            if (response.code == 401) throw MediaServerException("The server signed you out. Sign in again.")
            if (!response.isSuccessful) throw MediaServerException("The server said ${response.code}.")
            json.parseToJsonElement(response.body?.string().orEmpty().ifBlank { "{}" })
        }
    }

    private fun normalise(server: String): String {
        val trimmed = server.trim().trimEnd('/')
        if (trimmed.isEmpty()) throw MediaServerException("Enter your server address.")
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }

    private fun JsonElement.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun mediaServerRepository(): MediaServerRepository
    }

    companion object {
        private const val TAG = "MediaServer"

        fun from(context: Context): MediaServerRepository =
            EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).mediaServerRepository()
    }
}
