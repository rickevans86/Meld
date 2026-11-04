/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.entities.Song
import com.metrolist.music.subsonic.SubsonicAlbum
import com.metrolist.music.subsonic.SubsonicArtist
import com.metrolist.music.subsonic.SubsonicFolderNode
import com.metrolist.music.subsonic.SubsonicPlaylist
import com.metrolist.music.subsonic.SubsonicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SubsonicTab { ALBUMS, ARTISTS, PLAYLISTS, FOLDERS, STARRED }

/** A page opened from the server browser; songs/albums are null while loading. */
sealed interface SubsonicPage {
    val title: String

    data class Album(val album: SubsonicAlbum, val songs: List<Song>? = null) : SubsonicPage {
        override val title get() = album.name
    }
    data class Artist(val artist: SubsonicArtist, val albums: List<SubsonicAlbum>? = null) : SubsonicPage {
        override val title get() = artist.name
    }
    data class Playlist(val playlist: SubsonicPlaylist, val songs: List<Song>? = null) : SubsonicPage {
        override val title get() = playlist.name
    }
}

data class SubsonicSearchResults(
    val artists: List<SubsonicArtist>,
    val albums: List<SubsonicAlbum>,
    val songs: List<Song>,
)

@HiltViewModel
class SubsonicViewModel
@Inject
constructor(
    private val repository: SubsonicRepository,
) : ViewModel() {
    val session = repository.session

    private val _signingIn = MutableStateFlow(false)
    val signingIn = _signingIn.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _tab = MutableStateFlow(SubsonicTab.ALBUMS)
    val tab = _tab.asStateFlow()

    private val _albumSort = MutableStateFlow("newest")
    val albumSort = _albumSort.asStateFlow()

    private val _albums = MutableStateFlow<List<SubsonicAlbum>?>(null)
    val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<SubsonicArtist>?>(null)
    val artists = _artists.asStateFlow()

    private val _playlists = MutableStateFlow<List<SubsonicPlaylist>?>(null)
    val playlists = _playlists.asStateFlow()

    private val _starred = MutableStateFlow<List<Song>?>(null)
    val starred = _starred.asStateFlow()

    private val _search = MutableStateFlow<SubsonicSearchResults?>(null)
    val search = _search.asStateFlow()

    private val _pages = MutableStateFlow<List<SubsonicPage>>(emptyList())
    val pages = _pages.asStateFlow()

    private var searchJob: Job? = null

    init {
        if (repository.session.value != null) loadTab()
    }

    fun signIn(server: String, username: String, password: String) {
        if (_signingIn.value) return
        viewModelScope.launch {
            _signingIn.value = true
            runCatching { repository.signIn(server, username, password) }
                .onSuccess { loadTab() }
                .onFailure { _message.value = it.message ?: "Couldn't sign in." }
            _signingIn.value = false
        }
    }

    fun signOut() {
        repository.signOut()
        _albums.value = null
        _artists.value = null
        _playlists.value = null
        _folderNode.value = null
        _folderSongs.value = null
        _folderPath.value = emptyList()
        _starred.value = null
        _search.value = null
        _pages.value = emptyList()
    }

    fun selectTab(tab: SubsonicTab) {
        _tab.value = tab
        loadTab()
    }

    fun sortAlbums(type: String) {
        _albumSort.value = type
        _albums.value = null
        loadTab()
    }

    fun refresh() {
        when (_tab.value) {
            SubsonicTab.ALBUMS -> _albums.value = null
            SubsonicTab.ARTISTS -> _artists.value = null
            SubsonicTab.PLAYLISTS -> _playlists.value = null
            SubsonicTab.FOLDERS -> {
                openFolderPath(_folderPath.value, refresh = true)
                return
            }
            SubsonicTab.STARRED -> _starred.value = null
        }
        loadTab()
    }

    private fun loadTab() {
        val tab = _tab.value
        viewModelScope.launch {
            runCatching {
                when (tab) {
                    SubsonicTab.ALBUMS -> if (_albums.value == null) _albums.value = repository.albums(_albumSort.value)
                    SubsonicTab.ARTISTS -> if (_artists.value == null) _artists.value = repository.artists()
                    SubsonicTab.PLAYLISTS -> if (_playlists.value == null) _playlists.value = repository.playlists()
                    SubsonicTab.FOLDERS -> if (_folderNode.value == null) openFolderPath(_folderPath.value)
                    SubsonicTab.STARRED -> _starred.value = repository.prepare(repository.starredSongs())
                }
            }.onFailure { _message.value = it.message ?: "Couldn't load from the server." }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _search.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            runCatching {
                val result = repository.search(query.trim())
                SubsonicSearchResults(result.artists, result.albums, repository.prepare(result.songs))
            }.onSuccess { _search.value = it }
                .onFailure { _message.value = it.message ?: "Search failed." }
        }
    }

    fun openAlbum(album: SubsonicAlbum) = push(SubsonicPage.Album(album)) {
        SubsonicPage.Album(album, repository.prepare(repository.albumSongs(album.id)))
    }

    fun openArtist(artist: SubsonicArtist) = push(SubsonicPage.Artist(artist)) {
        SubsonicPage.Artist(artist, repository.artistAlbums(artist.id))
    }

    fun openPlaylist(playlist: SubsonicPlaylist) = push(SubsonicPage.Playlist(playlist)) {
        SubsonicPage.Playlist(playlist, repository.prepare(repository.playlistSongs(playlist.id)))
    }

    // ── Folders: a file-manager view built from the songs' real locations on the server ──
    /** Folder names from the top of the library down to the open folder. */
    private val _folderPath = MutableStateFlow<List<String>>(emptyList())
    val folderPath = _folderPath.asStateFlow()

    private val _folderNode = MutableStateFlow<SubsonicFolderNode?>(null)
    val folderNode = _folderNode.asStateFlow()

    /** Playable songs directly inside the open folder (null while preparing). */
    private val _folderSongs = MutableStateFlow<List<Song>?>(null)
    val folderSongs = _folderSongs.asStateFlow()

    private val _libraryName = MutableStateFlow("Music")
    val libraryName = _libraryName.asStateFlow()

    private var folderJob: Job? = null

    fun openFolderPath(path: List<String>, refresh: Boolean = false) {
        _folderPath.value = path
        _folderSongs.value = null
        if (refresh) _folderNode.value = null
        folderJob?.cancel()
        folderJob = viewModelScope.launch {
            runCatching {
                if (refresh || _folderNode.value == null) {
                    repository.libraryName()?.let { _libraryName.value = it }
                }
                val node = repository.treeNode(path, refresh) ?: repository.treeNode(emptyList())!!.also { _folderPath.value = emptyList() }
                _folderNode.value = node
                repository.rememberTreeFolder(node)
                _folderSongs.value = repository.prepare(node.songs)
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _message.value = it.message ?: "Couldn't load the folders."
                _folderSongs.value = emptyList()
            }
        }
    }

    fun openSubFolder(name: String) = openFolderPath(_folderPath.value + name)

    /** Goes up one folder; false when already at the top. */
    fun folderUp(): Boolean {
        if (_folderPath.value.isEmpty()) return false
        openFolderPath(_folderPath.value.dropLast(1))
        return true
    }

    fun back() {
        _pages.update { it.dropLast(1) }
    }

    fun coverUrl(coverArt: String?) = repository.coverUrl(coverArt, size = 300)

    fun dismissMessage() {
        _message.value = null
    }

    private fun push(loading: SubsonicPage, load: suspend () -> SubsonicPage) {
        _pages.update { it + loading }
        viewModelScope.launch {
            runCatching { load() }
                .onSuccess { loaded -> _pages.update { pages -> pages.map { if (it === loading) loaded else it } } }
                .onFailure {
                    _message.value = it.message ?: "Couldn't load from the server."
                    _pages.update { pages -> pages.filterNot { p -> p === loading } }
                }
        }
    }

    companion object {
        val ALBUM_SORTS = listOf(
            "newest" to "Recently added",
            "recent" to "Recently played",
            "frequent" to "Most played",
            "alphabeticalByName" to "A–Z",
            "random" to "Random",
        )
    }
}
