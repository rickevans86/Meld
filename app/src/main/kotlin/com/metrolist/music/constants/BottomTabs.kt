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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.map

/**
 * The bottom bar: up to [MAX_BOTTOM_TABS] screens in the order chosen in Settings → Features.
 * Saved as routes separated by commas; everything is always in the side menu as well.
 */
val BottomTabsKey = stringPreferencesKey("wavelengthBottomTabs")

const val MAX_BOTTOM_TABS = 6

/** The bar's tabs until changed. Search isn't a tab: each tab has its own search button in the top bar. */
val DEFAULT_BOTTOM_TABS = listOf("home", "podcasts", "news", "library")

/** Every screen that can go on the bar, in side-menu order. The second home is named after its source. */
fun bottomTabChoices(alternateIsSpotify: Boolean): List<Screens> = listOf(
    // Main home, named after the default source (the second home is the other one)
    if (alternateIsSpotify) Screens.MainHomeYouTube else Screens.MainHomeSpotify,
    if (alternateIsSpotify) Screens.AltHomeSpotify else Screens.AltHomeYouTube,
    Screens.Library,
    Screens.Podcasts,
    Screens.News,
    Screens.Audiobooks,
    Screens.Radio,
    Screens.Subsonic,
    Screens.MediaServer,
    Screens.LocalFiles,
)

fun parseBottomTabs(saved: String?): List<String> =
    saved?.split(',')?.map { it.trim() }
        // Search was a tab before the top-bar search; it's removed from bars saved back then
        ?.filter { it.isNotEmpty() && it != Screens.Search.route }
        ?.distinct()?.takeIf { it.isNotEmpty() }
        ?: DEFAULT_BOTTOM_TABS

/** The bar's routes in order, as saved (features switched off are removed where it's shown). */
@Composable
fun rememberBottomTabs(): List<String> {
    val context = LocalContext.current
    val flow = remember(context) { context.dataStore.data.map { parseBottomTabs(it[BottomTabsKey]) } }
    val tabs by flow.collectAsState(initial = DEFAULT_BOTTOM_TABS)
    return tabs
}
