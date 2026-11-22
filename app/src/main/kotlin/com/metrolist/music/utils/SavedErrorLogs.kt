package com.metrolist.music.utils

import android.content.Context
import android.os.Build
import com.metrolist.music.BuildConfig
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal data class SavedErrorReport(val name: String, val text: String)

/** Bounded private reports; never log failures of the logger back into itself. */
internal class ErrorLogStore(private val directory: File, enabled: Boolean = false) {
    @Volatile var enabled = enabled
        private set
    private var generation = 0L
    private var sequence = files().firstOrNull()?.name?.substringBefore('-')?.toLongOrNull() ?: 0L

    @Synchronized fun setEnabled(value: Boolean) { enabled = value; generation++ }
    @Synchronized fun generation(): Long = generation

    @Synchronized fun write(text: String, expectedGeneration: Long = generation) {
        if (!enabled || expectedGeneration != generation) return
        runCatching {
            check(directory.isDirectory || directory.mkdirs())
            sequence = maxOf(System.currentTimeMillis(), sequence + 1)
            val name = "${sequence.toString().padStart(19, '0')}-${UUID.randomUUID()}.txt"
            val temporary = File(directory, "$name.tmp")
            try {
                temporary.writeText(redactErrorLog(text).take(MAX_CHARS))
                check(temporary.renameTo(File(directory, name)))
            } finally { temporary.delete() }
            files().drop(MAX_REPORTS).forEach { it.delete() }
        }
    }

    @Synchronized fun reports(): List<SavedErrorReport> = files().mapNotNull { file ->
        runCatching { SavedErrorReport(file.name, file.readText().take(MAX_CHARS)) }.getOrNull()
    }
    @Synchronized fun clear() {
        generation++ // Queued writes from before Clear must not restore deleted reports.
        directory.listFiles()?.forEach { check(it.delete() || !it.exists()) }
    }
    private fun files() = runCatching {
        directory.listFiles()?.filter { it.extension == "txt" }?.sortedByDescending { it.name }.orEmpty()
    }.getOrDefault(emptyList())

    companion object {
        const val MAX_REPORTS = 20
        const val MAX_CHARS = 32_000
    }
}

internal fun redactErrorLog(text: String): String = text
    .replace(Regex("(?i)(?:https?|rtsp)://[^\\s<>]+"), "[URL removed]")
    .replace(Regex("(?i)((?:authorization|cookie|set-cookie|(?:access[_-]?|refresh[_-]?)?token|password|secret|api[_-]?key)\\s*[=:]\\s*)[^\\r\\n]+"), "$1[redacted]")
    .replace(Regex("(?i)Bearer\\s+[^\\s]+"), "Bearer [redacted]")

internal object SavedErrorLogs {
    private var context: Context? = null
    private var store: ErrorLogStore? = null
    private val writer = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(40), { task -> Thread(task, "Wavelength-error-logs").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardPolicy())

    fun init(context: Context) {
        this.context = context.applicationContext
        store = runCatching {
            ErrorLogStore(File(context.noBackupFilesDir, "error_reports"),
                context.getSharedPreferences("saved_error_logs", Context.MODE_PRIVATE).getBoolean("enabled", false))
        }.getOrNull()
    }
    fun enabled() = store?.enabled == true
    fun setEnabled(enabled: Boolean) {
        val ctx = checkNotNull(context)
        checkNotNull(store)
        check(ctx.getSharedPreferences("saved_error_logs", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).commit())
        store?.setEnabled(enabled)
    }
    fun reports() = store?.reports().orEmpty()
    fun clear() = store?.clear()

    fun record(kind: String, error: Throwable, details: String = "", fatal: Boolean = false) {
        val target = store?.takeIf { it.enabled } ?: return
        val generation = target.generation()
        val time = Instant.now()
        val save = Runnable {
            runCatching {
                target.write(buildString {
                    appendLine("Wavelength $kind report")
                    appendLine("Time: $time")
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), ${BuildConfig.ARCHITECTURE}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine(details)
                    append(error.stackTraceToString())
                }, generation)
            }
        }
        if (fatal) save.run() else writer.execute(save)
    }
}
