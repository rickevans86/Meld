/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.audiobooks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.datastore.preferences.core.intPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.utils.rememberPreference

data class AudiobookChapter(val mediaId: String, val title: String, val positionMs: Long)

object AudiobookPlaybackSettings {
    val BackKey = intPreferencesKey("audiobookSkipBackSeconds")
    val ForwardKey = intPreferencesKey("audiobookSkipForwardSeconds")
    const val DEFAULT_BACK = 15
    const val DEFAULT_FORWARD = 30
    val CHOICES = listOf(5, 10, 15, 30, 45, 60, 90, 120)

    fun seconds(value: Int, default: Int): Int = value.takeIf { it in CHOICES } ?: default
}

internal fun audiobookSeekPosition(positionMs: Long, seconds: Int, durationMs: Long): Long =
    (positionMs + seconds * 1000L).coerceIn(0L, durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)

@Composable
fun AudiobookSkipSettings() {
    val (back, setBack) = rememberPreference(AudiobookPlaybackSettings.BackKey, AudiobookPlaybackSettings.DEFAULT_BACK)
    val (forward, setForward) = rememberPreference(AudiobookPlaybackSettings.ForwardKey, AudiobookPlaybackSettings.DEFAULT_FORWARD)
    var pickingForward by remember { mutableStateOf<Boolean?>(null) }
    Material3SettingsGroup(
        title = stringResource(R.string.filter_audiobooks),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.skip_previous),
                title = { Text(stringResource(R.string.audiobook_rewind_time)) },
                description = { Text(stringResource(R.string.audiobook_seconds, AudiobookPlaybackSettings.seconds(back, 15))) },
                onClick = { pickingForward = false },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.skip_next),
                title = { Text(stringResource(R.string.audiobook_forward_time)) },
                description = { Text(stringResource(R.string.audiobook_seconds, AudiobookPlaybackSettings.seconds(forward, 30))) },
                onClick = { pickingForward = true },
            ),
        ),
    )
    pickingForward?.let { isForward ->
        AlertDialog(
            onDismissRequest = { pickingForward = null },
            title = { Text(stringResource(if (isForward) R.string.audiobook_forward_time else R.string.audiobook_rewind_time)) },
            text = {
                Column {
                    AudiobookPlaybackSettings.CHOICES.forEach { seconds ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (isForward) setForward(seconds) else setBack(seconds)
                                pickingForward = null
                            }.padding(vertical = 2.dp),
                        ) {
                            RadioButton(selected = seconds == if (isForward) forward else back, onClick = null)
                            Text(stringResource(R.string.audiobook_seconds, seconds))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingForward = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
