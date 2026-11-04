/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.podcasts.DiscoverRow
import com.metrolist.music.podcasts.DiscoverShow
import com.metrolist.music.podcasts.PodcastDirectory
import com.metrolist.music.podcasts.PodcastDiscover
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastDiscoverViewModel
@Inject
constructor(
    private val discover: PodcastDiscover,
    private val directory: PodcastDirectory,
) : ViewModel() {
    val followed = directory.followed
    val countryName get() = discover.countryName

    /** Null while loading; empty if the charts couldn't be reached. */
    private val _rows = MutableStateFlow<List<DiscoverRow>?>(null)
    val rows = _rows.asStateFlow()

    /** Chart shows being looked up (spinner on their + button). */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        load()
    }

    fun load(refresh: Boolean = false) {
        if (refresh) _rows.value = null
        viewModelScope.launch { _rows.value = discover.rows(refresh) }
    }

    /** Follows a chart show (looking up its feed first). */
    fun follow(show: DiscoverShow) = withFeed(show) { directory.setFollowed(it, true) }

    /** Opens a chart show's episode page with [onOpen] and its feed address. */
    fun open(show: DiscoverShow, onOpen: (String) -> Unit) = withFeed(show) { onOpen(it.feedUrl) }

    fun dismissMessage() {
        _message.value = null
    }

    private fun withFeed(show: DiscoverShow, then: (com.metrolist.music.podcasts.PodcastShow) -> Unit) {
        if (show.appleId in _busy.value) return
        _busy.update { it + show.appleId }
        viewModelScope.launch {
            val found = discover.resolve(show)
            _busy.update { it - show.appleId }
            if (found == null) _message.value = "${show.name} doesn't have a public feed, so it can't be played here." else then(found)
        }
    }
}
