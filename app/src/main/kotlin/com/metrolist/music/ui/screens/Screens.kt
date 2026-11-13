/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.metrolist.music.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconIdInactive = R.drawable.search,
        iconIdActive = R.drawable.search,
        route = "search_input"
    )

    object ListenTogether : Screens(
        titleId = R.string.together,
        iconIdInactive = R.drawable.group_outlined,
        iconIdActive = R.drawable.group_filled,
        route = "listen_together"
    )

    object Podcasts : Screens(
        titleId = R.string.filter_podcasts,
        iconIdInactive = R.drawable.podcasts,
        iconIdActive = R.drawable.podcasts,
        route = "podcasts"
    )

    object News : Screens(
        titleId = R.string.news_tab,
        iconIdInactive = R.drawable.newspaper,
        iconIdActive = R.drawable.newspaper,
        route = "news"
    )

    object Audiobooks : Screens(
        titleId = R.string.filter_audiobooks,
        iconIdInactive = R.drawable.audiobook,
        iconIdActive = R.drawable.audiobook,
        route = "audiobooks"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    // Wavelength screens that can also be put on the bottom bar (Settings → Features → Bottom bar)
    // The main home ("home") as shown on the bottom bar, named after the default source
    object MainHomeYouTube : Screens(R.string.home_youtube, R.drawable.home_outlined, R.drawable.home_filled, "home")
    object MainHomeSpotify : Screens(R.string.home_spotify, R.drawable.spotify, R.drawable.spotify, "home")
    object AltHomeSpotify : Screens(R.string.home_spotify, R.drawable.spotify, R.drawable.spotify, "home_alt")
    object AltHomeYouTube : Screens(R.string.home_youtube, R.drawable.home_outlined, R.drawable.home_filled, "home_alt")
    object Radio : Screens(R.string.radio_tab, R.drawable.radio, R.drawable.radio, "radio")
    object Subsonic : Screens(R.string.subsonic_tab, R.drawable.dns, R.drawable.dns, "subsonic")
    object MediaServer : Screens(R.string.media_server_tab, R.drawable.dns, R.drawable.dns, "media_server")
    object LocalFiles : Screens(R.string.filter_local_files, R.drawable.folder, R.drawable.folder, "local_files")

    companion object {
        // Audiobooks, Together and the rest live in the side menu (WavelengthDrawer)
        val MainScreens = listOf(Home, Search, Podcasts, News, Library)
    }
}
