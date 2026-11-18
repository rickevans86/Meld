package com.metrolist.music.playback

import org.junit.Assert.*
import org.junit.Test

class AutoOfflineModeTest {
    @Test fun `saved online browse paths are rejected in offline mode`() {
        listOf("audiobooks/library", "audiobooks/groups/series/0", "podcasts/new", "podcasts/saved",
            "podcasts/channel/show", "radio", "news", "subsonic", "media_server", "playlist")
            .forEach { assertFalse(it, autoOfflineBrowseAllowed(it)) }
    }
    @Test fun `downloads and bounded book pages remain reachable`() {
        listOf(MusicService.ROOT, "podcasts", "podcasts/downloaded", "audiobooks", "audiobooks/downloaded",
            "audiobooks/downloaded/1", "local_files", "local_files/folder/a")
            .forEach { assertTrue(it, autoOfflineBrowseAllowed(it)) }
        assertFalse(autoOfflineBrowseAllowed("audiobooks/downloaded/bad"))
    }
}
