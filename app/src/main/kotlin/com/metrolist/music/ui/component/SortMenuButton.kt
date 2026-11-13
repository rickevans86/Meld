/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.IconButton as PlainIconButton

/** A sort icon that opens a menu of [options] (value to label) plus "Reverse order". */
@Composable
fun <T> SortMenuButton(
    options: List<Pair<T, String>>,
    selected: T,
    reversed: Boolean,
    onSelect: (T) -> Unit,
    onReversedChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        PlainIconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.sort), contentDescription = stringResource(R.string.sort_by))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = {
                        if (value == selected) {
                            Icon(painterResource(R.drawable.check), contentDescription = null)
                        } else {
                            Box(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        onSelect(value)
                        open = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sort_reverse)) },
                leadingIcon = {
                    if (reversed) Icon(painterResource(R.drawable.check), contentDescription = null) else Box(Modifier.size(24.dp))
                },
                onClick = {
                    onReversedChange(!reversed)
                    open = false
                },
            )
        }
    }
}
