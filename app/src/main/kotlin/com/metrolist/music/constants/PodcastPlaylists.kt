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

/**
 * A YouTube account playlist that isn't saved in Wavelength (e.g. an unlisted one made in the YouTube app),
 * marked as a podcast. Saved as "yt:<playlist id>\t<title>\t<thumbnail>" so it can be listed offline.
 */
data class YouTubePodcastPlaylist(val id: String, val title: String, val thumbnail: String?)

private const val YT_PREFIX = "yt:"

fun Preferences.youTubePodcastPlaylists(): List<YouTubePodcastPlaylist> =
    podcastPlaylistIds().filter { it.startsWith(YT_PREFIX) }.map { line ->
        val parts = line.removePrefix(YT_PREFIX).split('\t')
        YouTubePodcastPlaylist(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: parts[0], parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }

@Composable
fun rememberYouTubePodcastPlaylists(): List<YouTubePodcastPlaylist> {
    val context = LocalContext.current
    val flow = remember(context) { context.dataStore.data.map { it.youTubePodcastPlaylists() } }
    val items by flow.collectAsState(initial = emptyList())
    return items
}

suspend fun setYouTubePodcastPlaylist(context: Context, playlist: YouTubePodcastPlaylist, isPodcast: Boolean) {
    context.dataStore.edit { prefs ->
        val current = prefs.podcastPlaylistIds().filterNot { it == YT_PREFIX + playlist.id || it.startsWith(YT_PREFIX + playlist.id + "\t") }
        val line = YT_PREFIX + listOf(playlist.id, playlist.title.replace('\t', ' ').replace('\n', ' '), playlist.thumbnail.orEmpty()).joinToString("\t")
        prefs[PodcastPlaylistsKey] = (if (isPodcast) current + line else current).joinToString("\n")
    }
}

/** The playlist id in a YouTube / YouTube Music link ("…list=PL…"), or the text itself if it already looks like one. */
fun youTubePlaylistIdFrom(text: String): String? {
    val trimmed = text.trim()
    Regex("[?&]list=([A-Za-z0-9_-]+)").find(trimmed)?.let { return it.groupValues[1] }
    return trimmed.takeIf { Regex("^(PL|OL|VL|RD|UU|FL|LL)[A-Za-z0-9_-]{8,}$").matches(it) }?.removePrefix("VL")
}
