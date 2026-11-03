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

    companion object {
        // Audiobooks, Together and the rest live in the side menu (WavelengthDrawer)
        val MainScreens = listOf(Home, Search, Podcasts, News, Library)
    }
}
