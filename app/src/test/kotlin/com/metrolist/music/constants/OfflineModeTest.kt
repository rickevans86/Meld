package com.metrolist.music.constants

import org.junit.Assert.*
import org.junit.Test

class OfflineModeTest {
    @Test fun `offline mode hides remote destinations and their deep links`() {
        listOf("home", "home_alt", "search_input", "library", "news", "radio", "subsonic", "media_server", "recognition", "history", "album/remote")
            .forEach { assertTrue(it, offlineHidesRoute(it)) }
    }
    @Test fun `offline mode retains local destinations and settings`() {
        listOf("podcasts", "audiobooks", "local_files", "settings", "settings/features", "stats")
            .forEach { assertFalse(it, offlineHidesRoute(it)) }
        assertFalse(offlineHidesRoute(null))
    }
}
