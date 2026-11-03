/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.audiobooks

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.AUDIOBOOK_ID_PREFIX
import com.metrolist.music.constants.AudiobooksFolderKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.text.Collator
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class Audiobook(
    val id: String,
    val title: String,
    val author: String? = null,
    val coverUri: String? = null,
    val chapterIds: List<String>,
)

@Serializable
data class AudiobookProgress(
    val chapterId: String,
    val updatedAt: Long,
    val finished: Boolean = false,
)

@Serializable
private data class AudiobookStore(
    val books: List<Audiobook> = emptyList(),
    val progress: Map<String, AudiobookProgress> = emptyMap(),
)

/**
 * Audiobooks stored on the device, read from a folder the user picks.
 *
 * A folder that directly contains audio files is one book and each file is a chapter;
 * audio files sitting in the top-level folder are single-file books (e.g. .m4b).
 * Chapters are stored as local songs with an [AUDIOBOOK_ID_PREFIX] id, so the existing
 * player, position memory and sleep timer work on them without schema changes.
 */
@Singleton
class AudiobookRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val storeFile = File(context.filesDir, "audiobooks.json")
    private val artworkDir = File(context.cacheDir, "artwork")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storeLock = Mutex()
    private val scanLock = Mutex()

    private val _books = MutableStateFlow<List<Audiobook>>(emptyList())
    val books = _books.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, AudiobookProgress>>(emptyMap())
    val progress = _progress.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    @Volatile
    private var chapterToBook: Map<String, Audiobook> = emptyMap()

    init {
        scope.launch { load() }
    }

    fun folderUri(): Uri? =
        context.dataStore[AudiobooksFolderKey]?.takeIf { it.isNotBlank() }?.toUri()

    suspend fun setFolder(uri: Uri?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(AudiobooksFolderKey) else prefs[AudiobooksFolderKey] = uri.toString()
        }
        if (uri == null) {
            _books.value = emptyList()
            chapterToBook = emptyMap()
            save()
        } else {
            scan()
        }
    }

    fun bookForChapter(chapterId: String): Audiobook? = chapterToBook[chapterId]

    suspend fun chapters(book: Audiobook): List<Song> {
        val byId = database.getSongsByIds(book.chapterIds).associateBy { it.id }
        return book.chapterIds.mapNotNull(byId::get)
    }

    fun onChapterStarted(chapterId: String) {
        val book = chapterToBook[chapterId] ?: return
        _progress.update { it + (book.id to AudiobookProgress(chapterId, System.currentTimeMillis())) }
        scope.launch { save() }
    }

    /** A chapter played to its end: forget its position so it starts from the beginning next time. */
    fun onChapterFinished(chapterId: String) {
        scope.launch {
            database.updatePlaybackPosition(chapterId, null)
            val book = chapterToBook[chapterId] ?: return@launch
            if (book.chapterIds.lastOrNull() == chapterId) {
                _progress.update {
                    it + (book.id to AudiobookProgress(chapterId, System.currentTimeMillis(), finished = true))
                }
                save()
            }
        }
    }

    suspend fun scan(): Int = withContext(Dispatchers.IO) {
        val treeUri = folderUri() ?: return@withContext 0
        scanLock.withLock {
            _isScanning.value = true
            try {
                artworkDir.mkdirs()
                val found = mutableListOf<Audiobook>()
                walk(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "", null, 0, found)
                val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
                val sorted = found.sortedWith(compareBy(collator) { it.title })
                _books.value = sorted
                chapterToBook = index(sorted)
                val ids = sorted.mapTo(HashSet()) { it.id }
                _progress.update { progress -> progress.filterKeys { it in ids } }
                save()
                sorted.size
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Audiobook scan failed")
                -1
            } finally {
                _isScanning.value = false
            }
        }
    }

    private suspend fun walk(
        treeUri: Uri,
        documentId: String,
        name: String,
        parentName: String?,
        depth: Int,
        out: MutableList<Audiobook>,
    ) {
        val children = listChildren(treeUri, documentId)
        val audio = children.filter { it.isAudio }.sortedWith(compareBy(naturalNameOrder) { it.name })

        if (depth == 0) {
            audio.forEach { file ->
                out += buildBook(treeUri, file.documentId, file.name.substringBeforeLast('.'), null, listOf(file), emptyList(), true)
            }
        } else if (audio.isNotEmpty()) {
            out += buildBook(treeUri, documentId, name, parentName, audio, children.filter { it.isImage }, false)
            return
        }

        if (depth < MAX_DEPTH) {
            children.filter { it.isDirectory }.forEach { dir ->
                walk(treeUri, dir.documentId, dir.name, if (depth == 0) null else name, depth + 1, out)
            }
        }
    }

    private suspend fun buildBook(
        treeUri: Uri,
        bookDocumentId: String,
        folderName: String,
        parentName: String?,
        files: List<DocumentEntry>,
        images: List<DocumentEntry>,
        singleFile: Boolean,
    ): Audiobook {
        val bookId = "book:" + hash(bookDocumentId)
        val fileUris = files.map { DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId) }
        val firstTags = readTags(fileUris.first(), extractArtwork = images.isEmpty())

        val title = firstTags.album?.takeIf { it.isNotBlank() }
            ?: if (singleFile) firstTags.title?.takeIf { it.isNotBlank() } ?: folderName else folderName
        val author = listOf(firstTags.albumArtist, firstTags.artist, firstTags.author, parentName)
            .firstOrNull { !it.isNullOrBlank() }

        val cover = pickCoverImage(images)?.let {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId).toString()
        } ?: firstTags.artwork?.let { bytes ->
            val file = File(artworkDir, "audiobook_${hash(bookDocumentId)}.jpg")
            runCatching { file.writeBytes(bytes) }.getOrNull()?.let { Uri.fromFile(file).toString() }
        }

        val chapterIds = files.mapIndexed { index, file ->
            val chapterId = AUDIOBOOK_ID_PREFIX + hash(file.documentId)
            val uri = fileUris[index].toString()
            val existing = database.getSongById(chapterId)?.song
            if (existing == null) {
                val tags = if (index == 0) firstTags else readTags(fileUris[index], extractArtwork = false)
                val chapterTitle = if (singleFile) {
                    title
                } else {
                    tags.title?.takeIf { it.isNotBlank() } ?: file.name.substringBeforeLast('.')
                }
                val entity = SongEntity(
                    id = chapterId,
                    title = chapterTitle,
                    duration = (tags.durationMs / 1000).toInt(),
                    thumbnailUrl = cover,
                    albumName = title,
                    isLocal = true,
                    localPath = uri,
                )
                with(database) {
                    upsert(entity)
                    if (author != null) {
                        val artistId = artistByName(author)?.id ?: ArtistEntity(
                            id = ArtistEntity.generateArtistId(),
                            name = author,
                        ).also { insert(it) }.id
                        insert(SongArtistMap(songId = chapterId, artistId = artistId, position = 0))
                    }
                }
            } else if (existing.localPath != uri || existing.thumbnailUrl != cover || existing.albumName != title) {
                with(database) {
                    upsert(existing.copy(localPath = uri, thumbnailUrl = cover, albumName = title))
                }
            }
            chapterId
        }

        return Audiobook(bookId, title, author, cover, chapterIds)
    }

    private fun listChildren(treeUri: Uri, documentId: String): List<DocumentEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val entries = mutableListOf<DocumentEntry>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                if (name.startsWith(".")) continue
                entries += DocumentEntry(id, name, cursor.getString(2).orEmpty())
            }
        }
        return entries
    }

    private fun readTags(uri: Uri, extractArtwork: Boolean): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Tags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                artwork = if (extractArtwork) retriever.embeddedPicture else null,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not read tags from $uri")
            Tags()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun load() {
        val store = runCatching {
            if (storeFile.exists()) json.decodeFromString<AudiobookStore>(storeFile.readText()) else null
        }.onFailure { Timber.tag(TAG).w(it, "Could not read audiobook list") }.getOrNull() ?: return
        _books.value = store.books
        _progress.value = store.progress
        chapterToBook = index(store.books)
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        storeLock.withLock {
            runCatching {
                val tmp = File(storeFile.parentFile, storeFile.name + ".tmp")
                tmp.writeText(json.encodeToString(AudiobookStore.serializer(), AudiobookStore(_books.value, _progress.value)))
                if (!tmp.renameTo(storeFile)) {
                    storeFile.writeText(tmp.readText())
                    tmp.delete()
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not save audiobook list") }
        }
    }

    private fun index(books: List<Audiobook>): Map<String, Audiobook> =
        buildMap { books.forEach { book -> book.chapterIds.forEach { put(it, book) } } }

    private data class DocumentEntry(val documentId: String, val name: String, val mimeType: String) {
        val extension get() = name.substringAfterLast('.', "").lowercase()
        val isDirectory get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
        val isAudio get() = !isDirectory && (mimeType.startsWith("audio/") || extension in AUDIO_EXTENSIONS)
        val isImage get() = mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS
    }

    private class Tags(
        val title: String? = null,
        val album: String? = null,
        val albumArtist: String? = null,
        val artist: String? = null,
        val author: String? = null,
        val durationMs: Long = 0L,
        val artwork: ByteArray? = null,
    )

    private fun pickCoverImage(images: List<DocumentEntry>): DocumentEntry? =
        images.firstOrNull { it.name.substringBeforeLast('.').lowercase() in COVER_NAMES } ?: images.firstOrNull()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private companion object {
        const val TAG = "Audiobooks"
        const val MAX_DEPTH = 4
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "ogg", "oga", "opus", "flac", "wav", "wma", "mka")
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
        val COVER_NAMES = setOf("cover", "folder", "front", "poster")
    }
}

/** Orders "Chapter 2" before "Chapter 10". */
internal val naturalNameOrder: Comparator<String> = Comparator { a, b ->
    val tokens = Regex("""\d+|\D+""")
    val left = tokens.findAll(a.lowercase()).map { it.value }.toList()
    val right = tokens.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
        val x = left[i]
        val y = right[i]
        val result = if (x[0].isDigit() && y[0].isDigit()) {
            val xs = x.trimStart('0')
            val ys = y.trimStart('0')
            if (xs.length != ys.length) xs.length - ys.length else xs.compareTo(ys)
        } else {
            x.compareTo(y)
        }
        if (result != 0) return@Comparator result
    }
    left.size - right.size
}
