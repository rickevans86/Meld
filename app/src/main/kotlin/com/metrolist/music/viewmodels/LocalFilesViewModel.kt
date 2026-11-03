/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.SongSortDescendingKey
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.constants.SongSortTypeKey
import com.metrolist.music.constants.isAudiobookId
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.localfiles.LocalMusicFolders
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import javax.inject.Inject

data class LocalFolder(
    val path: String,
    val name: String,
    val songs: List<Song>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LocalFilesViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
    val musicFolders: LocalMusicFolders,
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    private var rescanRequested = false

    private val allLocalSongs =
        context.dataStore.data
            .map {
                it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey] ?: true)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.localSongs(sortType, descending)
            }

    /** Song id -> folder path relative to storage root. */
    private val folderPaths =
        combine(musicFolders.index, musicFolders.choices) { index, choices ->
            if (index == null) return@combine null
            val foldersWithMusic = index.filter { it.isMusic }.mapTo(HashSet()) { it.folder }
            index
                .filter { choices.isIncluded(it.folder, it.folder in foldersWithMusic) }
                .associate { "local:${it.mediaStoreId}" to it.folder }
        }

    val songs =
        combine(allLocalSongs, folderPaths, musicFolders.choices, musicFolders.index) { songs, paths, choices, index ->
            // A file in a hand-added folder that the phone also indexes would otherwise show twice
            val indexedPaths = index.orEmpty().mapTo(HashSet()) { it.path }
            songs.filter { song ->
                val id = song.id
                when {
                    isAudiobookId(id) -> false
                    id.startsWith(PICKED_PREFIX) ->
                        choices.added.any { tree -> song.song.localPath?.startsWith(tree) == true } &&
                            song.song.localPath?.let(LocalMusicFolders::documentPath) !in indexedPaths
                    // Until the media index is read, keep showing what was there before
                    paths == null -> true
                    else -> id in paths
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** The all-songs list: copies of the same song (e.g. exported into several playlist folders) shown once. */
    val uniqueSongs =
        songs.map { LocalMusicFolders.uniqueCopies(it) }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val folders =
        combine(songs, folderPaths) { songs, paths ->
            songs
                .groupBy { song ->
                    paths?.get(song.id)
                        ?: song.song.localPath?.takeIf { song.id.startsWith(PICKED_PREFIX) }
                            ?.let(LocalMusicFolders::documentFolder)
                }
                .mapNotNull { (path, folderSongs) ->
                    path?.let {
                        LocalFolder(
                            path = it,
                            name = it.substringAfterLast('/'),
                            songs = folderSongs,
                        )
                    }
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.path })
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun loadFolders() {
        if (musicFolders.index.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                musicFolders.refreshIndex()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error reading local folders")
            }
        }
    }

    fun setFolderIncluded(folder: String, include: Boolean) {
        viewModelScope.launch {
            musicFolders.setIncluded(folder, include)
            if (include) scanLocalFiles()
        }
    }

    fun addFolder(treeUri: Uri) {
        viewModelScope.launch {
            musicFolders.addFolder(treeUri)
            scanLocalFiles()
        }
    }

    fun removeFolder(treeUri: String) {
        viewModelScope.launch {
            musicFolders.removeFolder(treeUri)
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    treeUri.toUri(),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun scanLocalFiles() {
        if (_isScanning.value) {
            rescanRequested = true
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                do {
                    rescanRequested = false
                    musicFolders.refreshIndex()
                    performScan()
                    scanPickedFolders()
                } while (rescanRequested)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error scanning local files")
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun performScan() {
        val artworkDir = File(context.cacheDir, "artwork").also { it.mkdirs() }
        val includedIds = musicFolders.includedFolderById().keys

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val mediaStoreId = cursor.getLong(idCol)
                if (mediaStoreId !in includedIds) continue
                val title = cursor.getString(titleCol) ?: continue
                val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" }
                val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" }
                val durationMs = cursor.getLong(durationCol)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mediaStoreId.toString()
                ).toString()

                saveSong(
                    songId = "local:$mediaStoreId",
                    contentUri = contentUri,
                    artworkDir = artworkDir,
                    tags = { Tags(title, artist, album, durationMs) },
                )
            }
        }
    }

    /** Folders added by hand, for audio the phone's media index doesn't list. */
    private suspend fun scanPickedFolders() {
        val artworkDir = File(context.cacheDir, "artwork").also { it.mkdirs() }
        val trees = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        val indexedPaths = musicFolders.index.value.orEmpty().mapTo(HashSet()) { it.path }
        val added = musicFolders.currentChoices().added
        for (tree in added) {
            if (tree !in trees) continue
            val treeUri = tree.toUri()
            runCatching {
                walkTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri), 0) { documentId ->
                    if (documentId.substringAfter(':', "") in indexedPaths) return@walkTree
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString()
                    saveSong(
                        songId = PICKED_PREFIX + hash(documentId),
                        contentUri = fileUri,
                        artworkDir = artworkDir,
                        tags = { readTags(fileUri, documentId) },
                    )
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not scan $tree") }
        }
    }

    private fun walkTree(treeUri: Uri, documentId: String, depth: Int, onAudio: (String) -> Unit) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val folders = mutableListOf<String>()
        context.contentResolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1).orEmpty()
                val mime = cursor.getString(2).orEmpty()
                if (name.startsWith(".")) continue
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> folders += id
                    mime.startsWith("audio/") ||
                        name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS -> onAudio(id)
                }
            }
        }
        if (depth < MAX_DEPTH) folders.forEach { walkTree(treeUri, it, depth + 1, onAudio) }
    }

    private class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long,
    )

    private fun readTags(uri: String, documentId: String): Tags {
        val fallbackTitle = documentId.substringAfterLast('/').substringBeforeLast('.')
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri.toUri())
            Tags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() } ?: fallbackTitle,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not read tags from $uri")
            Tags(fallbackTitle, null, null, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun saveSong(
        songId: String,
        contentUri: String,
        artworkDir: File,
        tags: () -> Tags,
    ) {
        val existing = database.getSongByIdBlocking(songId)
        if (existing == null) {
            val info = tags()
            val title = info.title ?: return
            val artist = info.artist?.takeIf { it.isNotBlank() }
            val songEntity = SongEntity(
                id = songId,
                title = title,
                duration = (info.durationMs / 1000).toInt(),
                thumbnailUrl = extractAndCacheArtwork(contentUri, songId, artworkDir),
                albumName = info.album,
                isLocal = true,
                localPath = contentUri,
                inLibrary = LocalDateTime.now(),
            )

            database.query {
                upsert(songEntity)
                if (artist != null) {
                    val existingArtist = artistByName(artist)
                    val artistId = existingArtist?.id ?: run {
                        val newArtist = ArtistEntity(
                            id = ArtistEntity.generateArtistId(),
                            name = artist
                        )
                        insert(newArtist)
                        newArtist.id
                    }
                    insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                }
            }
        } else {
            if (existing.song.localPath != contentUri) {
                database.query { setLocalPath(songId, contentUri) }
            }
            // Backfill artwork for songs that were imported before artwork extraction
            if (existing.song.thumbnailUrl == null) {
                val thumbnailUri = extractAndCacheArtwork(contentUri, songId, artworkDir)
                if (thumbnailUri != null) {
                    database.query {
                        upsert(existing.song.copy(thumbnailUrl = thumbnailUri))
                    }
                }
            }
        }
    }

    /**
     * Extracts embedded cover art from a local audio file via its content URI
     * and saves it to the artwork cache directory. Returns a file:// URI
     * pointing to the cached image, or null if no artwork is embedded.
     */
    private fun extractAndCacheArtwork(
        contentUri: String,
        songId: String,
        artworkDir: File,
    ): String? {
        val safeFileName = songId.replace(":", "_") + ".jpg"
        val artworkFile = File(artworkDir, safeFileName)

        if (artworkFile.exists()) {
            return Uri.fromFile(artworkFile).toString()
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(contentUri))
            val artBytes = retriever.embeddedPicture ?: return null
            artworkFile.writeBytes(artBytes)
            Uri.fromFile(artworkFile).toString()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract artwork for $songId")
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private companion object {
        const val TAG = "LocalFilesVM"
        /** Songs from folders added by hand; still "local:" so playback resolves them from their path. */
        const val PICKED_PREFIX = "local:doc:"
        const val MAX_DEPTH = 6
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "wma", "mka")
    }
}
