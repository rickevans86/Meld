/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.podcast

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.PODCAST_EPISODE_ID_PREFIX
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.podcasts.PodcastEpisode
import com.metrolist.music.podcasts.PodcastShow
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.PodcastDirectoryViewModel
import com.metrolist.music.viewmodels.ShowPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDirectoryScreen(
    navController: NavController,
    viewModel: PodcastDirectoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val followed by viewModel.followed.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val spotifyShows by viewModel.spotifyShows.collectAsState()
    val page by viewModel.page.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    BackHandler(enabled = page != null) { viewModel.close() }
    LaunchedEffect(page?.title) { listState.scrollToItem(0) }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom + WindowInsetsSides.Top)
            .asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val current = page) {
            null -> {
                item(key = "search") {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.search(it)
                        },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.podcast_search_hint)) },
                        trailingIcon = {
                            if (searching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                val found = results
                if (query.isNotBlank() && found != null) {
                    if (found.isEmpty()) item(key = "no_results") { Hint(stringResource(R.string.podcast_no_results)) }
                    items(found, key = { "result:${it.feedUrl}" }) { show ->
                        ShowRow(show.title, show.author, show.artworkUrl) { viewModel.open(show) }
                    }
                } else {
                    if (followed.isNotEmpty()) {
                        item(key = "following_title") { SectionTitle(stringResource(R.string.podcast_following)) }
                        items(followed, key = { "followed:${it.feedUrl}" }) { show ->
                            ShowRow(show.title, show.author, show.artworkUrl) { viewModel.open(show) }
                        }
                    }
                    if (spotifyShows.isNotEmpty()) {
                        item(key = "spotify_title") { SectionTitle(stringResource(R.string.podcast_spotify_shows)) }
                        items(spotifyShows, key = { "spotify:${it.id}" }) { show ->
                            ShowRow(show.name, show.publisher, show.imageUrl) { viewModel.openSpotify(show) }
                        }
                    }
                    if (followed.isEmpty() && spotifyShows.isEmpty()) {
                        item(key = "intro") { Hint(stringResource(R.string.podcast_directory_intro)) }
                    }
                }
            }

            is ShowPage.Loading -> item(key = "loading") {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(48.dp)) {
                    CircularProgressIndicator()
                }
            }

            is ShowPage.Unavailable -> item(key = "unavailable") { Hint(current.message) }

            is ShowPage.Ready -> {
                val show = current.feed.show
                val isFollowing = followed.any { it.feedUrl == show.feedUrl }
                item(key = "show_header") {
                    ShowHeader(
                        show = show,
                        episodeCount = current.feed.episodes.size,
                        following = isFollowing,
                        onToggleFollow = { viewModel.toggleFollow(show) },
                        onPlayLatest = {
                            viewModel.play(0) { songs, start -> playerConnection.playQueue(queueOf(show.title, songs, start)) }
                        },
                    )
                }
                itemsIndexed(current.feed.episodes, key = { _, episode -> "episode:${episode.id}" }) { index, episode ->
                    EpisodeRow(
                        episode = episode,
                        isCurrent = mediaMetadata?.id == PODCAST_EPISODE_ID_PREFIX + episode.id,
                        onClick = {
                            viewModel.play(index) { songs, start ->
                                playerConnection.playQueue(queueOf(show.title, songs, start))
                            }
                        },
                    )
                }
            }
        }
    }

    TopAppBar(
        title = {
            Text(
                page?.title ?: stringResource(R.string.podcast_directory),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = { if (page != null) viewModel.close() else navController.navigateUp() },
                onLongClick = navController::backToMain,
            ) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

private fun queueOf(title: String, songs: List<com.metrolist.music.db.entities.Song>, start: Int) =
    ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = start)

@Composable
private fun ShowHeader(
    show: PodcastShow,
    episodeCount: Int,
    following: Boolean,
    onToggleFollow: () -> Unit,
    onPlayLatest: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Artwork(show.artworkUrl, 112.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(show.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                show.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    stringResource(R.string.podcast_episode_count, episodeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPlayLatest, enabled = episodeCount > 0, modifier = Modifier.weight(1f)) {
                Icon(painterResource(R.drawable.play), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.podcast_play_latest))
            }
            OutlinedButton(onClick = onToggleFollow, modifier = Modifier.weight(1f)) {
                Text(stringResource(if (following) R.string.podcast_unfollow else R.string.podcast_follow))
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: PodcastEpisode, isCurrent: Boolean, onClick: () -> Unit) {
    val details = listOfNotNull(
        episode.publishedAt?.let {
            DateUtils.formatDateTime(LocalContext.current, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        },
        episode.durationSec?.takeIf { it > 0 }?.let { formatLength(it) },
    ).joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            episode.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (details.isNotEmpty()) {
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ShowRow(title: String, author: String?, artworkUrl: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Artwork(artworkUrl, 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Artwork(url: String?, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(painterResource(R.drawable.mic), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

private fun formatLength(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}
