package com.metrolist.music.playback

import com.metrolist.music.podcasts.markedPodcastPlaylist
import org.junit.Assert.*
import org.junit.Test

class PlaylistAutoDownloadIdentityTest {
    @Test fun `online and saved YouTube playlist actions use the same key while old local IDs remain aliases`() {
        val canonical = PlaylistAutoDownload.sourceId("local-id", "VLPL123")
        assertEquals("youtube:PL123", canonical)
        assertEquals(canonical, PlaylistAutoDownload.sourceId(null, "PL123"))
        assertEquals("liked", PlaylistAutoDownload.sourceId(null, "VLLM"))
        assertEquals("local-only", PlaylistAutoDownload.sourceId("local-only", null))
        val old = setOf("local-id", "another-list", "spotify:abc")
        val enabled = PlaylistAutoDownload.enabledIds(old, canonical, true, setOf("local-id"))
        assertEquals(setOf(canonical, "another-list", "spotify:abc"), enabled)
        assertEquals(setOf("another-list", "spotify:abc"),
            PlaylistAutoDownload.enabledIds(enabled + "local-id", canonical, false, setOf("local-id")))
    }

    @Test fun `podcast menu recognition handles local and online playlist aliases without confusing other lists`() {
        val selected = listOf("saved-show", "yt:VLPL123\tMy show\timage")
        assertTrue(markedPodcastPlaylist(selected, "saved-show", null))
        assertTrue(markedPodcastPlaylist(selected, null, "PL123"))
        assertTrue(markedPodcastPlaylist(selected, null, "VLPL123"))
        assertFalse(markedPodcastPlaylist(selected, "another", "PL999"))
    }
}
