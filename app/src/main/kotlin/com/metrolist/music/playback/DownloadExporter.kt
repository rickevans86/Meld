/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import com.metrolist.music.constants.DownloadExportFolderKey
import com.metrolist.music.constants.DownloadPlaylistFoldersKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.podcasts.PodcastDownloadStorage
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

    /** Playlist names are captured when Download is requested, never inferred from membership. */
    fun sortsByPlaylist(): Boolean = context.dataStore[DownloadPlaylistFoldersKey] ?: true

    suspend fun request(songId: String, folder: String?, sourcePlaylistId: String? = null, episodeHint: Boolean = false) {
        if (PodcastDownloadStorage.isPrivate(context, database, songId, episodeHint, sourcePlaylistId)) {
            PodcastDownloadStorage.remember(context, songId)
            context.dataStore.edit { it[targetsKey(songId)] = emptySet() }
            return
        }
        val destination = folder?.let { sanitizeFileName(it, "Playlist") }
            ?: if (database.playlistFoldersForSong(songId).isEmpty()) "No Playlist" else ""
        context.dataStore.edit { prefs ->
            val key = targetsKey(songId)
            prefs[key] = prefs[key].orEmpty() + destination
            // Wavelength: remember which playlist a download came from, so the download-management
            // screen can group downloads by playlist even for Spotify (whose track lists aren't
            // stored locally). Keyed by song; the source's display name is kept alongside.
            if (sourcePlaylistId != null) {
                prefs[sourceKey(songId)] = sourcePlaylistId
                if (!folder.isNullOrBlank()) prefs[sourceNameKey(sourcePlaylistId)] = folder
            }
        }
    }

    private fun sourceKey(songId: String) = stringPreferencesKey("downloadSource:$songId")
    private fun sourceNameKey(sourceId: String) = stringPreferencesKey("downloadSourceName:$sourceId")

    suspend fun export(songId: String): Boolean = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext false
        lock.withLock { exportPending(songId, FolderCache(treeUri)) }
    }

    suspend fun exportAll(songIds: Collection<String>): Int = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext 0
        lock.withLock {
            val folders = FolderCache(treeUri)
            songIds.count {
                if (PodcastDownloadStorage.isPrivate(context, database, it)) false else {
                    if (context.dataStore[targetsKey(it)] == null || requestedFolders(it).isEmpty()) request(it, null)
                    exportPending(it, folders)
                }
            }
        }
    }

    internal fun requestedFolders(songId: String): List<String> {
        val pending = context.dataStore[targetsKey(songId)]?.toList() ?: listOf("")
        return if (pending.isEmpty() || sortsByPlaylist()) pending else listOf("")
    }

    private suspend fun completeRequest(songId: String, folders: List<String>) {
        context.dataStore.edit { prefs ->
            val key = targetsKey(songId)
            prefs[key] = prefs[key].orEmpty() - folders.toSet()
        }
    }

    private fun targetsKey(songId: String) = stringSetPreferencesKey("downloadExportTargets:$songId")
    private fun copiesKey(songId: String) = stringSetPreferencesKey("downloadExportCopies:$songId")

    private fun existingCopy(songId: String): Uri? =
        context.dataStore[copiesKey(songId)].orEmpty().firstNotNullOfOrNull { raw ->
            runCatching {
                raw.toUri().takeIf { uri -> context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } == true }
            }.getOrNull()
        }

    private fun openSource(songId: String, copy: Uri?) =
        copy?.let { runCatching { context.contentResolver.openInputStream(it) }.getOrNull() } ?: openCache(songId)

    private suspend fun exportPending(songId: String, folders: FolderCache): Boolean {
        do {
            if (!exportTo(songId, folders)) return false
        } while (requestedFolders(songId).isNotEmpty())
        return true
    }

    private suspend fun exportTo(songId: String, folders: FolderCache): Boolean {
        val song = database.getSongByIdBlocking(songId) ?: return false
        if (PodcastDownloadStorage.isPrivate(context, database, songId)) {
            PodcastDownloadStorage.remember(context, songId)
            context.dataStore.edit { it[targetsKey(songId)] = emptySet() }
            return true
        }
        val resolver = context.contentResolver
        return try {
            val pending = context.dataStore[targetsKey(songId)]?.toList() ?: listOf("")
            val requested = if (sortsByPlaylist()) pending else pending.takeIf { it.isNotEmpty() }?.let { listOf("") }.orEmpty()
            if (requested.isEmpty()) return true
            val copy = existingCopy(songId)
            val sourceExtension = sniffExtension(songId, copy)
                ?: downloadFileExtension(database.format(songId).first()?.mimeType)
            val artist = song.artists.joinToString(", ") { it.name }
            val title = if (artist.isBlank()) song.song.title else "$artist - ${song.song.title}"
            val baseName = sanitizeFileName(title, songId)
            val m4aName = "$baseName.m4a"

            val targets = requested
                .map { folders.directoryId(it) ?: return false }
                .filter { dirId ->
                    val names = folders.names(dirId)
                    m4aName !in names && "$baseName.$sourceExtension" !in names
                }
            if (targets.isEmpty()) {
                completeRequest(songId, pending)
                return true
            }

            val workDir = File(context.cacheDir, "download_export").apply { mkdirs() }
            val safeId = songId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val source = File(workDir, "$safeId.$sourceExtension")
            val converted = File(workDir, "$safeId.m4a.tmp")
            try {
                openSource(songId, copy).use { input -> source.outputStream().use { input.copyTo(it) } }
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
                    ) ?: throw IOException("Could not create exported audio file")
                    try {
                        val output = resolver.openOutputStream(target)
                            ?: throw IOException("Could not open $target for writing")
                        output.use { out -> fileToSave.inputStream().use { it.copyTo(out) } }
                    } catch (e: Exception) {
                        runCatching { DocumentsContract.deleteDocument(resolver, target) }
                        throw e
                    }
                    folders.names(dirId).add(displayName)
                    context.dataStore.edit { prefs ->
                        val key = copiesKey(songId)
                        prefs[key] = prefs[key].orEmpty() + target.toString()
                    }
                }
                completeRequest(songId, pending)
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

    private fun sniffExtension(songId: String, copy: Uri?): String? = runCatching {
        val header = ByteArray(12)
        val read = openSource(songId, copy).use { it.read(header) }
        if (read < 4) null else audioExtensionFromHeader(header)
    }.getOrNull()

    private companion object {
        const val TAG = "DownloadExporter"
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
