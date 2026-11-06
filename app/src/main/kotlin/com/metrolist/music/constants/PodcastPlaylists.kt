/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.map

/**
 * Ordinary playlists (e.g. YouTube playlists of a show's episodes) that you've marked as podcasts in
 * Settings → Features → Podcasts. They're listed in the Podcasts tab and Android Auto's Podcasts section.
 * Saved as Wavelength playlist ids, one per line, in the order chosen.
 */
val PodcastPlaylistsKey = stringPreferencesKey("wavelengthPodcastPlaylists")

fun Preferences.podcastPlaylistIds(): List<String> =
    this[PodcastPlaylistsKey]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

@Composable
fun rememberPodcastPlaylistIds(): List<String> {
    val context = LocalContext.current
    val flow = remember(context) { context.dataStore.data.map { it.podcastPlaylistIds() } }
    val ids by flow.collectAsState(initial = emptyList())
    return ids
}

suspend fun setPodcastPlaylist(context: Context, playlistId: String, isPodcast: Boolean) {
    context.dataStore.edit { prefs ->
        val current = prefs.podcastPlaylistIds() - playlistId
        prefs[PodcastPlaylistsKey] = (if (isPodcast) current + playlistId else current).joinToString("\n")
    }
}
