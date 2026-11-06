/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.podcast

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.NewEpisodesViewModel
import com.metrolist.music.ui.component.IconButton as LongPressIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(
    navController: NavController,
    viewModel: NewEpisodesViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val followedAll by viewModel.followed.collectAsState()
    val youtubeAll by viewModel.youtube.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val played = com.metrolist.music.podcasts.rememberPlayedEpisodes()
    val (hidePlayed, onHidePlayedChange) = com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.podcasts.HidePlayedEpisodesKey,
        false,
    )
    val followed = followedAll?.filter { !hidePlayed || it.first.id !in played }
    val youtube = youtubeAll?.filter { !hidePlayed || it.id !in played }
    val refreshing by viewModel.refreshing.collectAsState()
    val current by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState(initial = false)
    val title = stringResource(R.string.new_episodes)

    // Followed podcasts first (newest first), then YouTube subscriptions
    val queue = followed.orEmpty().map { it.first.toMediaItem() } + youtube.orEmpty().map { it.toMediaItem() }
    fun playFrom(index: Int) {
        if (queue.isNotEmpty()) playerConnection.playQueue(ListQueue(title = title, items = queue, startIndex = index))
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom + WindowInsetsSides.Top)
            .asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Button(onClick = { playFrom(0) }, enabled = queue.isNotEmpty()) {
                    Icon(painterResource(R.drawable.play), contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.subsonic_play_all))
                }
                androidx.compose.material3.FilterChip(
                    selected = hidePlayed,
                    onClick = { onHidePlayedChange(!hidePlayed) },
                    label = { Text(stringResource(R.string.podcast_hide_played)) },
                )
                Spacer(Modifier.weight(1f))
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                IconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                    Icon(painterResource(R.drawable.refresh), contentDescription = stringResource(R.string.news_refresh))
                }
            }
        }

        item(key = "followed_title") { SectionTitle(stringResource(R.string.new_episodes_followed)) }
        val rss = followed
        when {
            rss == null -> item(key = "followed_loading") { LoadingRow() }
            rss.isEmpty() -> item(key = "followed_empty") { EmptyRow(stringResource(R.string.new_episodes_followed_empty)) }
            else -> itemsIndexed(rss, key = { _, item -> "rss:${item.first.id}" }) { index, (song, publishedAt) ->
                SongListItem(
                    song = song,
                    subtitleOverride = listOfNotNull(
                        song.song.albumName,
                        publishedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() },
                    ).joinToString(" · "),
                    showLikedIcon = false,
                    isActive = current?.id == song.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        com.metrolist.music.podcasts.EpisodeDownloadButton(song)
                        val isPlayed = song.id in played
                        IconButton(onClick = { com.metrolist.music.podcasts.PlayedEpisodes.set(context, song.id, !isPlayed) }) {
                            Icon(
                                painterResource(if (isPlayed) R.drawable.check_circle else R.drawable.radio_button_unchecked),
                                contentDescription = stringResource(if (isPlayed) R.string.mark_unplayed else R.string.mark_played),
                                tint = if (isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) } }) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (current?.id == song.id) playerConnection.togglePlayPause() else playFrom(index)
                    },
                )
            }
        }

        item(key = "youtube_title") { SectionTitle(stringResource(R.string.new_episodes_youtube)) }
        val yt = youtube
        when {
            yt == null -> item(key = "youtube_loading") { LoadingRow() }
            yt.isEmpty() -> item(key = "youtube_empty") { EmptyRow(stringResource(R.string.new_episodes_youtube_empty)) }
            else -> itemsIndexed(yt, key = { _, item -> "yt:${item.id}" }) { index, item ->
                YouTubeListItem(
                    item = item,
                    isActive = current?.id == item.id,
                    isPlaying = isPlaying,
                    trailingContent = {
                        IconButton(onClick = { menuState.show { YouTubeSongMenu(item, menuState::dismiss) } }) {
                            Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (current?.id == item.id) playerConnection.togglePlayPause() else playFrom(rss.orEmpty().size + index)
                    },
                )
            }
        }
    }

    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            LongPressIconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
