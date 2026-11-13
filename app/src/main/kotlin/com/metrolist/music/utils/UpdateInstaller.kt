/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.metrolist.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState
    data class Downloading(val progress: Float) : UpdateDownloadState
    data object Failed : UpdateDownloadState
}

/** Downloads a release APK inside the app and hands it to Android's installer. */
object UpdateInstaller {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private var job: Job? = null
    private var downloaded: Pair<String, File>? = null

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state = _state.asStateFlow()

    fun start(context: Context, downloadUrl: String) {
        if (job?.isActive == true) return
        val appContext = context.applicationContext
        // Already downloaded, e.g. before the user went to allow installs: go straight to installing
        downloaded?.takeIf { (url, file) -> url == downloadUrl && file.exists() }?.let { (_, file) ->
            install(appContext, file)
            return
        }
        Toast.makeText(appContext, R.string.update_downloading_toast, Toast.LENGTH_SHORT).show()
        job = scope.launch {
            _state.value = UpdateDownloadState.Downloading(0f)
            try {
                val apk = download(appContext, downloadUrl)
                downloaded = downloadUrl to apk
                _state.value = UpdateDownloadState.Idle
                withContext(Dispatchers.Main) { install(appContext, apk) }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Update download failed")
                _state.value = UpdateDownloadState.Failed
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.update_download_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun download(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "update.apk")
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var written = 0L
                    var lastReported = 0f
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = written.toFloat() / total
                            if (progress - lastReported >= 0.01f) {
                                lastReported = progress
                                _state.value = UpdateDownloadState.Downloading(progress)
                            }
                        }
                    }
                }
            }
        }
        target
    }

    private fun install(context: Context, apk: File) {
        // Android asks once for permission to install apps from this source
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(context, R.string.update_allow_installs, Toast.LENGTH_LONG).show()
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private const val TAG = "UpdateInstaller"
}
