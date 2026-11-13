/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.audiobooks.abs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsProgressTest {
    private fun progress(current: Double, duration: Double, serverFraction: Double = 0.0, finished: Boolean = false) =
        AbsProgress("item", current, duration, serverFraction, finished, 0L)

    @Test fun `percent comes from position over duration, not the server fraction`() {
        // 57:12 of a 4h09m book is ~23%, even though the server fraction is 0
        val p = progress(current = 3432.0, duration = 14_940.0, serverFraction = 0.0)
        assertEquals(23, Math.round(p.fractionComplete(14_940.0) * 100).toInt())
    }

    @Test fun `the book duration is preferred but the server duration is a fallback`() {
        assertEquals(0.5, progress(50.0, 0.0).fractionComplete(100.0), 0.0001)   // book duration
        assertEquals(0.25, progress(25.0, 100.0).fractionComplete(0.0), 0.0001)  // fallback to progress' own
        // With no usable duration at all, fall back to the server fraction
        assertEquals(0.4, progress(0.0, 0.0, serverFraction = 0.4).fractionComplete(0.0), 0.0001)
    }

    @Test fun `a book within the last 60 seconds counts as finished`() {
        val duration = 3600.0
        assertFalse(progress(duration - 61, duration).isComplete(duration))
        assertTrue(progress(duration - 60, duration).isComplete(duration))
        assertTrue(progress(duration - 1, duration).isComplete(duration))
        assertTrue(progress(duration, duration).isComplete(duration))
    }

    @Test fun `the server finished flag always wins`() {
        assertTrue(progress(0.0, 3600.0, finished = true).isComplete(3600.0))
    }

    @Test fun `fraction never leaves 0 to 1`() {
        assertEquals(1.0, progress(5000.0, 3600.0).fractionComplete(3600.0), 0.0001)
        assertEquals(0.0, progress(-10.0, 3600.0).fractionComplete(3600.0), 0.0001)
    }
}
