/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.playback.DownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Wavelength: backs the playlist sections of the Manage download queue screen. Combines the saved
 * playlists (local + pinned YouTube in the playlist table) with the live download states to list
 * playlists that are currently downloading and playlists that are fully downloaded, and exposes
 * whole-playlist actions (pause/resume/cancel, remove, re-download) that fan out to member songs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadManagementViewModel @Inject constructor(
    val database: MusicDatabase,
    private val downloadUtil: DownloadUtil,
) : ViewModel() {

    data class PlaylistDownload(
        val id: String,
        val name: String,
        val browseId: String?,
        val thumbnailUrl: String?,
        val total: Int,
        val completed: Int,
        val active: Int,
        val paused: Int,
    ) {
        /** Progress as finished songs over total, 0f..1f. */
        val fraction: Float get() = if (total > 0) completed.toFloat() / total else 0f
        val anyPaused: Boolean get() = paused > 0 && active == 0
    }

    private val computed =
        combine(
            database.playlists(PlaylistSortType.NAME, false),
            downloadUtil.downloads,
        ) { playlists, downloads -> playlists to downloads }
            .mapLatest { (playlists, downloads) ->
                withContext(Dispatchers.IO) {
                    val downloading = mutableListOf<PlaylistDownload>()
                    val downloaded = mutableListOf<PlaylistDownload>()
                    for (playlist in playlists) {
                        val ids = database.playlistSongIds(playlist.id)
                        if (ids.isEmpty()) continue
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
                        val item = PlaylistDownload(
                            id = playlist.id,
                            name = playlist.playlist.name,
                            browseId = playlist.playlist.browseId,
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            total = ids.size,
                            completed = completed,
                            active = active,
                            paused = paused,
                        )
                        when {
                            active > 0 || paused > 0 -> downloading += item
                            completed == ids.size -> downloaded += item
                        }
                    }
                    downloading.sortedBy { it.name.lowercase() } to downloaded.sortedBy { it.name.lowercase() }
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

    private fun forEachSong(playlistId: String, action: (String) -> Unit) {
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) { database.playlistSongIds(playlistId) }
            ids.forEach(action)
        }
    }

    fun pausePlaylist(playlistId: String) = forEachSong(playlistId, downloadUtil::pauseDownload)

    fun resumePlaylist(playlistId: String) = forEachSong(playlistId, downloadUtil::resumeDownload)

    fun cancelPlaylist(playlistId: String) = forEachSong(playlistId, downloadUtil::cancelDownload)

    /** Delete every downloaded file for the playlist (frees space; keeps the playlist). */
    fun removePlaylistDownload(playlistId: String) = forEachSong(playlistId, downloadUtil::cancelDownload)

    /** Re-queue every song in the playlist to download again. */
    fun redownloadPlaylist(playlistId: String) = forEachSong(playlistId, downloadUtil::download)
}
