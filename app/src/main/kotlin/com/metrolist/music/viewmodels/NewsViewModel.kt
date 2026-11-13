/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.entities.Song
import com.metrolist.music.podcasts.NewsBriefing
import com.metrolist.music.podcasts.NewsSourceChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import com.metrolist.music.podcasts.Bulletin
import com.metrolist.music.podcasts.NewsSort
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewsViewModel
@Inject
constructor(
    private val briefing: NewsBriefing,
) : ViewModel() {
    val choices = briefing.choices.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    /** Each enabled source's newest bulletin by source key: missing while loading, null if there isn't one. */
    private val _bulletins = MutableStateFlow<Map<String, Bulletin?>>(emptyMap())
    val bulletins = _bulletins.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    val sort = briefing.sortSetting.stateIn(viewModelScope, SharingStarted.Eagerly, NewsSort.YOUR_ORDER to false)

    /** Enabled sources in the chosen order, using fresh bulletin times when sorting by latest. */
    val sortedEnabled = combine(choices, sort, _bulletins) { all, (order, reversed), bulletins ->
        briefing.sortSources(
            all.filter { it.enabled }.map { it.source },
            order,
            reversed,
            bulletins.mapValues { it.value?.publishedAt },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setSort(order: NewsSort) {
        viewModelScope.launch { briefing.setSort(order) }
    }

    fun setSortReversed(reversed: Boolean) {
        viewModelScope.launch { briefing.setSortReversed(reversed) }
    }

    private var refreshJob: Job? = null

    /** Loads the newest bulletin from every enabled source, showing each as soon as it arrives. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _refreshing.value = true
            _bulletins.value = emptyMap()
            val sources = briefing.choices.first().filter { it.enabled }.map { it.source }
            coroutineScope {
                sources.forEach { source ->
                    launch {
                        val bulletin = briefing.latestBulletin(source)
                        _bulletins.update { it + (source.key to bulletin) }
                    }
                }
            }
            _refreshing.value = false
        }
    }

    /** Plays one bulletin on its own, from the start. */
    fun playOne(bulletin: Bulletin, onReady: (Song) -> Unit) {
        viewModelScope.launch {
            briefing.restart(bulletin.song.id)
            onReady(bulletin.song)
        }
    }

    /** Fetches the latest bulletins and calls [onReady] with them, or with an empty list if none came back. */
    fun play(onReady: (List<Song>) -> Unit) {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            // Plays in the order chosen with the sort button
            val sources = sortedEnabled.value.ifEmpty { briefing.sortedEnabledSources() }
            val loaded = _bulletins.value
            val songs = if (!_refreshing.value && sources.isNotEmpty() && sources.all { it.key in loaded }) {
                // Already fetched for the News tab: play those straight away
                sources.mapNotNull { loaded[it.key]?.song }.onEach { briefing.restart(it.id) }
            } else {
                runCatching { briefing.latestBulletins(sources) }.getOrDefault(emptyList())
            }
            _loading.value = false
            onReady(songs)
        }
    }

    /** Key of the source being checked for a public feed, so the screen can show a spinner. */
    private val _checking = MutableStateFlow<String?>(null)
    val checking = _checking.asStateFlow()

    private val _unavailable = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Names of sources that turned out to have no public feed. */
    val unavailable = _unavailable.asSharedFlow()

    /** Last known status of each source, by key. */
    val statuses = briefing.statuses

    /** (checked, total) while the sources are being checked, otherwise null. */
    private val _checkProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val checkProgress = _checkProgress.asStateFlow()
    private var checkJob: Job? = null

    /** Checks every source in the background so the screen can mark the ones that can't be played. */
    fun checkAllSources(force: Boolean = false) {
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            val sources = briefing.choices.first().map { it.source }
            briefing.checkSources(sources, force) { done, total -> _checkProgress.value = done to total }
            _checkProgress.value = null
        }
    }

    fun toggle(choice: NewsSourceChoice) {
        val key = choice.source.key
        if (choice.enabled) {
            save(enabledKeys() - key)
            return
        }
        if (statuses.value[key] == com.metrolist.music.podcasts.SourceStatus.NO_FEED) {
            _unavailable.tryEmit(choice.source.name)
            return
        }
        if (!key.startsWith("search:")) {
            save(enabledKeys() + key)
            return
        }
        if (_checking.value != null) return
        viewModelScope.launch {
            _checking.value = key
            // Same check as the background one, so a failed source is struck through straight away
            val status = briefing.checkSource(choice.source)
            val ok = status != null && status != com.metrolist.music.podcasts.SourceStatus.NO_FEED
            _checking.value = null
            if (ok) briefing.setEnabled(enabledKeys() + key) else _unavailable.tryEmit(choice.source.name)
        }
    }

    fun move(choice: NewsSourceChoice, offset: Int) {
        val keys = enabledKeys().toMutableList()
        val from = keys.indexOf(choice.source.key)
        val to = from + offset
        if (from < 0 || to !in keys.indices) return
        keys.add(to, keys.removeAt(from))
        save(keys)
    }

    private fun enabledKeys() = choices.value.filter { it.enabled }.map { it.source.key }

    private fun save(keys: List<String>) {
        viewModelScope.launch { briefing.setEnabled(keys) }
    }
}
