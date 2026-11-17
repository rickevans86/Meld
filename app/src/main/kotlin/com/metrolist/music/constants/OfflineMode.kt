package com.metrolist.music.constants

import androidx.datastore.preferences.core.booleanPreferencesKey

val OfflineModeKey = booleanPreferencesKey("offlineMode")

/** Remote catalogs are hidden; settings and local statistics remain reachable. */
fun offlineHidesRoute(route: String?): Boolean = route != null && route.substringBefore('/') !in
    setOf("podcasts", "audiobooks", "local_files", "settings", "stats")
