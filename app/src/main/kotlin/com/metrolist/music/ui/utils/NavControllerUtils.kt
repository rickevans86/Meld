/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.navigation.NavController
import com.metrolist.music.ui.screens.Screens

fun NavController.backToMain() {
    // Any top-level screen counts, since the bottom bar can be changed
    val mainRoutes = Screens.MainScreens.map { it.route } + com.metrolist.music.ui.component.WavelengthDrawer.drawerOnlyRoutes

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
