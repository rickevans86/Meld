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
    @Test fun `uncached RSS and other streams cannot use the direct local file path`() {
        listOf("podcast:episode", "radio:station", "subsonic:song", "mediaserver:song", "spotify:song", "youtube")
            .forEach { assertFalse(it, offlineAllowsDirectPlayback(it)) }
        assertTrue(offlineAllowsDirectPlayback("local:file"))
        assertTrue(offlineAllowsDirectPlayback("audiobook:device:chapter"))
        // ABS files still undergo the repository's local-file check before opening.
        assertTrue(offlineAllowsDirectPlayback("audiobook:abs:book:0"))
    }

}
