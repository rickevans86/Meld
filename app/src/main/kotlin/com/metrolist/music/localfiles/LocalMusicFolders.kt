/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localfiles

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.LocalMusicAddedFoldersKey
import com.metrolist.music.constants.LocalMusicExcludedFoldersKey
import com.metrolist.music.constants.LocalMusicIncludedFoldersKey
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.Collator
import javax.inject.Inject
import javax.inject.Singleton

/** One audio file the phone's media index knows about. */
data class IndexedAudio(
    val mediaStoreId: Long,
    val folder: String,
    val isMusic: Boolean,
    val fileName: String = "",
) {
    /** "Music/Meld/song.m4a", the same form [LocalMusicFolders.documentPath] gives for picked folders. */
    val path: String get() = if (folder.isEmpty()) fileName else "$folder/$fileName"
}

data class AudioFolder(
    /** Path relative to the storage root, e.g. "Music/Meld"; empty for the root itself. */
    val path: String,
    val fileCount: Int,
    val included: Boolean,
) {
    val name: String get() = path.substringAfterLast('/')
}

data class FolderChoices(
    val included: Set<String> = emptySet(),
    val excluded: Set<String> = emptySet(),
    /** Folder trees picked by hand, as persisted document-tree URIs. */
    val added: Set<String> = emptySet(),
) {
    /** Folders holding files Android marks as music are shown unless turned off. */
    fun isIncluded(folder: String, hasMusic: Boolean): Boolean =
        folder in included || (hasMusic && folder !in excluded)
}

/**
 * Which folders count as local music. Reads every audio file in the phone's media index,
 * not only files flagged as music, so folders such as Download/... can be turned on.
 */
@Singleton
class LocalMusicFolders
@Inject
constructor(
    @ApplicationContext private val context: Context,
) {
    private val _index = MutableStateFlow<List<IndexedAudio>?>(null)
    val index = _index.asStateFlow()

    val choices = context.dataStore.data.map { it.toChoices() }

    val folders =
        combine(index, choices) { index, choices ->
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            index.orEmpty()
                .groupBy { it.folder }
                .map { (path, files) ->
                    AudioFolder(path, files.size, choices.isIncluded(path, files.any { it.isMusic }))
                }
                .sortedWith(compareBy(collator) { it.path })
        }

    suspend fun currentChoices(): FolderChoices = choices.first()

    /** Media-store id -> folder, for files in folders that are turned on. */
    suspend fun includedFolderById(): Map<Long, String> {
        val index = _index.value ?: refreshIndex()
        val choices = currentChoices()
        val musicFolders = index.filter { it.isMusic }.mapTo(HashSet()) { it.folder }
        return index
            .filter { choices.isIncluded(it.folder, it.folder in musicFolders) }
            .associate { it.mediaStoreId to it.folder }
    }

    suspend fun setIncluded(folder: String, include: Boolean) {
        context.dataStore.edit { prefs ->
            val included = prefs[LocalMusicIncludedFoldersKey].orEmpty()
            val excluded = prefs[LocalMusicExcludedFoldersKey].orEmpty()
            prefs[LocalMusicIncludedFoldersKey] = if (include) included + folder else included - folder
            prefs[LocalMusicExcludedFoldersKey] = if (include) excluded - folder else excluded + folder
        }
    }

    suspend fun addFolder(treeUri: Uri) {
        context.dataStore.edit { prefs ->
            prefs[LocalMusicAddedFoldersKey] = prefs[LocalMusicAddedFoldersKey].orEmpty() + treeUri.toString()
        }
    }

    suspend fun removeFolder(treeUri: String) {
        context.dataStore.edit { prefs ->
            prefs[LocalMusicAddedFoldersKey] = prefs[LocalMusicAddedFoldersKey].orEmpty() - treeUri
        }
    }

    @Suppress("DEPRECATION")
    suspend fun refreshIndex(): List<IndexedAudio> = withContext(Dispatchers.IO) {
        val useRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val pathColumn = if (useRelativePath) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val storageRoot = Environment.getExternalStorageDirectory().absolutePath
        val entries = mutableListOf<IndexedAudio>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    pathColumn,
                    MediaStore.Audio.Media.IS_MUSIC,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val pathCol = cursor.getColumnIndexOrThrow(pathColumn)
                val musicCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val raw = cursor.getString(pathCol) ?: continue
                    val folder = if (useRelativePath) {
                        raw.trim('/')
                    } else {
                        File(raw).parent?.removePrefix(storageRoot)?.trim('/') ?: continue
                    }
                    val name = cursor.getString(nameCol) ?: if (useRelativePath) "" else File(raw).name
                    entries += IndexedAudio(cursor.getLong(idCol), folder, cursor.getInt(musicCol) != 0, name)
                }
            }
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "No permission to read audio files")
        }
        // A file rewritten in place can be listed twice; keep only its newest entry
        val unique = entries
            .groupBy { if (it.fileName.isEmpty()) "id:${it.mediaStoreId}" else it.path }
            .map { (_, copies) -> copies.maxBy { it.mediaStoreId } }
        _index.value = unique
        unique
    }

    private fun Preferences.toChoices() = FolderChoices(
        included = this[LocalMusicIncludedFoldersKey].orEmpty(),
        excluded = this[LocalMusicExcludedFoldersKey].orEmpty(),
        added = this[LocalMusicAddedFoldersKey].orEmpty(),
    )

    companion object {
        private const val TAG = "LocalMusicFolders"

        /** Folder of a file inside a picked folder tree, as "Music/Albums/X". */
        fun documentFolder(documentUri: String): String? = runCatching {
            DocumentsContract.getDocumentId(documentUri.toUri())
                .substringAfter(':', "")
                .substringBeforeLast('/', "")
        }.getOrNull()

        /** Path of a file inside a picked folder tree, as "Music/Meld/song.m4a". */
        fun documentPath(documentUri: String): String? = runCatching {
            DocumentsContract.getDocumentId(documentUri.toUri()).substringAfter(':', "")
        }.getOrNull()

        fun treeLabel(treeUri: String): String = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri.toUri()).substringAfter(':', "")
        }.getOrDefault(treeUri)
    }
}
