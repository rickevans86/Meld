/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.constants

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeMediaIdTest {
    @Test fun `only plain YouTube ids count as YouTube - every other source is excluded`() {
        assertEquals(true, isYouTubeMediaId("dQw4w9WgXcQ"))   // a YouTube video id
        assertEquals(false, isYouTubeMediaId("audiobook:abs:item42:0"))
        assertEquals(false, isYouTubeMediaId("podcast:abc123"))
        assertEquals(false, isYouTubeMediaId("radio:5"))
        assertEquals(false, isYouTubeMediaId("local:12"))
        assertEquals(false, isYouTubeMediaId("subsonic:99"))
        assertEquals(false, isYouTubeMediaId("mediaserver:7"))
        assertEquals(false, isYouTubeMediaId("spotify:track:xyz"))
    }
}
