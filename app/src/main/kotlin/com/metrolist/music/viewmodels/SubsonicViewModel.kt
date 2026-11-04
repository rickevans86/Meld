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
import com.metrolist.music.subsonic.SubsonicFolderEntry
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
    /** A server folder: its sub-folders and songs (null while loading). */
    data class Folder(
        val entry: SubsonicFolderEntry,
        val folders: List<SubsonicFolderEntry>? = null,
        val songs: List<Song>? = null,
    ) : SubsonicPage {
        override val title get() = entry.name
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

    private val _rootFolders = MutableStateFlow<List<SubsonicFolderEntry>?>(null)
    val rootFolders = _rootFolders.asStateFlow()

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
        _rootFolders.value = null
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
                _rootFolders.value = null
                _treeContents.value = emptyMap()
                _expanded.value = emptySet()
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
                    SubsonicTab.FOLDERS -> if (_rootFolders.value == null) _rootFolders.value = repository.rootFolders()
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

    fun openFolder(entry: SubsonicFolderEntry) = push(SubsonicPage.Folder(entry)) {
        val (_, entries) = repository.folder(entry.id)
        SubsonicPage.Folder(
            entry,
            folders = entries.filter { it.isFolder },
            songs = repository.prepare(entries.mapNotNull { it.song }),
        )
    }

    // ── Folder tree: folders expand in place; their contents load the first time they open ──
    private val _expanded = MutableStateFlow<Set<String>>(emptySet())
    val expanded = _expanded.asStateFlow()

    /** Folder id -> its sub-folders and songs, once loaded. */
    private val _treeContents = MutableStateFlow<Map<String, Pair<List<SubsonicFolderEntry>, List<Song>>>>(emptyMap())
    val treeContents = _treeContents.asStateFlow()

    fun toggleFolder(entry: SubsonicFolderEntry) {
        if (entry.id in _expanded.value) {
            _expanded.update { it - entry.id }
            return
        }
        _expanded.update { it + entry.id }
        if (entry.id in _treeContents.value) return
        viewModelScope.launch {
            runCatching {
                val (_, entries) = repository.folder(entry.id)
                entries.filter { it.isFolder } to repository.prepare(entries.mapNotNull { it.song })
            }.onSuccess { contents -> _treeContents.update { it + (entry.id to contents) } }
                .onFailure {
                    _expanded.update { set -> set - entry.id }
                    _message.value = it.message ?: "Couldn't open that folder."
                }
        }
    }

    fun collapseAll() {
        _expanded.value = emptySet()
    }

    /** Songs sitting at the top level of the library, shown as their own list. */
    fun openLooseSongs(title: String, songs: List<com.metrolist.music.subsonic.SubsonicSong>) =
        push(SubsonicPage.Folder(SubsonicFolderEntry("root", title, null, null))) {
            SubsonicPage.Folder(SubsonicFolderEntry("root", title, null, null), emptyList(), repository.prepare(songs))
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
