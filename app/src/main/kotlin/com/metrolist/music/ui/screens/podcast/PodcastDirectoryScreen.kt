/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.podcast

import android.text.format.DateUtils
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.podcasts.PlayedEpisodes
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.remember
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
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.SongMenu
import androidx.compose.material3.IconButton as PlainIconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.PodcastDirectoryViewModel
import com.metrolist.music.viewmodels.ShowPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDirectoryScreen(
    navController: NavController,
    initialFeedUrl: String? = null,
    viewModel: PodcastDirectoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState(initial = false)
    val menuState = LocalMenuState.current
    val played = com.metrolist.music.podcasts.rememberPlayedEpisodes()
    var downloadedOnly by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val downloadStates by com.metrolist.music.LocalDownloadUtil.current.downloads.collectAsState()
    val speeds by viewModel.speeds.collectAsState()
    val (hidePlayed, onHidePlayedChange) = rememberPreference(com.metrolist.music.podcasts.HidePlayedEpisodesKey, false)
    val (oldestFirst, onOldestFirstChange) = rememberPreference(com.metrolist.music.podcasts.PodcastOldestFirstKey, false)

    val followed by viewModel.followed.collectAsState()
    val results by viewModel.results.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val spotifyShows by viewModel.spotifyShows.collectAsState()
    val page by viewModel.page.collectAsState()
    val message by viewModel.message.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Opened straight onto a followed show: back returns to where the user came from
    val openedDirectly = initialFeedUrl != null
    LaunchedEffect(initialFeedUrl) {
        if (initialFeedUrl != null && viewModel.page.value == null) viewModel.openFeed(initialFeedUrl)
    }
    BackHandler(enabled = page != null && !openedDirectly) { viewModel.close() }
    LaunchedEffect(page?.title) { listState.scrollToItem(0) }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }

    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
    // Same search top bar as every other tab's search
    if (page == null) {
        com.metrolist.music.ui.component.SearchTopBar(
            query = query,
            onQueryChange = {
                query = it
                viewModel.search(it)
            },
            placeholder = stringResource(R.string.podcast_search_hint),
            onBack = { navController.navigateUp() },
            onSearch = { viewModel.search(it) },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            actions = {
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.padding(horizontal = 12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    PlainIconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                            if (pasted.isNullOrEmpty()) {
                                Toast.makeText(context, R.string.podcast_clipboard_empty, Toast.LENGTH_SHORT).show()
                            } else {
                                query = pasted
                                viewModel.search(pasted)
                            }
                        },
                    ) {
                        Icon(painterResource(R.drawable.content_paste), contentDescription = stringResource(R.string.podcast_paste))
                    }
                }
            },
        )
    }
    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(
                if (page == null) {
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                } else {
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom + WindowInsetsSides.Top
                },
            )
            .asPaddingValues(),
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        when (val current = page) {
            null -> {
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
                        items(spotifyShows.distinctBy { it.id }, key = { "spotify:${it.id}" }) { show ->
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

            is ShowPage.Unavailable -> {
                item(key = "unavailable") { Hint(current.message) }
                // A followed show whose feed has gone (e.g. imported from OPML) can be removed from here
                val deadShow = current.show?.takeIf { s -> followed.any { it.feedUrl == s.feedUrl } }
                if (deadShow != null) {
                    item(key = "unfollow_dead") {
                        androidx.compose.material3.OutlinedButton(
                            onClick = {
                                viewModel.unfollow(deadShow)
                                if (openedDirectly) navController.navigateUp() else viewModel.close()
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text(stringResource(R.string.podcast_unfollow))
                        }
                    }
                }
            }

            is ShowPage.Ready -> {
                val show = current.feed.show
                val isFollowing = followed.any { it.feedUrl == show.feedUrl }
                val followedShow = followed.firstOrNull { it.feedUrl == show.feedUrl }
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
                item(key = "show_options") {
                    ShowOptions(
                        autoDownload = followedShow?.autoDownload == true,
                        keep = followedShow?.autoDownloadKeep ?: 3,
                        speed = speeds[show.feedUrl] ?: 1f,
                        hidePlayed = hidePlayed,
                        onAutoDownload = { enabled, keep -> viewModel.setAutoDownload(show, enabled, keep) },
                        onSpeed = { viewModel.setSpeed(show, it) },
                        onHidePlayed = onHidePlayedChange,
                    )
                }
                val episodesById = current.feed.episodes.associateBy { PODCAST_EPISODE_ID_PREFIX + it.id }
                // Played episodes can be hidden; indexes stay those of the full list for playing
                // "Downloaded" chip: only episodes saved on the phone
                item(key = "downloaded_filter") {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        com.metrolist.music.podcasts.EpisodeSortChip(oldestFirst, onOldestFirstChange)
                        androidx.compose.material3.FilterChip(
                            selected = downloadedOnly,
                            onClick = { downloadedOnly = !downloadedOnly },
                            label = { Text(stringResource(R.string.filter_downloaded)) },
                        )
                    }
                }
                // The feed is newest first; "Oldest first" shows (and plays) it the other way round
                val ordered = current.songs.withIndex().toList().let { if (oldestFirst) it.reversed() else it }
                val visible = ordered.filter { (_, song) ->
                    (!hidePlayed || song.id !in played || song.id == mediaMetadata?.id) &&
                        (!downloadedOnly || downloadStates[song.id]?.state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED)
                }
                items(visible, key = { "episode:${it.value.id}" }) { (index, song) ->
                    val episode = episodesById[song.id]
                    val isPlayed = song.id in played
                    SongListItem(
                        song = song,
                        subtitleOverride = episode?.let { episodeDetails(it) },
                        showLikedIcon = false,
                        isActive = mediaMetadata?.id == song.id,
                        isPlaying = isPlaying,
                        trailingContent = {
                            com.metrolist.music.podcasts.EpisodeDownloadButton(song)
                            PlainIconButton(onClick = { PlayedEpisodes.set(context, song.id, !isPlayed) }) {
                                Icon(
                                    painterResource(if (isPlayed) R.drawable.check_circle else R.drawable.radio_button_unchecked),
                                    contentDescription = stringResource(if (isPlayed) R.string.mark_unplayed else R.string.mark_played),
                                    tint = if (isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            PlainIconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(originalSong = song, onDismiss = menuState::dismiss)
                                    }
                                },
                            ) {
                                Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    viewModel.play(index) { songs, start ->
                                        if (oldestFirst) {
                                            // Play on towards newer episodes
                                            playerConnection.playQueue(queueOf(show.title, songs.reversed(), songs.lastIndex - start))
                                        } else {
                                            playerConnection.playQueue(queueOf(show.title, songs, start))
                                        }
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
    }

    // A show's page keeps its title bar; the search list uses the search top bar above
    if (page != null) TopAppBar(
        title = {
            Text(
                page?.title?.ifBlank { null } ?: stringResource(R.string.podcast_directory),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = { if (page != null && !openedDirectly) viewModel.close() else navController.navigateUp() },
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

/** Per-show options: auto-download on Wi-Fi (and how many to keep), playback speed, hide played. */
@Composable
private fun ShowOptions(
    autoDownload: Boolean,
    keep: Int,
    speed: Float,
    hidePlayed: Boolean,
    onAutoDownload: (Boolean, Int) -> Unit,
    onSpeed: (Float?) -> Unit,
    onHidePlayed: (Boolean) -> Unit,
) {
    var keepMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        FilterChip(
            selected = autoDownload,
            onClick = { onAutoDownload(!autoDownload, keep) },
            label = { Text(stringResource(R.string.podcast_auto_download)) },
            leadingIcon = { Icon(painterResource(R.drawable.download), contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        if (autoDownload) {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { keepMenu = true },
                    label = { Text(stringResource(R.string.podcast_keep_count, keep)) },
                )
                DropdownMenu(expanded = keepMenu, onDismissRequest = { keepMenu = false }) {
                    listOf(1, 2, 3, 5, 10).forEach { count ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.podcast_keep_count, count)) },
                            onClick = {
                                onAutoDownload(true, count)
                                keepMenu = false
                            },
                        )
                    }
                }
            }
        }
        Box {
            FilterChip(
                selected = speed != 1f,
                onClick = { speedMenu = true },
                label = { Text(stringResource(R.string.podcast_speed, formatSpeed(speed))) },
            )
            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                listOf(0.8f, 1f, 1.2f, 1.5f, 1.75f, 2f).forEach { value ->
                    DropdownMenuItem(
                        text = { Text(formatSpeed(value)) },
                        onClick = {
                            onSpeed(value.takeIf { it != 1f })
                            speedMenu = false
                        },
                    )
                }
            }
        }
        FilterChip(
            selected = hidePlayed,
            onClick = { onHidePlayed(!hidePlayed) },
            label = { Text(stringResource(R.string.podcast_hide_played)) },
        )
    }
}

private fun formatSpeed(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0')) + "×"

@Composable
private fun episodeDetails(episode: PodcastEpisode): String? =
    listOfNotNull(
        episode.publishedAt?.let {
            DateUtils.formatDateTime(LocalContext.current, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        },
        episode.durationSec?.takeIf { it > 0 }?.let { formatLength(it) },
    ).joinToString(" · ").ifEmpty { null }

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
