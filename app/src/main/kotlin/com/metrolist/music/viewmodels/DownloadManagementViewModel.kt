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
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.playback.DownloadUtil
import com.metrolist.music.playback.PlaylistAutoDownload
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Wavelength: backs the playlist sections of the Manage download queue screen.
 *
 * "Downloaded" and "downloading" are membership questions — is every song on the device, or is the
 * whole playlist enqueued — so local playlists (including Radio/Recognised, which are real playlists)
 * and Liked are classified from their actual song lists. A song shared across playlists therefore
 * counts toward each playlist that is genuinely fully on-device or fully enqueued, and a playlist that
 * only shares a few queued songs with another download is not shown as downloading. Spotify playlists,
 * whose track lists aren't stored offline, are grouped from the per-download source tag instead.
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
        val fraction: Float get() = if (total > 0) completed.toFloat() / total else 0f
        val anyPaused: Boolean get() = paused > 0 && active == 0

        /** Every song is on the device. */
        val isDownloaded: Boolean get() = total > 0 && completed == total

        /** The whole playlist is enqueued (nothing left un-queued) and something is still in flight. */
        val isDownloading: Boolean
            get() = total > 0 && completed + active + paused == total && completed < total && (active > 0 || paused > 0)
    }

    data class AutoDownloadPlaylist(
        val id: String,
        val name: String,
        val thumbnailUrl: String?,
        val openRoute: String?,
    )

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

    /** Resolve a source/auto-download id to a display name, thumbnail and open route. */
    private suspend fun resolveSource(id: String, storedName: String?): Triple<String, String?, String?> {
        fun clean(n: String?): String? =
            n?.removePrefix("YouTube - ")?.removePrefix("Spotify - ")?.trim()?.ifBlank { null }
        return when {
            id == PlaylistAutoDownload.LIKED_ID ->
                Triple(context.getString(R.string.liked), null, "auto_playlist/liked")

            id.startsWith(PlaylistAutoDownload.SPOTIFY_PREFIX) ->
                Triple(
                    clean(storedName) ?: context.getString(R.string.spotify_playlist_label),
                    null,
                    "spotify_playlist/${id.removePrefix(PlaylistAutoDownload.SPOTIFY_PREFIX)}",
                )

            id.startsWith(PlaylistAutoDownload.YOUTUBE_PREFIX) -> {
                val remote = id.removePrefix(PlaylistAutoDownload.YOUTUBE_PREFIX).removePrefix("VL")
                val playlist = database.playlistByBrowseId(remote).first()
                    ?: database.playlistByBrowseId("VL$remote").first()
                Triple(
                    playlist?.playlist?.name ?: clean(storedName) ?: id,
                    playlist?.thumbnails?.firstOrNull(),
                    "online_playlist/$remote",
                )
            }

            else -> {
                val playlist = database.playlist(id).first()
                Triple(
                    playlist?.playlist?.name ?: clean(storedName) ?: id,
                    playlist?.thumbnails?.firstOrNull(),
                    playlist?.playlist?.browseId?.let { "online_playlist/$it" } ?: "local_playlist/$id",
                )
            }
        }
    }

    private fun openRouteFor(playlist: Playlist): String =
        playlist.playlist.browseId?.let { "online_playlist/$it" } ?: "local_playlist/${playlist.id}"

    private val autoIdsFlow =
        context.dataStore.data
            .map { it[PlaylistAutoDownload.AutoDownloadPlaylistsKey].orEmpty() }
            .distinctUntilChanged()

    private data class Inputs(
        val playlists: List<Playlist>,
        val liked: List<Song>,
        val downloads: Map<String, Download>,
        val autoIds: Set<String>,
    )

    private val computed =
        combine(
            database.playlists(PlaylistSortType.NAME, false),
            database.likedSongs(SongSortType.CREATE_DATE, true),
            downloadUtil.downloads,
            autoIdsFlow,
        ) { playlists, liked, downloads, autoIds -> Inputs(playlists, liked, downloads, autoIds) }
            .mapLatest { input ->
                withContext(Dispatchers.IO) {
                    val items = mutableListOf<PlaylistDownload>()

                    // Local + pinned YouTube playlists (includes Radio/Recognised, which are real playlists).
                    for (playlist in input.playlists) {
                        val ids = database.playlistSongIds(playlist.id)
                        aggregate(
                            key = "pl:${playlist.id}",
                            name = playlist.playlist.name,
                            thumbnailUrl = playlist.thumbnails.firstOrNull(),
                            openRoute = openRouteFor(playlist),
                            ids = ids,
                            downloads = input.downloads,
                        )?.let { items += it }
                    }

                    // Liked songs.
                    aggregate(
                        key = "liked",
                        name = context.getString(R.string.liked),
                        thumbnailUrl = null,
                        openRoute = "auto_playlist/liked",
                        ids = input.liked.map { it.id },
                        downloads = input.downloads,
                    )?.let { items += it }

                    // Spotify playlists, grouped from the per-download source tags (no offline track list).
                    val prefs = context.dataStore.data.first()
                    val groups = LinkedHashMap<String, MutableList<String>>()
                    val sourceNames = HashMap<String, String>()
                    for ((prefKey, value) in prefs.asMap()) {
                        val name = prefKey.name
                        when {
                            name.startsWith("downloadSource:") -> {
                                val source = value as? String ?: continue
                                if (source.startsWith(PlaylistAutoDownload.SPOTIFY_PREFIX)) {
                                    groups.getOrPut(source) { mutableListOf() }
                                        .add(name.removePrefix("downloadSource:"))
                                }
                            }
                            name.startsWith("downloadSourceName:") ->
                                (value as? String)?.let { sourceNames[name.removePrefix("downloadSourceName:")] = it }
                        }
                    }
                    for ((source, ids) in groups) {
                        val (name, thumb, route) = resolveSource(source, sourceNames[source])
                        aggregate(source, name, thumb, route, ids.distinct(), input.downloads)?.let { items += it }
                    }

                    val downloading = items.filter { it.isDownloading }.sortedBy { it.name.lowercase() }
                    val downloaded = items.filter { it.isDownloaded }.sortedBy { it.name.lowercase() }

                    val auto = input.autoIds.map { id ->
                        val (name, thumb, route) = resolveSource(id, sourceNames[id])
                        AutoDownloadPlaylist(id, name, thumb, route)
                    }.sortedBy { it.name.lowercase() }

                    Triple(downloading, downloaded, auto)
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                Triple(emptyList<PlaylistDownload>(), emptyList<PlaylistDownload>(), emptyList<AutoDownloadPlaylist>()),
            )

    val downloadingPlaylists =
        computed.map { it.first }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadedPlaylists =
        computed.map { it.second }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val autoDownloadPlaylists =
        computed.map { it.third }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Turn off auto-download for a playlist. */
    fun disableAutoDownload(id: String) =
        viewModelScope.launch { PlaylistAutoDownload.setEnabled(context, id, false) }
}
