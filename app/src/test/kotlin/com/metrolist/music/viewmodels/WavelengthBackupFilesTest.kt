/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.viewmodels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavelengthBackupFilesTest {
    @Test fun `every listed file round-trips through its zip entry name`() {
        WavelengthBackupFiles.NAMES.forEach { name ->
            assertEquals(name, WavelengthBackupFiles.fileName(WavelengthBackupFiles.entryName(name)))
        }
    }

    @Test fun `podcast follows and radio stations are backed up`() {
        listOf("podcast_follows.json", "radio_favourites.json", "radio_starred.json").forEach {
            assertEquals(true, it in WavelengthBackupFiles.NAMES)
        }
    }

    @Test fun `other entries and path tricks are not restored`() {
        assertNull(WavelengthBackupFiles.fileName("podcast_follows.json"))
        assertNull(WavelengthBackupFiles.fileName("wavelength/other.json"))
        assertNull(WavelengthBackupFiles.fileName("wavelength/../datastore/settings.preferences_pb"))
        assertNull(WavelengthBackupFiles.fileName("wavelength/podcast_follows.json/../x"))
        assertNull(WavelengthBackupFiles.fileName("song.db"))
    }
}
