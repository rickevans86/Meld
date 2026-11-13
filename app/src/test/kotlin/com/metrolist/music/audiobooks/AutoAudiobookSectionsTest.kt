/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.audiobooks

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoAudiobookSectionsTest {
    @Test fun `section order and disabled rows survive saving`() {
        val rows = listOf(AutoAudiobookSection.SERIES to true, AutoAudiobookSection.CONTINUE to false,
            AutoAudiobookSection.BROWSE to true, AutoAudiobookSection.RECENT to false)
        assertEquals(rows, AutoAudiobookSections.decode(AutoAudiobookSections.encode(rows)))
    }
    @Test fun `corrupt and future preferences retain valid choices and append new rows`() {
        val rows = AutoAudiobookSections.decode("recent:false,unknown:true,recent:true,browse:bad,series:true:extra")
        assertEquals(AutoAudiobookSection.RECENT to false, rows.first())
        assertEquals(AutoAudiobookSection.entries.size, rows.size)
        assertEquals(AutoAudiobookSection.entries.toSet(), rows.map { it.first }.toSet())
    }
    @Test fun `first installation enables the default scrolling page`() {
        assertEquals(AutoAudiobookSection.entries.map { it to true }, AutoAudiobookSections.decode(""))
    }
}
