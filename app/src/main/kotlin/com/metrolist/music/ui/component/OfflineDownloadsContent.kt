/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.OfflineMusicSource
import com.metrolist.music.playback.offlineMusicSongs
import com.metrolist.music.playback.queues.ListQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/** Local catalog only: opening this list never needs server credentials or a network request. */
@Composable
fun OfflineDownloadsContent(source: OfflineMusicSource, onBack: (() -> Unit)? = null) {
    val database = LocalDatabase.current
    val downloads = LocalDownloadUtil.current
    val player = LocalPlayerConnection.current ?: return
    val current by player.mediaMetadata.collectAsState()
    val playing by player.isEffectivelyPlaying.collectAsState()
    val flow = remember(database, downloads, source) {
        combine(database.downloadedSongsByCreateDateAsc(), downloads.downloads) { songs, states ->
            val spotifyIds = if (source == OfflineMusicSource.SPOTIFY) songs.map { it.id }.chunked(500)
                .flatMap { database.getSpotifyMatchesByYouTubeIds(it) }.mapTo(mutableSetOf()) { it.youtubeId } else emptySet()
            offlineMusicSongs(songs, states, source, spotifyIds)
        }.flowOn(Dispatchers.IO)
    }
    val songs by flow.collectAsState(initial = emptyList())
    val title = stringResource(R.string.downloaded_songs)
    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Text(title)
                Text(stringResource(R.string.offline_saved_content_hint))
            }
        }
        if (songs.isEmpty()) item { Text(stringResource(R.string.offline_no_saved_music), Modifier.padding(16.dp)) }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongListItem(song = song, isActive = current?.id == song.id, isPlaying = playing,
                modifier = Modifier.fillMaxWidth().clickable {
                    if (current?.id == song.id) player.togglePlayPause()
                    else player.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = index))
                })
        }
    }
}

/** Keep a local-download entry available even when a reachable network has an unavailable server. */
@Composable
fun OfflineDownloadsGate(source: OfflineMusicSource, content: @Composable () -> Unit) {
    val player = LocalPlayerConnection.current ?: return
    val online by player.service.connectivityObserver.networkStatus.collectAsState()
    var downloads by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = downloads && online) { downloads = false }
    if (!online || downloads) {
        OfflineDownloadsContent(source, onBack = if (online) ({ downloads = false }) else null)
    } else {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { downloads = true }) { Text(stringResource(R.string.downloaded_songs)) }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}
