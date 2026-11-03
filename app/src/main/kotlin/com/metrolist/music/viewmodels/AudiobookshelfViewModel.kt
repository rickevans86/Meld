/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.audiobooks.abs.AbsBook
import com.metrolist.music.audiobooks.abs.AbsBookSummary
import com.metrolist.music.audiobooks.abs.AbsHttpException
import com.metrolist.music.audiobooks.abs.AbsLibrary
import com.metrolist.music.audiobooks.abs.AbsProgress
import com.metrolist.music.audiobooks.abs.AbsRepository
import com.metrolist.music.audiobooks.abs.PreparedAbsBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfViewModel
@Inject
constructor(
    private val repository: AbsRepository,
) : ViewModel() {
    val session = repository.session
    val signInExpired = repository.signInExpired

    private val _libraries = MutableStateFlow<List<AbsLibrary>>(emptyList())
    val libraries = _libraries.asStateFlow()

    private val _libraryId = MutableStateFlow(repository.selectedLibraryId())
    val libraryId = _libraryId.asStateFlow()

    private val _inProgress = MutableStateFlow<List<AbsBookSummary>>(emptyList())
    val inProgress = _inProgress.asStateFlow()

    private val _books = MutableStateFlow<List<AbsBookSummary>>(emptyList())
    val books = _books.asStateFlow()

    private val _totalBooks = MutableStateFlow(0)
    val totalBooks = _totalBooks.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, AbsProgress>>(emptyMap())
    val progress = _progress.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AbsBookSummary>?>(null)
    val searchResults = _searchResults.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _openBook = MutableStateFlow<AbsBook?>(null)
    val openBook = _openBook.asStateFlow()

    val openBookId = MutableStateFlow<String?>(null)

    val downloads = repository.downloads

    private val _downloadedBooks = MutableStateFlow<List<AbsBookSummary>>(emptyList())
    val downloadedBooks = _downloadedBooks.asStateFlow()

    private var page = 0
    private var searchJob: Job? = null

    init {
        if (session.value != null) refreshAll()
        viewModelScope.launch {
            downloads.collect { states ->
                _downloadedBooks.value = withContext(Dispatchers.IO) { repository.downloadedBooks() }
                    .filter { states[it.id]?.done == true }
                    .map { AbsBookSummary(it.id, it.title, it.author, it.durationSec) }
            }
        }
    }

    fun download(itemId: String) = repository.download(itemId)

    fun removeDownload(itemId: String) = repository.removeDownload(itemId)

    fun coverUrl(itemId: String): String? = repository.coverUrl(itemId)

    fun signIn(server: String, username: String, password: String) =
        runSignIn { repository.signIn(server, username, password) }

    fun signInWithApiKey(server: String, apiKey: String) =
        runSignIn { repository.signInWithApiKey(server, apiKey) }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _libraries.value = emptyList()
            _books.value = emptyList()
            _inProgress.value = emptyList()
            _progress.value = emptyMap()
            _searchResults.value = null
            _openBook.value = null
            openBookId.value = null
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val libraries = repository.libraries()
                _libraries.value = libraries
                val chosen = _libraryId.value?.takeIf { id -> libraries.any { it.id == id } }
                    ?: libraries.firstOrNull()?.id
                _libraryId.value = chosen
                chosen?.let { repository.selectLibrary(it) }
                loadFirstPage()
                refreshProgress()
            } catch (e: Exception) {
                _error.value = describe(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun selectLibrary(id: String) {
        if (id == _libraryId.value) return
        _libraryId.value = id
        viewModelScope.launch {
            repository.selectLibrary(id)
            _loading.value = true
            runCatching { loadFirstPage() }.onFailure { _error.value = describe(it) }
            _loading.value = false
        }
    }

    fun loadMore() {
        val library = _libraryId.value ?: return
        if (_loading.value || _books.value.size >= _totalBooks.value) return
        viewModelScope.launch {
            _loading.value = true
            runCatching {
                val next = repository.books(library, page + 1, PAGE_SIZE)
                page += 1
                _books.value = _books.value + next.books
            }.onFailure { _error.value = describe(it) }
            _loading.value = false
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        val library = _libraryId.value
        if (query.isBlank() || library == null) {
            _searchResults.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runCatching { repository.search(library, query.trim()) }
                .onSuccess { _searchResults.value = it }
                .onFailure { _error.value = describe(it) }
        }
    }

    fun open(itemId: String) {
        openBookId.value = itemId
        _openBook.value = null
        viewModelScope.launch {
            runCatching { repository.book(itemId) }
                .onSuccess { if (openBookId.value == itemId) _openBook.value = it }
                .onFailure { _error.value = describe(it) }
            runCatching { repository.progress(itemId) }.onSuccess { progress ->
                _progress.value = if (progress == null) _progress.value - itemId else _progress.value + (itemId to progress)
            }
        }
    }

    fun setFinished(book: AbsBook, finished: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setFinished(book, finished) }
                .onSuccess {
                    _progress.value = _progress.value + (
                        book.id to AbsProgress(
                            libraryItemId = book.id,
                            currentTimeSec = if (finished) book.durationSec else 0.0,
                            durationSec = book.durationSec,
                            progress = if (finished) 1.0 else 0.0,
                            isFinished = finished,
                            lastUpdate = System.currentTimeMillis(),
                        )
                        )
                }
                .onFailure { _error.value = it.message ?: "Couldn't update the book on the server." }
        }
    }

    fun close() {
        openBookId.value = null
        _openBook.value = null
    }

    fun refreshProgress() {
        viewModelScope.launch {
            runCatching { repository.allProgress() }
                .onSuccess { list -> _progress.value = list.associateBy { it.libraryItemId } }
            runCatching { repository.inProgress() }
                .onSuccess { _inProgress.value = it }
        }
    }

    /** Prepares the book on the server's terms, then hands it to [onReady] to start playback. */
    fun play(itemId: String, startAtSec: Double?, onReady: (PreparedAbsBook) -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { repository.prepare(itemId, startAtSec) }
                .onSuccess(onReady)
                .onFailure { _error.value = describe(it) }
            _busy.value = false
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private suspend fun loadFirstPage() {
        val library = _libraryId.value ?: return
        val first = repository.books(library, 0, PAGE_SIZE)
        page = 0
        _books.value = first.books
        _totalBooks.value = first.total
    }

    private fun runSignIn(block: suspend () -> Unit) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }
                .onSuccess { refreshAll() }
                .onFailure { _error.value = describe(it, signingIn = true) }
            _busy.value = false
        }
    }

    private fun describe(error: Throwable, signingIn: Boolean = false): String = when {
        error is AbsHttpException && error.code == 401 && signingIn ->
            "The server didn't accept those details. Check them and try again."
        error is AbsHttpException && error.code == 401 ->
            "Your Audiobookshelf sign-in has expired. Sign out and sign in again."
        error is AbsHttpException && error.code == 429 ->
            "Too many attempts. Wait a minute, then try again."
        error is AbsHttpException -> "The server returned an error (${error.code})."
        error is UnknownHostException ->
            "Couldn't find that server. Check the address, and that your phone can reach it."
        else -> error.message ?: "Something went wrong talking to Audiobookshelf."
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
