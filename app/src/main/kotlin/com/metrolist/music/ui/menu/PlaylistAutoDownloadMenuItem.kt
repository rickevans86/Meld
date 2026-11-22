package com.metrolist.music.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.R
import com.metrolist.music.playback.PlaylistAutoDownload
import com.metrolist.music.ui.component.Material3MenuItemData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun playlistAutoDownloadMenuItem(id: String, aliases: Set<String> = emptySet(), podcast: Boolean = false, onDismiss: () -> Unit): Material3MenuItemData {
    val context = LocalContext.current.applicationContext
    val enabled = PlaylistAutoDownload.rememberEnabledIds()
    val on = id in enabled || aliases.any { it in enabled }
    return Material3MenuItemData(
        title = { Text(stringResource(if (podcast) { if (on) R.string.playlist_auto_episodes_off else R.string.playlist_auto_episodes_on } else { if (on) R.string.playlist_auto_download_off else R.string.playlist_auto_download_on })) },
        description = { Text(stringResource(R.string.playlist_auto_download_details)) },
        icon = { Icon(painterResource(R.drawable.download), contentDescription = null) },
        onClick = {
            CoroutineScope(Dispatchers.IO).launch { PlaylistAutoDownload.setEnabled(context, id, !on, aliases) }
            onDismiss()
        },
    )
}
