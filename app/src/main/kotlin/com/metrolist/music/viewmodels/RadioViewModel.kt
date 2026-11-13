/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.entities.Song
import com.metrolist.music.radio.RadioDirectory
import com.metrolist.music.radio.RadioStation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which stations the radio screen lists. */
sealed interface RadioList {
    data object Favourites : RadioList
    data object Popular : RadioList
    data class Genre(val tag: String, val label: String) : RadioList
    data class Search(val query: String) : RadioList
}

@HiltViewModel
class RadioViewModel
@Inject
constructor(
    private val directory: RadioDirectory,
) : ViewModel() {
    val favourites = directory.favourites
    val nowPlaying = directory.nowPlaying

    private val _list = MutableStateFlow<RadioList>(
        if (directory.favourites.value.isEmpty()) RadioList.Popular else RadioList.Favourites,
    )
    val list = _list.asStateFlow()

    private val _stations = MutableStateFlow<List<RadioStation>>(emptyList())
    val stations = _stations.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private var loadJob: Job? = null

    init {
        show(_list.value)
    }

    fun show(list: RadioList) {
        _list.value = list
        _error.value = null
        loadJob?.cancel()
        if (list is RadioList.Favourites) {
            _loading.value = false
            return
        }
        loadJob = viewModelScope.launch {
            if (list is RadioList.Search) delay(400)
            _loading.value = true
            runCatching {
                when (list) {
                    RadioList.Popular -> directory.popular()
                    is RadioList.Genre -> directory.byTag(list.tag, directory.homeCountry).ifEmpty { directory.byTag(list.tag) }
                    is RadioList.Search -> directory.search(list.query)
                    RadioList.Favourites -> emptyList()
                }
            }.onSuccess { _stations.value = it }
                .onFailure {
                    _stations.value = emptyList()
                    _error.value = "Couldn't reach the radio directory. Check your connection."
                }
            _loading.value = false
        }
    }

    fun search(query: String) = show(if (query.isBlank()) RadioList.Popular else RadioList.Search(query.trim()))

    /** Adds a pasted stream link as a favourite station; calls [onDone] with an error message or null. */
    fun addCustom(name: String, link: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching { directory.addCustom(name, link) }
                .onSuccess {
                    show(RadioList.Favourites)
                    onDone(null)
                }
                .onFailure { onDone(it.message ?: "Couldn't add that station.") }
        }
    }

    /** Plays a stream link found while browsing a station website, without saving it. */
    fun playLink(name: String, link: String, onReady: (Song) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val station = directory.addCustom(name, link, favourite = false)
                directory.prepare(listOf(station)).first()
            }.onSuccess(onReady).onFailure { onError(it.message ?: "Couldn't play that stream.") }
        }
    }

    fun isFavourite(station: RadioStation) = favourites.value.any { it.id == station.id }

    fun toggleFavourite(station: RadioStation) = directory.setFavourite(station, !isFavourite(station))

    /** Hearted stations go to the top of your stations (here and in Android Auto). */
    val starred = RadioDirectory.starredIds

    fun toggleStarred(station: RadioStation) = directory.setStarred(station, station.id !in starred.value)

    /** Queues the shown stations so next/previous flip between them, starting at [index]. */
    fun play(stations: List<RadioStation>, index: Int, onReady: (List<Song>, Int) -> Unit) {
        viewModelScope.launch {
            directory.resolveForPlay(stations[index])
            val songs = runCatching { directory.prepare(stations) }.getOrDefault(emptyList())
            val start = songs.indexOfFirst { it.id == stations[index].songId }
            if (start >= 0) onReady(songs, start)
        }
    }

    companion object {
        val GENRES = listOf(
            "news" to "News",
            "talk" to "Talk",
            "sport" to "Sport",
            "pop" to "Pop",
            "rock" to "Rock",
            "classic hits" to "Classic hits",
            "dance" to "Dance",
            "country" to "Country",
            "jazz" to "Jazz",
            "classical" to "Classical",
            "community radio" to "Community",
        )
    }
}
