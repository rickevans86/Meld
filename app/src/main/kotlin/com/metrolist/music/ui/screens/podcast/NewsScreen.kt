/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.podcast

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.viewmodels.NewsViewModel

/** The News tab: play every chosen outlet's latest bulletin, or pick one. */
@Composable
fun NewsScreen(
    navController: NavController,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val choices by viewModel.choices.collectAsState()
    val bulletins by viewModel.bulletins.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val currentId by playerConnection.mediaMetadata.collectAsState()
    val enabled = choices.filter { it.enabled }
    val enabledKeys = enabled.map { it.source.key }
    val sortedSources by viewModel.sortedEnabled.collectAsState()
    val sortSetting by viewModel.sort.collectAsState()
    // Typed in the top-bar search: shows only sources or bulletins that match
    val query = com.metrolist.music.ui.component.rememberPageSearchQuery("news")

    LaunchedEffect(enabledKeys) { if (enabledKeys.isNotEmpty()) viewModel.refresh() }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "play_all") {
            NewsBriefingCard(navController, viewModel = viewModel, modifier = Modifier.padding(top = 4.dp))
        }
        item(key = "header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            ) {
                Text(
                    stringResource(R.string.news_latest_bulletins),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                }
                com.metrolist.music.ui.component.SortMenuButton(
                    options = listOf(
                        com.metrolist.music.podcasts.NewsSort.YOUR_ORDER to stringResource(R.string.sort_your_order),
                        com.metrolist.music.podcasts.NewsSort.LATEST to stringResource(R.string.sort_latest_bulletin),
                        com.metrolist.music.podcasts.NewsSort.NAME to stringResource(R.string.sort_name),
                        com.metrolist.music.podcasts.NewsSort.REGION to stringResource(R.string.sort_region),
                    ),
                    selected = sortSetting.first,
                    reversed = sortSetting.second,
                    onSelect = viewModel::setSort,
                    onReversedChange = viewModel::setSortReversed,
                )
                IconButton(onClick = viewModel::refresh, enabled = !refreshing && enabled.isNotEmpty()) {
                    Icon(painterResource(R.drawable.refresh), contentDescription = stringResource(R.string.news_refresh))
                }
            }
        }
        if (enabled.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(R.string.news_choose_sources),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { navController.navigate("news_sources") }
                        .padding(16.dp),
                )
            }
        }
        val shownChoices = sortedSources.mapNotNull { s -> enabled.firstOrNull { it.source.key == s.key } }.filter { choice ->
            com.metrolist.music.ui.component.matchesSearch(query, choice.source.name, bulletins[choice.source.key]?.song?.song?.title)
        }
        if (query.isNotBlank() && shownChoices.isEmpty() && enabled.isNotEmpty()) {
            item(key = "no_match") {
                Text(
                    stringResource(R.string.page_search_no_match),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
        items(shownChoices, key = { it.source.key }) { choice ->
            val loaded = choice.source.key in bulletins
            val bulletin = bulletins[choice.source.key]
            val isCurrent = bulletin != null && currentId?.id == bulletin.song.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = bulletin != null) {
                        bulletin?.let { b ->
                            viewModel.playOne(b) { song ->
                                playerConnection.playQueue(ListQueue(title = choice.source.name, items = listOf(song.toMediaItem())))
                            }
                        }
                    }
                    .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    val art = bulletin?.artworkUrl ?: bulletin?.song?.song?.thumbnailUrl ?: choice.source.artworkUrl
                    if (art != null) {
                        AsyncImage(model = art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp))
                    } else {
                        Icon(painterResource(R.drawable.newspaper), contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        choice.source.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when {
                            !loaded -> stringResource(R.string.news_loading_one)
                            bulletin == null -> stringResource(R.string.news_no_recent)
                            else -> listOfNotNull(
                                bulletin.publishedAt?.let { DateUtils.getRelativeTimeSpanString(it).toString() },
                                bulletin.song.song.title,
                            ).joinToString(" · ")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (bulletin != null) {
                    Icon(painterResource(R.drawable.play), contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
