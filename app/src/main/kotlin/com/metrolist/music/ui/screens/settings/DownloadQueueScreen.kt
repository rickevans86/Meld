/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.constants.DownloadQueuePausedKey
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wavelength: lists the songs/episodes waiting to download, with per-item pause/resume/cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(
    navController: NavController,
) {
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val downloadsMap by downloadUtil.downloads.collectAsState()
    val (queuePaused, onQueuePausedChange) = rememberPreference(DownloadQueuePausedKey, defaultValue = false)

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

    androidx.compose.material3.Scaffold(
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
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.download_queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = paddingValues, modifier = Modifier.fillMaxSize()) {
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
