package com.metrolist.music.utils

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class SavedErrorLogsIntegrationTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun prepare() {
        context.getSharedPreferences("saved_error_logs", 0).edit().clear().commit()
        SavedErrorLogs.init(context)
        SavedErrorLogs.clear()
    }
    @After fun clean() {
        SavedErrorLogs.setEnabled(false)
        SavedErrorLogs.clear()
    }

    @Test fun `default off then persisted setting and fatal report survive reinitialization`() {
        SavedErrorLogs.record("Crash", IllegalStateException("not captured"), fatal = true)
        assertTrue(SavedErrorLogs.reports().isEmpty())
        SavedErrorLogs.setEnabled(true)
        SavedErrorLogs.init(context)
        assertTrue(SavedErrorLogs.enabled())
        SavedErrorLogs.record("Playback error", IllegalArgumentException("decoder failed"), "Code: 4003\nTitle: Example", fatal = true)
        SavedErrorLogs.init(context)
        val report = SavedErrorLogs.reports().single().text
        assertTrue(report.contains("Wavelength Playback error report"))
        assertTrue(report.contains("Code: 4003"))
        assertTrue(report.contains("decoder failed"))
        assertTrue(report.contains("App:"))
        assertTrue(java.io.File(context.noBackupFilesDir, "error_reports").isDirectory)
        SavedErrorLogs.setEnabled(false)
        SavedErrorLogs.init(context)
        assertFalse(SavedErrorLogs.enabled())
        assertEquals(1, SavedErrorLogs.reports().size)
    }

    @Test fun `crash entry point only saves when local logging is enabled`() {
        CrashReporter.reportFatal(IllegalStateException("disabled"))
        assertTrue(SavedErrorLogs.reports().isEmpty())
        SavedErrorLogs.setEnabled(true)
        CrashReporter.reportFatal(IllegalStateException("local diagnostic"))
        assertTrue(SavedErrorLogs.reports().single().text.contains("local diagnostic"))
        CrashReporter.reportNonFatal(RuntimeException(java.util.concurrent.CancellationException("normal cancellation")))
        assertEquals(1, SavedErrorLogs.reports().size)
        SavedErrorLogs.setEnabled(false)
        CrashReporter.reportFatal(IllegalStateException("disabled again"))
        assertEquals(1, SavedErrorLogs.reports().size)
    }

}
