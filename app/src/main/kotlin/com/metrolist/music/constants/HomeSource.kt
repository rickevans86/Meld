/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.map

/**
 * Home is split in two: a YouTube home and a Spotify home. The default one is the Home tab
 * (route "home"); the other opens from the side menu (route "home_alt") under its own name.
 */
val DefaultHomeSourceKey = stringPreferencesKey("defaultHomeSource")

const val HOME_SOURCE_YOUTUBE = "youtube"
const val HOME_SOURCE_SPOTIFY = "spotify"

/** The default home; people who had "Use Spotify for home" on start with Spotify. */
fun Preferences.defaultHomeSource(): String =
    this[DefaultHomeSourceKey] ?: if (this[UseSpotifyHomeKey] == true) HOME_SOURCE_SPOTIFY else HOME_SOURCE_YOUTUBE

/** Which source a home page shows: the default one, or the other one for the side-menu page. */
fun Preferences.homeSource(isAlternate: Boolean): String {
    val default = defaultHomeSource()
    if (!isAlternate) return default
    return if (default == HOME_SOURCE_SPOTIFY) HOME_SOURCE_YOUTUBE else HOME_SOURCE_SPOTIFY
}

@Composable
fun rememberDefaultHomeSource(): String {
    val context = LocalContext.current
    val flow = remember(context) { context.dataStore.data.map { it.defaultHomeSource() } }
    val source by flow.collectAsState(initial = HOME_SOURCE_YOUTUBE)
    return source
}
