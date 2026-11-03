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
import androidx.compose.foundation.lazy.itemsIndexed
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSourcesScreen(
    navController: NavController,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val choices by viewModel.choices.collectAsState()
    val enabledCount = choices.count { it.enabled }

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
        itemsIndexed(choices, key = { _, choice -> choice.source.key }) { index, choice ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggle(choice) }
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Checkbox(checked = choice.enabled, onCheckedChange = { viewModel.toggle(choice) })
                Column(modifier = Modifier.weight(1f)) {
                    Text(choice.source.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        choice.source.region,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (choice.enabled) {
                    PlainIconButton(onClick = { viewModel.move(choice, -1) }, enabled = index > 0) {
                        Icon(painterResource(R.drawable.arrow_upward), contentDescription = stringResource(R.string.news_move_up))
                    }
                    PlainIconButton(onClick = { viewModel.move(choice, 1) }, enabled = index < enabledCount - 1) {
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
