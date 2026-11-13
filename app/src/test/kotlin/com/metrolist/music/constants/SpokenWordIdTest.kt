/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.constants

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenWordIdTest {
    @Test fun `audiobooks and podcasts are spoken word, music and others are not`() {
        assertEquals(true, isSpokenWordId("audiobook:abs:item42:0"))
        assertEquals(true, isSpokenWordId("audiobook:local-book"))
        assertEquals(true, isSpokenWordId("podcast:abc123"))
        assertEquals(false, isSpokenWordId("dQw4w9WgXcQ"))     // a YouTube song id
        assertEquals(false, isSpokenWordId("local:12"))
        assertEquals(false, isSpokenWordId("subsonic:99"))
        assertEquals(false, isSpokenWordId("radio:5"))
        assertEquals(false, isSpokenWordId(null))
    }
}
