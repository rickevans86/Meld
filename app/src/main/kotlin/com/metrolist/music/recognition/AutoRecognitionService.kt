/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.recognition

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import com.metrolist.music.db.entities.RecognitionHistory
import com.metrolist.shazamkit.models.RecognitionStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.util.Locale

/**
 * Keeps listening and recognising songs until switched off (Recognise song screen or Settings →
 * Features → Song recognition → Keep listening, or Stop in its notification). Each new song is saved
 * to recognition history and, if a playlist is set, added to it (no duplicates). Runs as a
 * microphone foreground service with an ongoing notification showing the last song found.
 */
class AutoRecognitionService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var loop: Job? = null
    private var lastKey: String? = null
    private var found = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (loop?.isActive == true) return START_NOT_STICKY
        if (!MusicRecognitionService.hasRecordPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(getString(R.string.auto_recognise_listening)),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            // Android only lets the microphone start while the app is open
            Timber.tag(TAG).w(e, "Couldn't start listening in the background")
            stopSelf()
            return START_NOT_STICKY
        }
        _running.value = true
        loop = scope.launch { listenLoop() }
        return START_NOT_STICKY
    }

    private suspend fun listenLoop() {
        while (scope.isActive) {
            // Don't fight the normal Recognise screen for the microphone
            if (MusicRecognitionService.recognitionStatus.value.let {
                    it is RecognitionStatus.Listening || it is RecognitionStatus.Processing
                }
            ) {
                delay(5_000)
                continue
            }
            val status = MusicRecognitionService.recognize(this)
            when (status) {
                is RecognitionStatus.Success -> {
                    val result = status.result
                    val key = (result.title + "|" + result.artist).lowercase(Locale.ROOT)
                    if (key != lastKey) {
                        lastKey = key
                        found++
                        saveHistory(result)
                        update(getString(R.string.auto_recognise_last, result.title, result.artist, found))
                    }
                    // A song lasts a few minutes, so wait longer after a match
                    delay(MATCH_PAUSE_MS)
                }
                is RecognitionStatus.Error -> {
                    if (!MusicRecognitionService.hasRecordPermission(this)) {
                        stopSelf()
                        return
                    }
                    delay(ERROR_PAUSE_MS)
                }
                else -> delay(NO_MATCH_PAUSE_MS)
            }
            MusicRecognitionService.reset()
        }
    }

    private fun saveHistory(result: com.metrolist.shazamkit.models.RecognitionResult) {
        // Tell the Recognise screen (if open) it's already saved, so it doesn't save it twice
        MusicRecognitionService.resultSavedExternally = true
        scope.launch(Dispatchers.IO) {
            runCatching {
                EntryPointAccessors.fromApplication(applicationContext, RecognitionEntryPoint::class.java).databaseDao().insert(
                    RecognitionHistory(
                        trackId = result.trackId,
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.coverArtUrl,
                        coverArtHqUrl = result.coverArtHqUrl,
                        genre = result.genre,
                        releaseDate = result.releaseDate,
                        label = result.label,
                        shazamUrl = result.shazamUrl,
                        appleMusicUrl = result.appleMusicUrl,
                        spotifyUrl = result.spotifyUrl,
                        isrc = result.isrc,
                        youtubeVideoId = result.youtubeVideoId,
                        recognizedAt = LocalDateTime.now(),
                    ),
                )
            }.onFailure { Timber.tag(TAG).w(it, "Couldn't save recognition history") }
        }
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.mic)
        .setContentTitle(getString(R.string.auto_recognise_title))
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        .addAction(
            0,
            getString(R.string.auto_recognise_stop),
            PendingIntent.getService(
                this,
                1,
                Intent(this, AutoRecognitionService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.auto_recognise_title), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
    }

    override fun onDestroy() {
        loop?.cancel()
        scope.cancel()
        MusicRecognitionService.reset()
        _running.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AutoRecognition"
        private const val CHANNEL_ID = "auto_recognition"
        private const val NOTIFICATION_ID = 7712
        private const val ACTION_STOP = "com.metrolist.music.recognition.AUTO_STOP"
        private const val MATCH_PAUSE_MS = 45_000L
        private const val NO_MATCH_PAUSE_MS = 10_000L
        private const val ERROR_PAUSE_MS = 30_000L

        private val _running = MutableStateFlow(false)

        /** True while keep-listening is on. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, AutoRecognitionService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AutoRecognitionService::class.java).setAction(ACTION_STOP))
        }
    }
}
