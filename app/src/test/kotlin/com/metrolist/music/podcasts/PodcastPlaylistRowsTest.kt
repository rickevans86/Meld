package com.metrolist.music.podcasts

import org.junit.Assert.*
import org.junit.Test

class PodcastPlaylistRowsTest {
    @Test fun `metadata totals are used instead of synthetic zero or partial page size`() {
        assertEquals(7, podcastPlaylistEpisodeCount("7 songs", 3, true))
        assertEquals(1234, podcastPlaylistEpisodeCount("1,234 episodes", 50, true))
        assertEquals(7, podcastPlaylistEpisodeCount(null, 7, false))
        assertEquals(0, podcastPlaylistEpisodeCount("0 songs", 0, false))
        assertNull(podcastPlaylistEpisodeCount(null, 0, false))
        assertNull(podcastPlaylistEpisodeCount(null, 50, true))
        assertNull(podcastPlaylistEpisodeCount("1.2K songs", 50, true))
        assertNull(podcastPlaylistEpisodeCount("1.2 K songs", 50, true))
        assertNull(podcastPlaylistEpisodeCount("12 K songs", 50, true))
        assertEquals(1234, podcastPlaylistEpisodeCount("1 234 titres", 50, true))
    }
    @Test fun `phone and auto favourite identities use the actual playlist browse routes`() {
        assertEquals("youtube_playlist/PLshow", PodcastFavourites.playlistId("VLPLshow", true))
        assertEquals("playlist/local-id", PodcastFavourites.playlistId("local-id", false))
        val id = PodcastFavourites.playlistId("PLshow", true)
        assertEquals(listOf(id, "podcasts/channel/other"),
            listOf("podcasts/channel/other", id).podcastFavouritesFirst(setOf(id)) { it })
    }
}
