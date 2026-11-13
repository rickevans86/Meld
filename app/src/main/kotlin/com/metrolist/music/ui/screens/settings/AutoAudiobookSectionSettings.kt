/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.audiobooks.AutoAudiobookSection
import com.metrolist.music.audiobooks.autoTitleRes
import com.metrolist.music.audiobooks.AutoAudiobookSections
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun AutoAudiobookSectionSettings() {
    val (raw, save) = rememberPreference(AutoAudiobookSections.Key, "")
    var sections by remember(raw) { mutableStateOf(AutoAudiobookSections.decode(raw)) }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in sections.indices && to.index in sections.indices) {
            sections = sections.toMutableList().apply { add(to.index, removeAt(from.index)) }
            save(AutoAudiobookSections.encode(sections))
        }
    }
    Material3SettingsGroup(
        title = stringResource(R.string.auto_audiobook_sections),
        items = listOf(Material3SettingsItem(
            title = {}, description = { Text(stringResource(R.string.auto_audiobook_sections_hint)) }, onClick = null,
        )),
    )
    LazyColumn(state = listState, userScrollEnabled = false, modifier = Modifier.fillMaxWidth().height((sections.size * 80).dp)) {
        items(sections, key = { it.first.id }) { (section, enabled) ->
            ReorderableItem(reorderable, key = section.id) {
                Material3SettingsGroup(items = listOf(Material3SettingsItem(
                    title = { Text(stringResource(section.autoTitleRes())) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.drag_handle), stringResource(R.string.auto_audiobook_reorder),
                                Modifier.size(48.dp).longPressDraggableHandle().padding(12.dp))
                            Switch(checked = enabled, onCheckedChange = { checked ->
                                sections = sections.map { if (it.first == section) section to checked else it }
                                save(AutoAudiobookSections.encode(sections))
                            })
                        }
                    },
                    onClick = {
                        sections = sections.map { if (it.first == section) section to !enabled else it }
                        save(AutoAudiobookSections.encode(sections))
                    },
                )))
            }
        }
    }
}
