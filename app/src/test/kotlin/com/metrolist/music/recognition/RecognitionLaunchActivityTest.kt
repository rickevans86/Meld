package com.metrolist.music.recognition

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.IntentCompat
import com.metrolist.music.MainActivity
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RecognitionLaunchActivityTest {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() { shadowOf(app).clearStartedServices(); shadowOf(app).clearNextStartedActivities() }

    @Test fun `floating tap starts microphone service only after resume and waits for acknowledgement`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(RecognitionLaunchActivity::class.java,
            Intent().putExtra(RecognitionLaunchActivity.EXTRA_FLOATING, true)).create().start()
        try {
            assertNull(shadowOf(app).nextStartedService)
            controller.resume().visible()
            val intent = shadowOf(app).nextStartedService
            assertEquals(RecognitionForegroundService.ACTION_START_FLOATING, intent.action)
            assertFalse(controller.get().isFinishing)
            val ack = IntentCompat.getParcelableExtra(intent, RecognitionForegroundService.EXTRA_START_ACK, ResultReceiver::class.java)
            assertNotNull(ack)
            ack!!.send(0, null)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(controller.get().isFinishing)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun `denied microphone request never starts recognition`() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(RecognitionLaunchActivity::class.java,
            Intent().putExtra(RecognitionLaunchActivity.EXTRA_FLOATING, true)).setup()
        try {
            assertNull(shadowOf(app).nextStartedService)
            controller.get().onRequestPermissionsResult(71, arrayOf(Manifest.permission.RECORD_AUDIO), intArrayOf(PackageManager.PERMISSION_DENIED))
            assertTrue(controller.get().isFinishing)
            assertNull(shadowOf(app).nextStartedService)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun `legacy shortcut without microphone permission still opens the app permission flow`() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(RecognitionLaunchActivity::class.java).setup()
        try {
            val intent = shadowOf(app).nextStartedActivity
            assertEquals(MainActivity.ACTION_RECOGNITION, intent.action)
            assertTrue(intent.getBooleanExtra(MainActivity.EXTRA_AUTO_START_RECOGNITION, false))
            assertTrue(controller.get().isFinishing)
            assertNull(shadowOf(app).nextStartedService)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun `startup acknowledgement timeout releases transparent activity`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val controller = Robolectric.buildActivity(RecognitionLaunchActivity::class.java,
            Intent().putExtra(RecognitionLaunchActivity.EXTRA_FLOATING, true)).setup()
        try {
            assertFalse(controller.get().isFinishing)
            shadowOf(Looper.getMainLooper()).idleFor(3, TimeUnit.SECONDS)
            assertTrue(controller.get().isFinishing)
        } finally { controller.pause().stop().destroy() }
    }
}
