/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

/** Existing error entry points retain local diagnostics only; sharing is a manual user action. */
object CrashReporter {
    fun reportFatal(throwable: Throwable) {
        SavedErrorLogs.record("Crash", throwable, fatal = true)
    }

    fun reportNonFatal(throwable: Throwable) {
        // Playback failures already include their media context in MusicService.
        if (throwable is androidx.media3.common.PlaybackException ||
            generateSequence(throwable) { it.cause }.take(8).any {
                it is java.util.concurrent.CancellationException
            }) return
        SavedErrorLogs.record("Error", throwable)
    }

    fun reportAnr(mainThreadStack: Array<StackTraceElement>, blockedMs: Long) {
        val error = RuntimeException("Application Not Responding: main thread blocked for ${blockedMs}ms")
            .apply { stackTrace = mainThreadStack }
        SavedErrorLogs.record("App unresponsive", error)
    }
}
