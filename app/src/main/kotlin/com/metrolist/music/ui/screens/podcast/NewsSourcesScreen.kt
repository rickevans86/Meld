/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.metrolist.music.podcasts.NewsBriefing
import com.metrolist.music.podcasts.SourceStatus
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.NewsViewModel
import androidx.compose.material3.IconButton as PlainIconButton

private const val SELECTED = "__selected__"
private const val UNPLAYABLE = "__unplayable__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSourcesScreen(
    navController: NavController,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val choices by viewModel.choices.collectAsState()
    val checking by viewModel.checking.collectAsState()
    val statuses by viewModel.statuses.collectAsState()
    val checkProgress by viewModel.checkProgress.collectAsState()
    val unplayableCount = choices.count { statuses[it.source.key] == SourceStatus.NO_FEED }
    val enabledKeys = choices.filter { it.enabled }.map { it.source.key }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val categories = remember(choices) {
        (NewsBriefing.CATEGORIES + choices.map { it.source.region }).distinct()
            .filter { name -> choices.any { it.source.region == name } }
    }
    val shown = choices.filter { choice ->
        (query.isBlank() || choice.source.name.contains(query.trim(), ignoreCase = true)) &&
            (
                category == null || choice.source.region == category || (category == SELECTED && choice.enabled) ||
                    (category == UNPLAYABLE && statuses[choice.source.key] == SourceStatus.NO_FEED)
                )
    }

    // Check every source in the background so ones that can't be played are struck through
    LaunchedEffect(Unit) { viewModel.checkAllSources() }

    LaunchedEffect(Unit) {
        viewModel.unavailable.collect { name ->
            Toast.makeText(context, context.getString(R.string.news_source_unavailable, name), Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom + WindowInsetsSides.Top)
            .asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "intro") {
            Text(
                stringResource(R.string.news_sources_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.news_search_sources)) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item(key = "categories") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.news_category_all)) })
                }
                item {
                    FilterChip(
                        selected = category == SELECTED,
                        onClick = { category = if (category == SELECTED) null else SELECTED },
                        label = { Text(stringResource(R.string.news_category_selected, enabledKeys.size)) },
                    )
                }
                if (unplayableCount > 0) {
                    item {
                        FilterChip(
                            selected = category == UNPLAYABLE,
                            onClick = { category = if (category == UNPLAYABLE) null else UNPLAYABLE },
                            label = { Text(stringResource(R.string.news_category_unplayable, unplayableCount)) },
                        )
                    }
                }
                items(categories) { name ->
                    FilterChip(
                        selected = category == name,
                        onClick = { category = if (category == name) null else name },
                        label = { Text(name) },
                    )
                }
            }
        }
        item(key = "check_status") {
            val progress = checkProgress
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            ) {
                if (progress != null) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        stringResource(R.string.news_checking_sources, progress.first, progress.second),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                } else {
                    Text(
                        stringResource(R.string.news_unplayable_summary, unplayableCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.checkAllSources(force = true) }) {
                        Text(stringResource(R.string.news_check_again))
                    }
                }
            }
        }
        items(shown, key = { choice -> choice.source.key }) { choice ->
            val position = enabledKeys.indexOf(choice.source.key)
            val status = statuses[choice.source.key]
            val unplayable = status == SourceStatus.NO_FEED
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggle(choice) }
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                if (checking == choice.source.key) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                } else {
                    Checkbox(checked = choice.enabled, onCheckedChange = { viewModel.toggle(choice) })
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        choice.source.name,
                        style = MaterialTheme.typography.bodyLarge,
                        // Sources with no public podcast are struck through and dimmed
                        textDecoration = if (unplayable) TextDecoration.LineThrough else null,
                        color = if (unplayable) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        when (status) {
                            SourceStatus.NO_FEED -> stringResource(R.string.news_status_no_feed, choice.source.region)
                            SourceStatus.QUIET -> stringResource(R.string.news_status_quiet, choice.source.region)
                            else -> choice.source.region
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unplayable) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (choice.enabled) {
                    PlainIconButton(onClick = { viewModel.move(choice, -1) }, enabled = position > 0) {
                        Icon(painterResource(R.drawable.arrow_upward), contentDescription = stringResource(R.string.news_move_up))
                    }
                    PlainIconButton(onClick = { viewModel.move(choice, 1) }, enabled = position < enabledKeys.size - 1) {
                        Icon(painterResource(R.drawable.arrow_downward), contentDescription = stringResource(R.string.news_move_down))
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.news_sources)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}
