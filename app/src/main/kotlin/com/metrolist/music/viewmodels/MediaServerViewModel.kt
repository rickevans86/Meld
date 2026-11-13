/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.entities.Song
import com.metrolist.music.mediaserver.MediaServerKind
import com.metrolist.music.mediaserver.MediaServerRepository
import com.metrolist.music.mediaserver.MsAlbum
import com.metrolist.music.mediaserver.MsArtist
import com.metrolist.music.mediaserver.MsPlaylist
import com.metrolist.music.mediaserver.PlexPin
import com.metrolist.music.mediaserver.PlexServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MediaServerTab { ALBUMS, ARTISTS, PLAYLISTS, FAVOURITES }

sealed interface MediaServerPage {
    val title: String

    data class Album(val album: MsAlbum, val songs: List<Song>?) : MediaServerPage {
        override val title get() = album.title
    }

    data class Artist(val artist: MsArtist, val albums: List<MsAlbum>?) : MediaServerPage {
        override val title get() = artist.name
    }

    data class Playlist(val playlist: MsPlaylist, val songs: List<Song>?) : MediaServerPage {
        override val title get() = playlist.name
    }
}

/** Plex sign-in progress: waiting for the code to be entered, then picking a server. */
sealed interface PlexSignIn {
    data class WaitingForCode(val pin: PlexPin) : PlexSignIn
    data class PickServer(val servers: List<PlexServer>) : PlexSignIn
}

data class MediaServerSearch(val query: String, val songs: List<Song>, val albums: List<MsAlbum>, val artists: List<MsArtist>)

@HiltViewModel
class MediaServerViewModel
@Inject
constructor(
    private val repository: MediaServerRepository,
) : ViewModel() {
    val session = repository.session

    private val _signingIn = MutableStateFlow(false)
    val signingIn = _signingIn.asStateFlow()

    private val _plex = MutableStateFlow<PlexSignIn?>(null)
    val plex = _plex.asStateFlow()
    private var plexJob: Job? = null

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private val _tab = MutableStateFlow(MediaServerTab.ALBUMS)
    val tab = _tab.asStateFlow()

    private val _albums = MutableStateFlow<List<MsAlbum>?>(null)
    val albums = _albums.asStateFlow()
    private val _artists = MutableStateFlow<List<MsArtist>?>(null)
    val artists = _artists.asStateFlow()
    private val _playlists = MutableStateFlow<List<MsPlaylist>?>(null)
    val playlists = _playlists.asStateFlow()
    private val _favourites = MutableStateFlow<List<Song>?>(null)
    val favourites = _favourites.asStateFlow()

    private val _search = MutableStateFlow<MediaServerSearch?>(null)
    val search = _search.asStateFlow()

    private val _pages = MutableStateFlow<List<MediaServerPage>>(emptyList())
    val pages = _pages.asStateFlow()

    val hasFavourites get() = repository.hasFavourites

    init {
        if (session.value != null) loadTab()
    }

    fun dismissMessage() {
        _message.value = null
    }

    fun signInJellyfin(server: String, username: String, password: String) = work {
        repository.signInJellyfin(server, username, password)
        resetAndLoad()
    }

    /** Starts Plex sign-in: shows a code for plex.tv/link and waits (up to 10 minutes) for it to be entered. */
    fun startPlex() {
        plexJob?.cancel()
        plexJob = viewModelScope.launch {
            _signingIn.value = true
            runCatching {
                val pin = repository.plexStartPin()
                _plex.value = PlexSignIn.WaitingForCode(pin)
                _signingIn.value = false
                var token: String? = null
                repeat(300) {
                    if (token == null) {
                        delay(2_000)
                        token = repository.plexCheckPin(pin)
                    }
                }
                val accountToken = token ?: throw com.metrolist.music.mediaserver.MediaServerException("The code expired. Start again.")
                val servers = repository.plexServers(accountToken)
                when {
                    servers.isEmpty() -> throw com.metrolist.music.mediaserver.MediaServerException("No Plex servers on this account.")
                    servers.size == 1 -> usePlexServer(servers.first())
                    else -> _plex.value = PlexSignIn.PickServer(servers)
                }
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _plex.value = null
                _message.value = it.message ?: "Couldn't sign in to Plex."
            }
            _signingIn.value = false
        }
    }

    fun cancelPlex() {
        plexJob?.cancel()
        _plex.value = null
        _signingIn.value = false
    }

    fun usePlexServer(server: PlexServer) = work {
        repository.plexUseServer(server)
        _plex.value = null
        resetAndLoad()
    }

    fun signOut() {
        repository.signOut()
        _albums.value = null
        _artists.value = null
        _playlists.value = null
        _favourites.value = null
        _pages.value = emptyList()
        _search.value = null
    }

    fun selectTab(tab: MediaServerTab) {
        _tab.value = tab
        loadTab()
    }

    fun refresh() {
        when (_tab.value) {
            MediaServerTab.ALBUMS -> _albums.value = null
            MediaServerTab.ARTISTS -> _artists.value = null
            MediaServerTab.PLAYLISTS -> _playlists.value = null
            MediaServerTab.FAVOURITES -> _favourites.value = null
        }
        loadTab()
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _search.value = null
            return
        }
        work {
            val results = repository.search(query.trim())
            _search.value = MediaServerSearch(query, repository.prepare(results.songs), results.albums, results.artists)
        }
    }

    fun clearSearch() {
        _search.value = null
    }

    fun openAlbum(album: MsAlbum) {
        push(MediaServerPage.Album(album, null))
        work { replaceTop(MediaServerPage.Album(album, repository.prepare(repository.albumSongs(album)))) }
    }

    fun openArtist(artist: MsArtist) {
        push(MediaServerPage.Artist(artist, null))
        work { replaceTop(MediaServerPage.Artist(artist, repository.artistAlbums(artist.id))) }
    }

    fun openPlaylist(playlist: MsPlaylist) {
        push(MediaServerPage.Playlist(playlist, null))
        work { replaceTop(MediaServerPage.Playlist(playlist, repository.prepare(repository.playlistSongs(playlist)))) }
    }

    fun back() {
        _pages.update { it.dropLast(1) }
    }

    private fun push(page: MediaServerPage) {
        _pages.update { it + page }
    }

    private fun replaceTop(page: MediaServerPage) {
        _pages.update { pages -> if (pages.isEmpty()) pages else pages.dropLast(1) + page }
    }

    private fun resetAndLoad() {
        _albums.value = null
        _artists.value = null
        _playlists.value = null
        _favourites.value = null
        _pages.value = emptyList()
        _tab.value = MediaServerTab.ALBUMS
        loadTab()
    }

    private fun loadTab() = work {
        val tab = _tab.value
        try {
            when (tab) {
                MediaServerTab.ALBUMS -> if (_albums.value == null) _albums.value = repository.albums()
                MediaServerTab.ARTISTS -> if (_artists.value == null) _artists.value = repository.artists()
                MediaServerTab.PLAYLISTS -> if (_playlists.value == null) _playlists.value = repository.playlists()
                MediaServerTab.FAVOURITES -> if (_favourites.value == null) _favourites.value = repository.prepare(repository.favourites())
            }
        } catch (e: Exception) {
            // Show an empty list (with the error message) rather than spinning forever
            when (tab) {
                MediaServerTab.ALBUMS -> if (_albums.value == null) _albums.value = emptyList()
                MediaServerTab.ARTISTS -> if (_artists.value == null) _artists.value = emptyList()
                MediaServerTab.PLAYLISTS -> if (_playlists.value == null) _playlists.value = emptyList()
                MediaServerTab.FAVOURITES -> if (_favourites.value == null) _favourites.value = emptyList()
            }
            throw e
        }
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _signingIn.value = true
            runCatching { block() }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                _message.value = it.message ?: "Something went wrong talking to the server."
            }
            _signingIn.value = false
        }
    }

    val kind: MediaServerKind? get() = session.value?.kind
}
