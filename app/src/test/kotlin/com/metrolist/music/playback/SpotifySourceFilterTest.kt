package com.metrolist.music.playback

import com.metrolist.spotify.models.SpotifyTrack
import org.junit.Assert.*
import org.junit.Test

class SpotifySourceFilterTest {
    private fun track(id: String, uri: String? = null) = SpotifyTrack(id = id, name = "Example", artists = emptyList(), uri = uri)
    @Test fun `Spotify history rejects radio YouTube podcast and non track Spotify URIs`() {
        val id = "0123456789abcdefghijkl"
        assertTrue(isSpotifyMusicTrack(track(id)))
        assertTrue(isSpotifyMusicTrack(track("spotify:$id")))
        assertTrue(isSpotifyMusicTrack(track(id, "spotify:track:$id")))
        assertFalse(isSpotifyMusicTrack(track("aBcDefGh123")))
        assertFalse(isSpotifyMusicTrack(track("radio:station")))
        assertFalse(isSpotifyMusicTrack(track("podcast:episode")))
        assertFalse(isSpotifyMusicTrack(track(id, "spotify:episode:$id")))
    }
}
