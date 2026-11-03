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
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
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

    suspend fun export(songId: String): Boolean = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext false
        lock.withLock {
            exportTo(treeUri, songId, existingNames(treeUri))
        }
    }

    /** Returns how many songs are now present in the folder. */
    suspend fun exportAll(songIds: Collection<String>): Int = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext 0
        lock.withLock {
            val existing = existingNames(treeUri)
            songIds.count { exportTo(treeUri, it, existing) }
        }
    }

    private suspend fun exportTo(treeUri: Uri, songId: String, existing: MutableSet<String>): Boolean {
        val song = database.getSongByIdBlocking(songId) ?: return false
        val resolver = context.contentResolver
        return try {
            val extension = sniffExtension(songId)
                ?: downloadFileExtension(database.format(songId).first()?.mimeType)
            val artist = song.artists.joinToString(", ") { it.name }
            val title = if (artist.isBlank()) song.song.title else "$artist - ${song.song.title}"
            val displayName = "${sanitizeFileName(title, songId)}.$extension"
            if (displayName in existing) return true

            val parent = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            // octet-stream stops providers from appending their own extension to the name
            val target = DocumentsContract.createDocument(
                resolver, parent, "application/octet-stream", displayName,
            ) ?: return false
            try {
                copyFromCache(songId, target)
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, target) }
                throw e
            }
            existing += displayName
            true
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to save $songId to download folder")
            false
        }
    }

    private fun existingNames(treeUri: Uri): MutableSet<String> {
        val names = HashSet<String>()
        runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) cursor.getString(0)?.let(names::add)
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not list download folder") }
        return names
    }

    private fun openCache(songId: String) =
        DataSourceInputStream(
            CacheDataSource(downloadCache, null),
            DataSpec.Builder().setUri(songId).setKey(songId).build(),
        )

    private fun copyFromCache(songId: String, target: Uri) {
        val output = context.contentResolver.openOutputStream(target)
            ?: throw IOException("Could not open $target for writing")
        output.use { out ->
            openCache(songId).use { input -> input.copyTo(out) }
        }
    }

    private fun sniffExtension(songId: String): String? = runCatching {
        val header = ByteArray(12)
        val read = openCache(songId).use { it.read(header) }
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
