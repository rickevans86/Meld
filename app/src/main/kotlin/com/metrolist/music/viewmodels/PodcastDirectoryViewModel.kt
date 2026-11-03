/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.entities.Song
import com.metrolist.music.podcasts.PodcastDirectory
import com.metrolist.music.podcasts.PodcastFeed
import com.metrolist.music.podcasts.PodcastShow
import com.metrolist.music.utils.SpotifyTokenManager
import com.metrolist.spotify.Spotify
import com.metrolist.spotify.models.SpotifyShow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the show page is showing: loading, the feed, or why there isn't one. */
sealed interface ShowPage {
    val title: String

    data class Loading(override val title: String) : ShowPage
    /** [songs] are the feed's episodes saved as songs, in the same order, so they get the full song menu. */
    data class Ready(val feed: PodcastFeed, val songs: List<Song>) : ShowPage {
        override val title get() = feed.show.title
    }
    data class Unavailable(override val title: String, val message: String) : ShowPage
}

@HiltViewModel
class PodcastDirectoryViewModel
@Inject
constructor(
    private val directory: PodcastDirectory,
) : ViewModel() {
    val followed = directory.followed

    private val _results = MutableStateFlow<List<PodcastShow>?>(null)
    val results = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()

    private val _spotifyShows = MutableStateFlow<List<SpotifyShow>>(emptyList())
    val spotifyShows = _spotifyShows.asStateFlow()

    private val _page = MutableStateFlow<ShowPage?>(null)
    val page = _page.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    private var searchJob: Job? = null
    private var openJob: Job? = null

    init {
        viewModelScope.launch {
            if (!SpotifyTokenManager.ensureAuthenticated()) return@launch
            val shows = mutableListOf<SpotifyShow>()
            var offset = 0
            while (offset < 300) {
                val page = Spotify.myShows(limit = 50, offset = offset).getOrNull() ?: break
                shows += page.items
                offset += 50
                if (page.items.size < 50) break
            }
            _spotifyShows.value = shows
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = null
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _searching.value = true
            runCatching { directory.search(query.trim()) }
                .onSuccess { _results.value = it }
                .onFailure { _message.value = "Podcast search isn't available right now. Check your connection." }
            _searching.value = false
        }
    }

    fun open(show: PodcastShow) = load(show.title) { show }

    fun openFeed(feedUrl: String) {
        val show = followed.value.firstOrNull { it.feedUrl == feedUrl } ?: PodcastShow(feedUrl, title = "")
        open(show)
    }

    fun openSpotify(show: SpotifyShow) = load(show.name) {
        directory.findFeed(show.name, show.publisher)
    }

    fun close() {
        openJob?.cancel()
        _page.value = null
    }

    fun isFollowed(show: PodcastShow) = directory.isFollowed(show.feedUrl)

    fun toggleFollow(show: PodcastShow) = directory.setFollowed(show, !directory.isFollowed(show.feedUrl))

    /** Turns the open show's episodes into songs and hands them to [onReady] with the start index. */
    fun play(episodeIndex: Int, onReady: (List<Song>, Int) -> Unit) {
        val songs = (_page.value as? ShowPage.Ready)?.songs ?: return
        if (songs.isNotEmpty()) onReady(songs, episodeIndex.coerceIn(0, songs.lastIndex))
    }

    fun dismissMessage() {
        _message.value = null
    }

    private fun load(title: String, resolve: suspend () -> PodcastShow?) {
        openJob?.cancel()
        _page.value = ShowPage.Loading(title)
        openJob = viewModelScope.launch {
            val show = runCatching { resolve() }.getOrNull()
            if (show == null) {
                _page.value = ShowPage.Unavailable(
                    title,
                    "This show doesn't have a public feed, so it can only be played in Spotify.",
                )
                return@launch
            }
            runCatching {
                val feed = directory.feed(show)
                ShowPage.Ready(feed, directory.prepare(feed))
            }
                .onSuccess { _page.value = it }
                .onFailure {
                    _page.value = ShowPage.Unavailable(title, "Couldn't load this show's episodes. Try again later.")
                }
        }
    }
}
