package com.metrolist.music.utils

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedErrorLogsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun store(enabled: Boolean = false) = ErrorLogStore(File(temporary.root, "reports"), enabled)

    @Test fun `saving is opt in and disabled writes are discarded`() {
        val store = store()
        store.write("disabled")
        assertTrue(store.reports().isEmpty())
        store.setEnabled(true)
        store.write("saved")
        store.setEnabled(false)
        store.write("not saved")
        assertEquals(listOf("saved"), store.reports().map { it.text })
        assertEquals("saved", store(true).reports().single().text)
    }

    @Test fun `reports are bounded and newest retained across reopen`() {
        val store = store(true)
        repeat(25) { store.write("report $it " + "x".repeat(ErrorLogStore.MAX_CHARS)) }
        val reports = store(true).reports()
        assertEquals(20, reports.size)
        assertTrue(reports.all { it.text.length == ErrorLogStore.MAX_CHARS })
        assertTrue(reports.any { it.text.startsWith("report 24 ") })
        assertFalse(reports.any { it.text.startsWith("report 0 ") })
    }

    @Test fun `clear invalidates pending writes but allows future errors`() {
        val store = store(true)
        val pending = store.generation()
        store.write("before")
        store.clear()
        store.write("queued before clear", pending)
        assertTrue(store.reports().isEmpty())
        store.write("new error")
        assertEquals("new error", store.reports().single().text)
        val generation = store.generation()
        store.setEnabled(false)
        store.setEnabled(true)
        store.write("queued before disable", generation)
        assertEquals(1, store.reports().size)
    }

    @Test fun `private URLs and common credentials are redacted without losing stack`() {
        val result = redactErrorLog("https://server/private?token=abc\nAuthorization: Bearer secret\nCookie=session\naccess_token=secret\npassword: hidden\n at Decoder.render(Decoder.kt:12)")
        assertFalse(result.contains("server"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("session"))
        assertFalse(result.contains("hidden"))
        assertTrue(result.contains("Decoder.render"))
    }

    @Test fun `storage failure is ignored by error capture`() {
        val file = temporary.newFile()
        val store = ErrorLogStore(file, true)
        store.write("error")
        assertTrue(store.reports().isEmpty())
    }
}
