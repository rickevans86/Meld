/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.ui.component.Material3MenuItemData
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Library pins: items kept in a Pins row at the top of the Library, like Spotify's.
 * Keys: "playlist:<id>", "album:<id>", "artist:<id>", "spotify:<playlist id>" ("spotify:liked_songs").
 * Saved in pin order, newest first. Separate from the Home speed dial.
 */
val LibraryPinsKey = stringPreferencesKey("wavelengthLibraryPins")

private const val SEPARATOR = "\n"

@Composable
fun rememberLibraryPins(): List<String> {
    val context = LocalContext.current
    val flow = remember(context) {
        context.dataStore.data.map { prefs -> prefs[LibraryPinsKey]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty() }
    }
    val pins by flow.collectAsState(initial = emptyList())
    return pins
}

suspend fun setLibraryPinned(context: Context, key: String, pinned: Boolean) {
    context.dataStore.edit { prefs ->
        val current = prefs[LibraryPinsKey]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty() - key
        prefs[LibraryPinsKey] = (if (pinned) listOf(key) + current else current).joinToString(SEPARATOR)
    }
}

/** "Pin to top of Library" / "Unpin from Library" for an item's ⋮ menu. */
@Composable
fun libraryPinMenuItem(key: String, onDismiss: () -> Unit): Material3MenuItemData {
    val context = LocalContext.current
    val pinned = key in rememberLibraryPins()
    return Material3MenuItemData(
        title = { Text(stringResource(if (pinned) R.string.library_unpin else R.string.library_pin)) },
        icon = { Icon(painterResource(if (pinned) R.drawable.remove else R.drawable.ic_push_pin), contentDescription = null) },
        onClick = {
            // Not tied to the menu, which closes straight away
            val app = context.applicationContext
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { setLibraryPinned(app, key, !pinned) }
            onDismiss()
        },
    )
}
