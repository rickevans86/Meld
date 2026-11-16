/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.completed
import com.metrolist.music.constants.podcastPlaylistIds
import com.metrolist.music.constants.youTubePodcastPlaylists
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Explicit podcast classification also applies to ordinary YouTube videos inside selected playlists. */
@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastHomeFilter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val fetched = ConcurrentHashMap<String, Set<String>>()

    private val refreshes = MutableStateFlow(0)

    fun refresh() {
        fetched.clear()
        refreshes.update { it + 1 }
    }

    fun excludedIds(): Flow<Set<String>> = combine(
        context.dataStore.data.map { it.podcastPlaylistIds() }.distinctUntilChanged(), refreshes,
    ) { selected, _ -> selected }.flatMapLatest { selected ->
            val localIds = selected.filterNot { it.startsWith("yt:") }
            val onlineIds = context.dataStore.data.first().youTubePodcastPlaylists().map { it.id } +
                localIds.mapNotNull { database.playlist(it).first()?.playlist?.browseId }
            val remote = flow {
                val ids = onlineIds.map { it.removePrefix("VL") }.distinct()
                val prefs = context.dataStore.data.first()
                val cached = ids.flatMap { prefs[cacheKey(it)].orEmpty() }.toSet()
                val playlistAliases = ids.flatMap { listOf(it, "VL$it") }.toSet()
                emit(playlistAliases + cached)
                val fresh = mutableSetOf<String>()
                for (id in ids) {
                    val songs = fetched[id] ?: try {
                        withTimeoutOrNull(15_000) { YouTube.playlist(id).completed().getOrNull()?.songs?.map { it.id }?.toSet() }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { null }
                    if (songs != null) {
                        fetched[id] = songs
                        context.dataStore.edit { it[cacheKey(id)] = songs }
                        fresh += songs
                    } else fresh += prefs[cacheKey(id)].orEmpty()
                }
                emit(playlistAliases + fresh)
            }
            combine(database.podcastHomeSongIds(localIds), database.subscribedPodcasts(), remote) { songs, shows, remoteIds ->
                songs.toSet() + shows.flatMap { listOf(it.id, it.id.removePrefix("MPSP")) } + localIds + remoteIds
            }
        }.flowOn(Dispatchers.IO)

    private fun cacheKey(id: String) = stringSetPreferencesKey("podcastHomePlaylist:$id")
}
