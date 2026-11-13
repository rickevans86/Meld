/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.audiobooks.AudiobookSort
import com.metrolist.music.audiobooks.AudiobookTabs
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.screens.library.audiobooks.AudiobookSortDialog
import com.metrolist.music.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Wavelength: order of the phone Audiobooks tabs (drag to reorder, saved at once) and the library sort. */
@Composable
fun AudiobookTabSettings() {
    val (raw, save) = rememberPreference(AudiobookTabs.OrderKey, "")
    var tabs by remember(raw) { mutableStateOf(AudiobookTabs.decode(raw)) }
    val (sortRaw, saveSort) = rememberPreference(AudiobookTabs.SortKey, AudiobookSort.TITLE.id)
    val sort = AudiobookTabs.sort(sortRaw)
    var choosingSort by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in tabs.indices && to.index in tabs.indices) {
            tabs = tabs.toMutableList().apply { add(to.index, removeAt(from.index)) }
            save(AudiobookTabs.encode(tabs))
        }
    }
    Material3SettingsGroup(
        title = stringResource(R.string.audiobook_tab_order),
        items = listOf(Material3SettingsItem(
            title = {}, description = { Text(stringResource(R.string.audiobook_tab_order_desc)) }, onClick = null,
        )),
    )
    LazyColumn(state = listState, userScrollEnabled = false, modifier = Modifier.fillMaxWidth().height((tabs.size * 80).dp)) {
        items(tabs, key = { it.id }) { tab ->
            ReorderableItem(reorderable, key = tab.id) {
                Material3SettingsGroup(items = listOf(Material3SettingsItem(
                    icon = painterResource(tab.iconRes),
                    title = { Text(stringResource(tab.titleRes)) },
                    trailingContent = {
                        Icon(
                            painterResource(R.drawable.drag_handle),
                            stringResource(R.string.auto_audiobook_reorder),
                            Modifier.size(48.dp).longPressDraggableHandle().padding(12.dp),
                        )
                    },
                    onClick = null,
                )))
            }
        }
    }
    Material3SettingsGroup(
        items = listOf(Material3SettingsItem(
            icon = painterResource(R.drawable.sort),
            title = { Text(stringResource(R.string.audiobook_library_sort)) },
            description = { Text(stringResource(sort.labelRes)) },
            onClick = { choosingSort = true },
        )),
    )
    if (choosingSort) {
        AudiobookSortDialog(
            options = AudiobookSort.entries,
            selected = sort,
            onSelect = { saveSort(it.id); choosingSort = false },
            onDismiss = { choosingSort = false },
        )
    }
}
