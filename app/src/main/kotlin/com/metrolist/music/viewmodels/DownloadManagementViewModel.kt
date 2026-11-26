/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.R
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.SongSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Wavelength: backs the playlist sections of the Manage download queue screen. Combines the saved
 * playlists (local + pinned YouTube in the playlist table), Liked songs, and Spotify playlists
 * (grouped from the per-download source tag, since Spotify track lists aren't stored locally) with
 * the live download states, to list playlists that are currently downloading and playlists that are
 * fully downloaded, and exposes whole-playlist actions that fan out to the member songs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadManagementViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {

    data class PlaylistDownload(
        val key: String,
        val name: String,
        val thumbnailUrl: String?,
        val openRoute: String?,
        val memberIds: List<String>,
        val total: Int,
        val completed: Int,
        val active: Int,
        val paused: Int,
    ) {
        /** Progress as finished songs over total, 0f..1f. */
        val fraction: Float get() = if (total > 0) completed.toFloat() / total else 0f
        val anyPaused: Boolean get() = paused > 0 && active == 0
    }

    private fun aggregate(
        key: String,
        name: String,
        thumbnailUrl: String?,
        openRoute: String?,
        ids: List<String>,
        downloads: Map<String, Download>,
    ): PlaylistDownload? {
        if (ids.isEmpty()) return null
        var completed = 0
        var active = 0
        var paused = 0
        for (id in ids) {
            val download = downloads[id] ?: continue
            when {
                download.state == Download.STATE_COMPLETED -> completed++
                download.stopReason != Download.STOP_REASON_NONE ||
                    download.state == Download.STATE_STOPPED -> paused++
                download.state == Download.STATE_DOWNLOADING ||
                    download.state == Download.STATE_QUEUED ||
                    download.state == Download.STATE_RESTARTING -> active++
            }
        }
        return PlaylistDownload(key, name, thumbnailUrl, openRoute, ids, ids.size, completed, active, paused)
    }

    private val computed =
        combine(
            database.playlists(PlaylistSortType.NAME, false),
            database.likedSongs(SongSortType.CREATE_DATE, true),
            downloadUtil.downloads,
        ) { playlists, liked, downloads -> Triple(playlists, liked, downloads) }
            .mapLatest { (playlists, liked, downloads) ->
                withContext(Dispatchers.IO) {
                    val items = mutableListOf<PlaylistDownload>()

                    // Local + pinned YouTube playlists (reliable membership from the DB).
                    for (playlist in playlists) {
                        val ids = database.playlistSongIds(playlist.id)
                        aggregate(
                            key = "pl:${playlist.id}",
                            name = playlist.playlist.name,
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            openRoute = playlist.playlist.browseId?.let { "online_playlist/$it" }
                                ?: "local_playlist/${playlist.id}",
                            ids = ids,
                            downloads = downloads,
                        )?.let { items += it }
                    }

                    // Liked songs.
                    aggregate(
                        key = "liked",
                        name = context.getString(R.string.liked),
                        thumbnailUrl = null,
                        openRoute = "auto_playlist/liked",
                        ids = liked.map { it.id },
                        downloads = downloads,
                    )?.let { items += it }

                    // Spotify playlists, grouped from the per-download source tags.
                    val prefs = context.dataStore.data.first()
                    val groups = LinkedHashMap<String, MutableList<String>>()
                    val names = HashMap<String, String>()
                    for ((prefKey, value) in prefs.asMap()) {
                        val name = prefKey.name
                        when {
                            name.startsWith("downloadSource:") -> {
                                val source = value as? String ?: continue
                                if (source.startsWith("spotify:")) {
                                    groups.getOrPut(source) { mutableListOf() }
                                        .add(name.removePrefix("downloadSource:"))
                                }
                            }
                            name.startsWith("downloadSourceName:") ->
                                (value as? String)?.let { names[name.removePrefix("downloadSourceName:")] = it }
                        }
                    }
                    for ((source, ids) in groups) {
                        val display = names[source]?.removePrefix("Spotify - ")?.ifBlank { null }
                            ?: context.getString(R.string.spotify_playlist_label)
                        aggregate(
                            key = source,
                            name = display,
                            thumbnailUrl = null,
                            openRoute = "spotify_playlist/${source.removePrefix("spotify:")}",
                            ids = ids.distinct(),
                            downloads = downloads,
                        )?.let { items += it }
                    }

                    val downloading = items
                        .filter { it.active > 0 || it.paused > 0 }
                        .sortedBy { it.name.lowercase() }
                    val downloaded = items
                        .filter { it.active == 0 && it.paused == 0 && it.completed == it.total && it.total > 0 }
                        .sortedBy { it.name.lowercase() }
                    downloading to downloaded
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList<PlaylistDownload>() to emptyList(),
            )

    val downloadingPlaylists =
        computed.map { it.first }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedPlaylists =
        computed.map { it.second }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pausePlaylist(item: PlaylistDownload) =
        viewModelScope.launch { item.memberIds.forEach(downloadUtil::pauseDownload) }

    fun resumePlaylist(item: PlaylistDownload) =
        viewModelScope.launch { item.memberIds.forEach(downloadUtil::resumeDownload) }

    fun cancelPlaylist(item: PlaylistDownload) =
        viewModelScope.launch { item.memberIds.forEach(downloadUtil::cancelDownload) }

    /** Delete every downloaded file for the playlist (frees space; keeps the playlist). */
    fun removePlaylistDownload(item: PlaylistDownload) =
        viewModelScope.launch { item.memberIds.forEach(downloadUtil::cancelDownload) }

    /** Re-queue every song in the playlist to download again. */
    fun redownloadPlaylist(item: PlaylistDownload) =
        viewModelScope.launch { item.memberIds.forEach(downloadUtil::download) }
}
