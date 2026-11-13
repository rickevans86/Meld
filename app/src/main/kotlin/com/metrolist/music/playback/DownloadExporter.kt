/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.metrolist.music.constants.DownloadExportFolderKey
import com.metrolist.music.constants.DownloadPlaylistFoldersKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Song
import com.metrolist.music.utils.SpotifyTokenManager
import com.metrolist.spotify.Spotify
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * Copies completed downloads out of the private download cache into a user-chosen
 * folder (Storage Access Framework tree) as ordinary audio files other apps can play.
 */
class DownloadExporter(
    private val context: Context,
    private val database: MusicDatabase,
    private val downloadCache: Cache,
) {
    private val lock = Mutex()

    fun folderUri(): Uri? =
        context.dataStore[DownloadExportFolderKey]?.takeIf { it.isNotBlank() }?.toUri()

    /** Whether saved files go into one folder per playlist (a copy in each playlist the song is in). */
    fun sortsByPlaylist(): Boolean = context.dataStore[DownloadPlaylistFoldersKey] ?: true

    suspend fun export(songId: String): Boolean = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext false
        lock.withLock {
            if (sortsByPlaylist()) refreshSpotifyFolders(force = false)
            exportTo(songId, FolderCache(treeUri))
        }
    }

    /** Returns how many songs are now present in the folder. */
    suspend fun exportAll(songIds: Collection<String>): Int = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext 0
        lock.withLock {
            if (sortsByPlaylist()) refreshSpotifyFolders(force = true)
            val cache = FolderCache(treeUri)
            songIds.count { exportTo(it, cache) }
        }
    }

    /**
     * Folder names for a song: its playlists ("YouTube - " for ones synced from YouTube Music,
     * plain for ones made in Meld, "Spotify - " for Spotify's), plus a liked-songs folder.
     * An empty list entry means the main folder.
     */
    private fun folderNamesFor(song: Song): List<String> {
        if (!sortsByPlaylist()) return listOf("")
        val names = LinkedHashSet<String>()
        database.playlistFoldersForSong(song.id).forEach { playlist ->
            names += if (playlist.browseId != null) "YouTube - ${playlist.name}" else playlist.name
        }
        if (com.metrolist.music.constants.isSubsonicId(song.id)) {
            // Navidrome songs: their album / playlist folders, starred songs, else the album
            names += com.metrolist.music.subsonic.SubsonicRepository.from(context).exportFolders(song.id)
            if (song.song.liked) names += "${com.metrolist.music.subsonic.SubsonicRepository.FOLDER_PREFIX}Starred"
            if (names.isEmpty()) {
                names += com.metrolist.music.subsonic.SubsonicRepository.FOLDER_PREFIX + (song.song.albumName ?: "Songs")
            }
        } else if (com.metrolist.music.constants.isMediaServerId(song.id)) {
            // Jellyfin / Plex songs: "Jellyfin - <album or playlist>" / "Plex - …"
            val server = com.metrolist.music.mediaserver.MediaServerRepository.from(context)
            names += server.exportFolders(song.id)
            if (names.isEmpty()) names += server.folderPrefix + (song.song.albumName ?: "Songs")
        } else if (song.song.liked) {
            names += if (youTubeSignedIn()) "YouTube - Liked songs" else "Liked songs"
        }
        names += spotifyFolders[song.id].orEmpty()
        return names.map { sanitizeFileName(it, "Playlist") }.distinct().ifEmpty { listOf("") }
    }

    private fun youTubeSignedIn(): Boolean =
        context.dataStore[InnerTubeCookieKey]?.contains("SAPISID") == true

    /** YouTube song id -> "Spotify - …" folders, from the user's Spotify playlists and liked songs. */
    @Volatile
    private var spotifyFolders: Map<String, Set<String>> = emptyMap()
    private var spotifyFoldersAt = 0L

    private suspend fun refreshSpotifyFolders(force: Boolean) {
        if (!force && System.currentTimeMillis() - spotifyFoldersAt < SPOTIFY_REFRESH_MS) return
        runCatching { buildSpotifyFolders() }
            .onSuccess {
                spotifyFolders = it
                spotifyFoldersAt = System.currentTimeMillis()
            }
            .onFailure { Timber.tag(TAG).w(it, "Could not read Spotify playlists") }
    }

    private suspend fun buildSpotifyFolders(): Map<String, Set<String>> {
        if (!SpotifyTokenManager.ensureAuthenticated()) return emptyMap()
        val folders = HashMap<String, MutableSet<String>>()
        fun add(spotifyTrackId: String?, folder: String) {
            val youtubeId = spotifyTrackId?.takeIf { it.isNotBlank() }
                ?.let { database.getSpotifyMatch(it)?.youtubeId } ?: return
            folders.getOrPut(youtubeId) { HashSet() }.add(folder)
        }

        var playlistOffset = 0
        while (playlistOffset < MAX_SPOTIFY_PLAYLISTS) {
            val page = Spotify.myPlaylists(limit = 50, offset = playlistOffset).getOrNull() ?: break
            for (playlist in page.items) {
                var trackOffset = 0
                while (trackOffset < MAX_SPOTIFY_TRACKS) {
                    val tracks = Spotify.playlistTracks(playlist.id, limit = 100, offset = trackOffset).getOrNull() ?: break
                    tracks.items.filterNot { it.isLocal }.forEach { add(it.track?.id, "Spotify - ${playlist.name}") }
                    trackOffset += 100
                    if (tracks.items.size < 100) break
                }
            }
            playlistOffset += 50
            if (page.items.size < 50) break
        }

        var likedOffset = 0
        while (likedOffset < MAX_SPOTIFY_TRACKS) {
            val page = Spotify.likedSongs(limit = 50, offset = likedOffset).getOrNull() ?: break
            page.items.forEach { add(it.track.id, "Spotify - Liked songs") }
            likedOffset += 50
            if (page.items.size < 50) break
        }
        return folders
    }

    private suspend fun exportTo(songId: String, folders: FolderCache): Boolean {
        val song = database.getSongByIdBlocking(songId) ?: return false
        val resolver = context.contentResolver
        return try {
            val sourceExtension = sniffExtension(songId)
                ?: downloadFileExtension(database.format(songId).first()?.mimeType)
            val artist = song.artists.joinToString(", ") { it.name }
            val title = if (artist.isBlank()) song.song.title else "$artist - ${song.song.title}"
            val baseName = sanitizeFileName(title, songId)
            val m4aName = "$baseName.m4a"

            val targets = folderNamesFor(song)
                .mapNotNull(folders::directoryId)
                .filter { dirId ->
                    val names = folders.names(dirId)
                    m4aName !in names && "$baseName.$sourceExtension" !in names
                }
            if (targets.isEmpty()) return true

            val workDir = File(context.cacheDir, "download_export").apply { mkdirs() }
            val safeId = songId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val source = File(workDir, "$safeId.$sourceExtension")
            val converted = File(workDir, "$safeId.m4a.tmp")
            try {
                openCache(songId).use { input -> source.outputStream().use { input.copyTo(it) } }
                val (fileToSave, displayName) = if (sourceExtension == "m4a") {
                    source to m4aName
                } else {
                    runCatching {
                        AacTranscoder.transcode(source, converted)
                        converted to m4aName
                    }.getOrElse { error ->
                        Timber.tag(TAG).w(error, "Could not convert $songId to m4a, saving original format")
                        source to "$baseName.$sourceExtension"
                    }
                }

                for (dirId in targets) {
                    val parent = DocumentsContract.buildDocumentUriUsingTree(folders.treeUri, dirId)
                    // octet-stream stops providers from appending their own extension to the name
                    val target = DocumentsContract.createDocument(
                        resolver, parent, "application/octet-stream", displayName,
                    ) ?: continue
                    try {
                        val output = resolver.openOutputStream(target)
                            ?: throw IOException("Could not open $target for writing")
                        output.use { out -> fileToSave.inputStream().use { it.copyTo(out) } }
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(resolver, target) }
                        throw e
                    }
                    folders.names(dirId).add(displayName)
                }
                true
            } finally {
                source.delete()
                converted.delete()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to save $songId to download folder")
            false
        }
    }

    /** Folder ids and file names inside the chosen tree, read once per export run. */
    private inner class FolderCache(val treeUri: Uri) {
        private val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        private val namesByDir = HashMap<String, MutableSet<String>>()
        private val subfolders: MutableMap<String, String> by lazy {
            HashMap<String, String>().also { map ->
                listChildren(rootId) { id, name, mime ->
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) map[name] = id
                }
            }
        }

        /** The root for "", otherwise the named subfolder, created when missing. */
        fun directoryId(name: String): String? {
            if (name.isEmpty()) return rootId
            subfolders[name]?.let { return it }
            return runCatching {
                val created = DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                ) ?: return null
                DocumentsContract.getDocumentId(created).also { subfolders[name] = it }
            }.onFailure { Timber.tag(TAG).w(it, "Could not create folder $name") }.getOrNull()
        }

        fun names(dirId: String): MutableSet<String> = namesByDir.getOrPut(dirId) {
            HashSet<String>().also { set -> listChildren(dirId) { _, name, _ -> set += name } }
        }

        private fun listChildren(dirId: String, onChild: (id: String, name: String, mime: String) -> Unit) {
            runCatching {
                context.contentResolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId),
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null, null, null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        onChild(id, cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty())
                    }
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not list download folder") }
        }
    }

    private fun openCache(songId: String) =
        DataSourceInputStream(
            CacheDataSource(downloadCache, null),
            DataSpec.Builder().setUri(songId).setKey(songId).build(),
        )

    private fun sniffExtension(songId: String): String? = runCatching {
        val header = ByteArray(12)
        val read = openCache(songId).use { it.read(header) }
        if (read < 4) null else audioExtensionFromHeader(header)
    }.getOrNull()

    private companion object {
        const val TAG = "DownloadExporter"
        const val SPOTIFY_REFRESH_MS = 30 * 60 * 1000L
        const val MAX_SPOTIFY_PLAYLISTS = 500
        const val MAX_SPOTIFY_TRACKS = 5000
    }
}

internal fun audioExtensionFromHeader(header: ByteArray): String? {
    fun ascii(from: Int, length: Int) =
        if (header.size >= from + length) String(header, from, length, Charsets.ISO_8859_1) else ""
    return when {
        ascii(0, 4) == "fLaC" -> "flac"
        ascii(0, 4) == "OggS" -> "ogg"
        ascii(4, 4) == "ftyp" -> "m4a"
        ascii(0, 3) == "ID3" -> "mp3"
        header.size >= 4 &&
            header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte() -> "webm"
        header.size >= 2 &&
            header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0 -> "mp3"
        else -> null
    }
}

internal fun downloadFileExtension(mimeType: String?): String =
    when (mimeType?.lowercase()) {
        "audio/webm" -> "webm"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/mpeg" -> "mp3"
        "audio/ogg", "audio/opus" -> "ogg"
        else -> "m4a"
    }

internal fun sanitizeFileName(name: String, fallback: String): String =
    name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_")
        .trim()
        .trim('.')
        .take(150)
        .ifBlank { fallback.replace(":", "_") }
