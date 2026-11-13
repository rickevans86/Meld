/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.library.audiobooks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.audiobooks.AudiobookSort
import com.metrolist.music.audiobooks.AudiobookTab
import com.metrolist.music.audiobooks.AudiobookTabs
import com.metrolist.music.utils.rememberPreference

/** The saved tab order, open tab and library sort, shared by both sources. */
internal class AudiobookTabState(
    val order: List<AudiobookTab>,
    val selected: AudiobookTab,
    val select: (AudiobookTab) -> Unit,
    val sort: AudiobookSort,
    val setSort: (AudiobookSort) -> Unit,
)

@Composable
internal fun rememberAudiobookTabState(): AudiobookTabState {
    val (orderRaw, _) = rememberPreference(AudiobookTabs.OrderKey, "")
    val (selectedRaw, saveSelected) = rememberPreference(AudiobookTabs.SelectedKey, "")
    val (sortRaw, saveSort) = rememberPreference(AudiobookTabs.SortKey, AudiobookSort.TITLE.id)
    val order = remember(orderRaw) { AudiobookTabs.decode(orderRaw) }
    return AudiobookTabState(
        order = order,
        selected = AudiobookTabs.selected(selectedRaw, order),
        select = { saveSelected(it.id) },
        sort = AudiobookTabs.sort(sortRaw),
        setSort = { saveSort(it.id) },
    )
}

@Composable
internal fun AudiobookTabBar(order: List<AudiobookTab>, selected: AudiobookTab, onSelect: (AudiobookTab) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = order.indexOf(selected).coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        order.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(stringResource(tab.titleRes), maxLines = 1) },
            )
        }
    }
}

/** "Your library · 24 books" with optional actions and the Sort button. */
@Composable
internal fun LibraryHeader(
    count: String?,
    sort: AudiobookSort,
    sortOptions: List<AudiobookSort>,
    onSort: (AudiobookSort) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var choosing by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.audiobook_your_library), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            count?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
        TextButton(onClick = { choosing = true }) {
            Icon(painterResource(R.drawable.sort), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.audiobook_sort))
        }
    }
    if (choosing) {
        AudiobookSortDialog(sortOptions, sort, onSelect = { onSort(it); choosing = false }, onDismiss = { choosing = false })
    }
}

@Composable
internal fun AudiobookSortDialog(
    options: List<AudiobookSort>,
    selected: AudiobookSort,
    onSelect: (AudiobookSort) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audiobook_library_sort)) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(option.labelRes))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

/** The Library tab's Continue listening card: one book, its progress and a play/pause button. */
@Composable
internal fun ContinueCard(
    coverUrl: String?,
    title: String,
    author: String?,
    fraction: Float?,
    detail: String,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            stringResource(R.string.abs_continue_listening),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
                CoverArt(coverUrl, Modifier.size(84.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    author?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (fraction != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f).height(4.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (detail.isNotBlank()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                Spacer(Modifier.width(10.dp))
                FilledIconButton(onClick = onPlay, modifier = Modifier.size(52.dp)) {
                    Icon(
                        painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(if (isPlaying) R.string.audiobook_pause else R.string.audiobook_start),
                    )
                }
            }
        }
    }
}

/** What the corner of a grid cover shows. */
internal sealed interface GridBadge {
    data class Progress(val percent: Int) : GridBadge
    data object Downloaded : GridBadge
    data object Finished : GridBadge
}

/** Two covers per row inside a LazyColumn. [key] must be unique within the list. */
internal fun <T> LazyListScope.bookGrid(items: List<T>, key: (T) -> String, cell: @Composable (T) -> Unit) {
    items(items.chunked(2), key = { row -> "grid:${key(row.first())}" }) { row ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun GridBook(coverUrl: String?, title: String, author: String?, badge: GridBadge?, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.BottomEnd) {
            CoverArt(coverUrl, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(12.dp))
            badge?.let { Badge(it) }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 6.dp),
        )
        author?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun Badge(badge: GridBadge) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(6.dp),
    ) {
        when (badge) {
            is GridBadge.Progress -> Text(
                "${badge.percent}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
            GridBadge.Downloaded -> Icon(
                painterResource(R.drawable.download),
                contentDescription = stringResource(R.string.abs_downloaded),
                modifier = Modifier.padding(4.dp).size(16.dp),
            )
            GridBadge.Finished -> Icon(
                painterResource(R.drawable.check_circle),
                contentDescription = stringResource(R.string.audiobook_finished),
                modifier = Modifier.padding(4.dp).size(16.dp),
            )
        }
    }
}

@Composable
private fun CoverArt(url: String?, modifier: Modifier, shape: RoundedCornerShape) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Icon(painterResource(R.drawable.audiobook), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun TabMessage(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    )
}
