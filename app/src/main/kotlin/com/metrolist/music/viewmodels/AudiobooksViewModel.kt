/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.audiobooks.Audiobook
import com.metrolist.music.audiobooks.AudiobookProgress
import com.metrolist.music.audiobooks.AudiobookRepository
import com.metrolist.music.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AudiobookSummary(
    val book: Audiobook,
    val progress: AudiobookProgress?,
    /** Zero-based chapter to continue from. */
    val resumeIndex: Int,
    val fractionListened: Float,
) {
    val started get() = progress != null
    val finished get() = progress?.finished == true
}

data class AudiobookDetail(
    val summary: AudiobookSummary,
    val chapters: List<Song>,
)

@HiltViewModel
class AudiobooksViewModel
@Inject
constructor(
    private val repository: AudiobookRepository,
) : ViewModel() {
    val isScanning = repository.isScanning

    private val _hasFolder = MutableStateFlow(repository.folderUri() != null)
    val hasFolder = _hasFolder.asStateFlow()

    private val refreshTick = MutableStateFlow(0)
    val openBookId = MutableStateFlow<String?>(null)

    val summaries =
        combine(repository.books, repository.progress, refreshTick) { books, progress, _ ->
            books.map { summarize(it, progress[it.id], repository.chapters(it)) }
                .sortedByDescending { it.progress?.updatedAt ?: 0L }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val detail =
        combine(openBookId, repository.books, repository.progress, refreshTick) { id, books, progress, _ ->
            val book = books.firstOrNull { it.id == id } ?: return@combine null
            val chapters = repository.chapters(book)
            AudiobookDetail(summarize(book, progress[book.id], chapters), chapters)
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun refresh() {
        refreshTick.value++
    }

    fun setFinished(book: Audiobook, finished: Boolean) = repository.setFinished(book, finished)

    fun rescan() {
        viewModelScope.launch { repository.scan() }
    }

    fun setFolder(uri: Uri?) {
        _hasFolder.value = uri != null
        viewModelScope.launch { repository.setFolder(uri) }
    }

    fun currentFolder(): Uri? = repository.folderUri()

    /** Loads a book's chapters off the main thread, for playing it straight from a list. */
    fun chapters(book: Audiobook, onReady: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val chapters = withContext(Dispatchers.IO) { repository.chapters(book) }
            onReady(chapters)
        }
    }

    private fun summarize(book: Audiobook, progress: AudiobookProgress?, chapters: List<Song>): AudiobookSummary {
        val current = progress?.let { book.chapterIds.indexOf(it.chapterId) }?.takeIf { it >= 0 }
        val durationsMs = chapters.map { it.song.duration.coerceAtLeast(0) * 1000L }
        val totalMs = durationsMs.sum()
        val listenedMs = when {
            progress == null || current == null -> 0L
            progress.finished -> totalMs
            else -> durationsMs.take(current).sum() + (chapters.getOrNull(current)?.song?.playbackPosition ?: 0L)
        }
        return AudiobookSummary(
            book = book,
            progress = progress,
            resumeIndex = if (current == null || progress?.finished == true) 0 else current,
            fractionListened = if (totalMs > 0) (listenedMs.toFloat() / totalMs).coerceIn(0f, 1f) else 0f,
        )
    }
}
