/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import androidx.compose.material3.IconButton as PlainIconButton
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.offline.DownloadService
import com.metrolist.music.playback.ExoDownloadService
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first

/**
 * Podcast episode downloads: download when an episode starts playing, delete it when it finishes
 * (Settings → Features → Podcasts), plus a download / delete button on episode rows.
 */
object EpisodeDownloads {
    /** Download an episode as soon as it starts playing. */
    val DownloadOnPlayKey = booleanPreferencesKey("wavelengthEpisodeDownloadOnPlay")

    /** Delete an episode's download once it's played to the end. */
    val DeleteOnFinishKey = booleanPreferencesKey("wavelengthEpisodeDeleteOnFinish")

    suspend fun downloadOnPlay(context: Context): Boolean = context.dataStore.data.first()[DownloadOnPlayKey] ?: true

    suspend fun deleteOnFinish(context: Context): Boolean = context.dataStore.data.first()[DeleteOnFinishKey] ?: true
}

/** Download button for an episode row: download, a spinner while downloading, or tick (tap to delete). */
@Composable
fun EpisodeDownloadButton(song: Song) {
    val context = LocalContext.current
    val downloadUtil = LocalDownloadUtil.current
    val downloads by downloadUtil.downloads.collectAsState()
    val state = downloads[song.id]?.state
    PlainIconButton(
        onClick = {
            when (state) {
                Download.STATE_COMPLETED, Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.id, false)
                else -> downloadUtil.download(song)
            }
        },
    ) {
        when (state) {
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Download.STATE_COMPLETED -> Icon(
                painterResource(R.drawable.offline),
                contentDescription = stringResource(R.string.episode_delete_download),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                painterResource(R.drawable.download),
                contentDescription = stringResource(R.string.episode_download),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
