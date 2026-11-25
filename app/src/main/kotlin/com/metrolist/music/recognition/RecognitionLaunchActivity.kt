package com.metrolist.music.recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.metrolist.music.MainActivity

class RecognitionLaunchActivity : Activity() {
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        launching = false
    }

    override fun onPostResume() {
        super.onPostResume()
        handleRecognitionLaunch()
    }

    private val handler by lazy { android.os.Handler(mainLooper) }
    private var launching = false
    private val finishTimeout = Runnable { finish() }

    private fun handleRecognitionLaunch() {
        if (launching) return
        launching = true
        if (hasRecordPermission()) startRecognitionService()
        else if (intent.getBooleanExtra(EXTRA_FLOATING, false)) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 71)
        else { openRecognitionPermissionFlow(); finish() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 71) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && hasRecordPermission()) startRecognitionService()
        else {
            android.widget.Toast.makeText(this, com.metrolist.music.R.string.floating_recognition_mic_needed, android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(finishTimeout)
        super.onDestroy()
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openRecognitionPermissionFlow() {
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_RECOGNITION
                putExtra(MainActivity.EXTRA_AUTO_START_RECOGNITION, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        startActivity(intent)
    }

    companion object { const val EXTRA_FLOATING = "floating_recognition" }

    private fun startRecognitionService() {
        if (!hasRecordPermission()) { finish(); return }

        val serviceIntent = Intent(this, RecognitionForegroundService::class.java).apply {
            if (intent.getBooleanExtra(EXTRA_FLOATING, false)) action = RecognitionForegroundService.ACTION_START_FLOATING
            putExtra(RecognitionForegroundService.EXTRA_START_ACK, object : android.os.ResultReceiver(handler) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) { finish() }
            })
        }

        // Keep this activity visible until microphone FGS startup is acknowledged (Android 14+).
        handler.postDelayed(finishTimeout, 3_000L)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
        } catch (error: RuntimeException) {
            android.widget.Toast.makeText(this, com.metrolist.music.R.string.floating_recognition_start_failed, android.widget.Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
