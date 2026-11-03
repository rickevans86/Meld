/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.podcasts.PodcastDirectory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * New episodes from everywhere: the latest from podcasts followed in Wavelength (from their
 * feeds, newest first) plus YouTube Music's own "New episodes" list from subscriptions.
 */
@HiltViewModel
class NewEpisodesViewModel
@Inject
constructor(
    private val directory: PodcastDirectory,
) : ViewModel() {
    /** Followed-podcast episodes with publish time; null while loading. */
    private val _followed = MutableStateFlow<List<Pair<Song, Long?>>?>(null)
    val followed = _followed.asStateFlow()

    /** YouTube subscription episodes; null while loading. */
    private val _youtube = MutableStateFlow<List<SongItem>?>(null)
    val youtube = _youtube.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            val yt = async { YouTube.newEpisodes().getOrNull().orEmpty() }
            val rss = async { runCatching { directory.recentFromFollowed() }.getOrDefault(emptyList()) }
            _followed.value = rss.await()
            _youtube.value = yt.await()
            _refreshing.value = false
        }
    }
}
