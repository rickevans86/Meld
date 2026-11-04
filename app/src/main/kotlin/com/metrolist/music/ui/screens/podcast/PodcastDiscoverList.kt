/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.podcast

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.podcasts.DiscoverShow
import com.metrolist.music.podcasts.PodcastDiscover
import com.metrolist.music.viewmodels.PodcastDiscoverViewModel
import java.util.Locale

/**
 * Discover tab in Podcasts: top shows for your country, then a row per category, like Pocket Casts.
 * Tap a show to see its episodes, or + to follow it.
 */
@Composable
fun PodcastDiscoverList(
    navController: NavController,
    lazyListState: LazyListState,
    header: @Composable () -> Unit,
    viewModel: PodcastDiscoverViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val followed by viewModel.followed.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val followedNames = followed.map { normalize(it.title) }.toSet()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item(key = "filter") { header() }
        val loaded = rows
        when {
            loaded == null -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            loaded.isEmpty() -> item(key = "failed") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.podcast_discover_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { viewModel.load(refresh = true) }) { Text(stringResource(R.string.retry)) }
                }
            }
            else -> loaded.forEach { row ->
                item(key = "row:${row.title}") {
                    val title = if (row.title == PodcastDiscover.TOP_TITLE) {
                        stringResource(R.string.podcast_discover_top, viewModel.countryName)
                    } else {
                        row.title
                    }
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(row.shows, key = { it.appleId }) { show ->
                                DiscoverCard(
                                    show = show,
                                    following = normalize(show.name) in followedNames,
                                    busy = show.appleId in busy,
                                    onOpen = {
                                        viewModel.open(show) { feed ->
                                            navController.navigate("podcast_directory?feed=${android.net.Uri.encode(feed)}")
                                        }
                                    },
                                    onFollow = { viewModel.follow(show) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "credit") {
            Text(
                stringResource(R.string.podcast_discover_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun DiscoverCard(
    show: DiscoverShow,
    following: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onFollow: () -> Unit,
) {
    Column(Modifier.width(128.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen)) {
        Box {
            AsyncImage(
                model = show.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
            // + follows straight from the chart; a tick shows it's already followed
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .clickable(enabled = !following && !busy, onClick = onFollow),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    busy -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else -> Icon(
                        painterResource(if (following) R.drawable.check else R.drawable.add),
                        contentDescription = stringResource(if (following) R.string.podcast_discover_following else R.string.podcast_discover_follow),
                        tint = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(show.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        show.artist?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun normalize(text: String) = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
