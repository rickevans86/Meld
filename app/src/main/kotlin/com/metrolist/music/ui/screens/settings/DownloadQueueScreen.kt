/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.constants.DownloadQueuePausedKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.DownloadManagementViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wavelength: manage downloads. Shows playlists that are currently downloading and playlists that are
 * fully downloaded (with whole-playlist actions), plus the queue of individual songs with per-item
 * pause/resume/cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    navController: NavController,
    viewModel: DownloadManagementViewModel = hiltViewModel(),
) {
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val downloadsMap by downloadUtil.downloads.collectAsState()
    val (queuePaused, onQueuePausedChange) = rememberPreference(DownloadQueuePausedKey, defaultValue = false)
    val downloadingPlaylists by viewModel.downloadingPlaylists.collectAsState()
    val downloadedPlaylists by viewModel.downloadedPlaylists.collectAsState()
    val autoDownloadPlaylists by viewModel.autoDownloadPlaylists.collectAsState()

    // Only the active queue: downloading, queued, restarting and paused/stopped items.
    val queue = remember(downloadsMap) {
        downloadsMap.values
            .filter {
                it.state == Download.STATE_DOWNLOADING ||
                    it.state == Download.STATE_QUEUED ||
                    it.state == Download.STATE_RESTARTING ||
                    it.state == Download.STATE_STOPPED
            }
            .sortedWith(compareBy({ queueOrder(it) }, { it.request.id }))
    }

    val titles = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(queue.map { it.request.id }) {
        queue.forEach { download ->
            val id = download.request.id
            if (!titles.containsKey(id)) {
                val song = withContext(Dispatchers.IO) { database.getSongById(id) }
                titles[id] = song?.song?.title ?: id
            }
        }
    }

    var removeTarget by remember { mutableStateOf<DownloadManagementViewModel.PlaylistDownload?>(null) }
    removeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text(stringResource(R.string.remove_download)) },
            text = { Text(stringResource(R.string.remove_playlist_download_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePlaylistDownload(target)
                    removeTarget = null
                }) { Text(stringResource(R.string.remove_download)) }
            },
            dismissButton = {
                TextButton(onClick = { removeTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    fun openPlaylist(route: String?) {
        if (route != null) navController.navigate(route)
    }

    val isEmpty = queue.isEmpty() && downloadingPlaylists.isEmpty() &&
        downloadedPlaylists.isEmpty() && autoDownloadPlaylists.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_download_queue)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(painter = painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { onQueuePausedChange(!queuePaused) }) {
                            Icon(
                                painter = painterResource(if (queuePaused) R.drawable.play else R.drawable.pause),
                                contentDescription = stringResource(
                                    if (queuePaused) R.string.resume_all_downloads else R.string.pause_all_downloads
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (isEmpty) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.download_queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = paddingValues, modifier = Modifier.fillMaxSize()) {
                if (downloadingPlaylists.isNotEmpty()) {
                    item(key = "header_downloading_playlists") {
                        SectionHeader(stringResource(R.string.downloading_playlists))
                    }
                    items(items = downloadingPlaylists, key = { "dlp_${it.key}" }) { playlist ->
                        DownloadingPlaylistRow(
                            playlist = playlist,
                            onPauseResume = {
                                if (playlist.anyPaused) viewModel.resumePlaylist(playlist)
                                else viewModel.pausePlaylist(playlist)
                            },
                            onCancel = { viewModel.cancelPlaylist(playlist) },
                            onOpen = { openPlaylist(playlist.openRoute) },
                        )
                    }
                }

                if (downloadedPlaylists.isNotEmpty()) {
                    item(key = "header_downloaded_playlists") {
                        SectionHeader(stringResource(R.string.downloaded_playlists))
                    }
                    items(items = downloadedPlaylists, key = { "ddp_${it.key}" }) { playlist ->
                        DownloadedPlaylistRow(
                            playlist = playlist,
                            onRedownload = { viewModel.redownloadPlaylist(playlist) },
                            onRemove = { removeTarget = playlist },
                            onOpen = { openPlaylist(playlist.openRoute) },
                        )
                    }
                }

                if (autoDownloadPlaylists.isNotEmpty()) {
                    item(key = "header_auto_download") {
                        SectionHeader(stringResource(R.string.auto_download_playlists))
                    }
                    items(items = autoDownloadPlaylists, key = { "auto_${it.id}" }) { playlist ->
                        AutoDownloadRow(
                            playlist = playlist,
                            onTurnOff = { viewModel.disableAutoDownload(playlist.id) },
                            onOpen = { openPlaylist(playlist.openRoute) },
                        )
                    }
                }

                if (queue.isNotEmpty()) {
                    // Only label the song queue when playlist sections are also present.
                    if (downloadingPlaylists.isNotEmpty() || downloadedPlaylists.isNotEmpty() ||
                        autoDownloadPlaylists.isNotEmpty()
                    ) {
                        item(key = "header_songs") {
                            SectionHeader(stringResource(R.string.individual_songs))
                        }
                    }
                    items(items = queue, key = { it.request.id }) { download ->
                        DownloadQueueRow(
                            title = titles[download.request.id] ?: download.request.id,
                            download = download,
                            onPauseResume = {
                                if (download.stopReason != Download.STOP_REASON_NONE) {
                                    downloadUtil.resumeDownload(download.request.id)
                                } else {
                                    downloadUtil.pauseDownload(download.request.id)
                                }
                            },
                            onCancel = { downloadUtil.cancelDownload(download.request.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlaylistArtwork(url: String?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Icon(
            painter = painterResource(R.drawable.queue_music),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DownloadingPlaylistRow(
    playlist: DownloadManagementViewModel.PlaylistDownload,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PlaylistArtwork(playlist.thumbnailUrl)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = if (playlist.anyPaused) {
                stringResource(R.string.playlist_download_paused_status, playlist.completed, playlist.total)
            } else {
                stringResource(R.string.playlist_downloading_status, playlist.completed, playlist.total, playlist.active)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { playlist.fraction },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        IconButton(onClick = onPauseResume) {
            Icon(
                painter = painterResource(if (playlist.anyPaused) R.drawable.play else R.drawable.pause),
                contentDescription = stringResource(
                    if (playlist.anyPaused) R.string.resume_download else R.string.pause_download
                ),
            )
        }
        IconButton(onClick = onCancel) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.cancel_download),
            )
        }
    }
}

@Composable
private fun DownloadedPlaylistRow(
    playlist: DownloadManagementViewModel.PlaylistDownload,
    onRedownload: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PlaylistArtwork(playlist.thumbnailUrl)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.playlist_downloaded_status, playlist.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRedownload) {
            Icon(
                painter = painterResource(R.drawable.download),
                contentDescription = stringResource(R.string.redownload_playlist),
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = stringResource(R.string.remove_download),
            )
        }
    }
}

@Composable
private fun AutoDownloadRow(
    playlist: DownloadManagementViewModel.AutoDownloadPlaylist,
    onTurnOff: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PlaylistArtwork(playlist.thumbnailUrl)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.auto_download_on),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onTurnOff) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.turn_off_auto_download),
            )
        }
    }
}

@Composable
private fun DownloadQueueRow(
    title: String,
    download: Download,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
) {
    val paused = download.stopReason != Download.STOP_REASON_NONE || download.state == Download.STATE_STOPPED
    val percent = download.percentDownloaded
    val statusRes = when {
        paused -> R.string.download_state_paused
        download.state == Download.STATE_DOWNLOADING -> R.string.download_state_downloading
        download.state == Download.STATE_RESTARTING -> R.string.download_state_restarting
        else -> R.string.download_state_queued
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val status = stringResource(statusRes)
            Text(
                text = if (download.state == Download.STATE_DOWNLOADING && percent in 0f..100f) {
                    "$status · ${percent.toInt()}%"
                } else {
                    status
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (download.state == Download.STATE_DOWNLOADING && percent in 0f..100f) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onPauseResume) {
                Icon(
                    painter = painterResource(if (paused) R.drawable.play else R.drawable.pause),
                    contentDescription = stringResource(
                        if (paused) R.string.resume_download else R.string.pause_download
                    ),
                )
            }
            IconButton(onClick = onCancel) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.cancel_download),
                )
            }
        }
    }
}

/** Downloading first, then restarting, queued, then paused/stopped last. */
private fun queueOrder(download: Download): Int = when {
    download.stopReason != Download.STOP_REASON_NONE || download.state == Download.STATE_STOPPED -> 3
    download.state == Download.STATE_DOWNLOADING -> 0
    download.state == Download.STATE_RESTARTING -> 1
    else -> 2
}
