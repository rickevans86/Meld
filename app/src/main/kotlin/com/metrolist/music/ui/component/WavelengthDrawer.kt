/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import com.metrolist.music.R
import com.metrolist.music.constants.hidesRoute

/** One entry in the side menu. [topLevel] entries replace the current tab instead of stacking on it. */
data class DrawerDestination(
    val route: String,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val topLevel: Boolean = true,
)

object WavelengthDrawer {
    /** Home is the default source; the other source's home sits under its own name. */
    fun listen(alternateIsSpotify: Boolean) = listOf(
        DrawerDestination("home", R.string.home, R.drawable.home_outlined),
        if (alternateIsSpotify) {
            DrawerDestination(ALT_HOME_ROUTE, R.string.home_spotify, R.drawable.spotify)
        } else {
            DrawerDestination(ALT_HOME_ROUTE, R.string.home_youtube, R.drawable.home_outlined)
        },
        DrawerDestination("search_input", R.string.search, R.drawable.search),
        DrawerDestination("library", R.string.filter_library, R.drawable.library_music_outlined),
    )
    val browse = listOf(
        DrawerDestination("podcasts", R.string.filter_podcasts, R.drawable.podcasts),
        DrawerDestination("news", R.string.news_tab, R.drawable.newspaper),
        DrawerDestination("audiobooks", R.string.filter_audiobooks, R.drawable.audiobook),
        DrawerDestination("radio", R.string.radio_tab, R.drawable.radio),
        DrawerDestination("subsonic", R.string.subsonic_tab, R.drawable.dns),
        DrawerDestination("local_files", R.string.filter_local_files, R.drawable.folder),
    )
    val more = listOf(
        // Listen Together is switched off in Wavelength (see RemovedIntegrations)
        DrawerDestination("history", R.string.history, R.drawable.history, topLevel = false),
        DrawerDestination("stats", R.string.stats, R.drawable.stats, topLevel = false),
        DrawerDestination("settings", R.string.settings, R.drawable.settings, topLevel = false),
    )

    /** Screens only reachable from the side menu; they still show the top and bottom bars. */
    /** Top-level screens: they keep the bottom bar showing whether or not they're on it. */
    val drawerOnlyRoutes = setOf(
        "home", "search_input", "library", "podcasts", "news",
        "audiobooks", "radio", "subsonic", "local_files", "home_alt",
    )

    const val ALT_HOME_ROUTE = "home_alt"
}

@Composable
fun WavelengthDrawerSheet(
    currentRoute: String?,
    onSelect: (DrawerDestination) -> Unit,
) {
    val alternateIsSpotify = com.metrolist.music.constants.rememberDefaultHomeSource() !=
        com.metrolist.music.constants.HOME_SOURCE_SPOTIFY
    val disabled = com.metrolist.music.constants.rememberDisabledFeatures()
    ModalDrawerSheet {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
            )
            listOf(WavelengthDrawer.listen(alternateIsSpotify), WavelengthDrawer.browse, WavelengthDrawer.more).forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
                group.filterNot { disabled.hidesRoute(it.route) }.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(destination.title)) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        selected = currentRoute == destination.route,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
