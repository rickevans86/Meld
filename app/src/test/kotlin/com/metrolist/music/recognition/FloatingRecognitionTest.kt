package com.metrolist.music.recognition

import android.app.Application
import android.content.Intent
import android.os.Looper
import android.widget.LinearLayout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FloatingRecognitionTest {
    @Before fun prepare() { ShadowSettings.setCanDrawOverlays(false); MusicRecognitionService.reset() }
    @After fun clear() { ShadowSettings.setCanDrawOverlays(false) }

    @Test fun `denied overlay permission stops without a window or microphone`() {
        val controller = Robolectric.buildService(FloatingRecognitionService::class.java).create()
        try {
            val service = controller.get()
            assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(Intent(), 0, 1))
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertFalse(FloatingRecognitionService.running.value)
            assertNull(shadowOf(service).lastForegroundNotification)
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedService)
        } finally { controller.destroy() }
    }

    @Test fun `visible shortcut does not listen until tapped and its close control stops it`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(FloatingRecognitionService::class.java).create()
        try {
            val service = controller.get()
            assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(Intent(), 0, 1))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(FloatingRecognitionService.running.value)
            assertNotNull(shadowOf(service).lastForegroundNotification)
            assertTrue(MusicRecognitionService.recognitionStatus.value is com.metrolist.shazamkit.models.RecognitionStatus.Ready)
            val application = RuntimeEnvironment.getApplication()
            assertNull(shadowOf(application).nextStartedService)
            val bubble: LinearLayout = ReflectionHelpers.getField(service, "bubble")
            bubble.getChildAt(0).performClick()
            val launch = shadowOf(application).nextStartedActivity
            assertEquals(RecognitionLaunchActivity::class.java.name, launch.component?.className)
            assertTrue(launch.getBooleanExtra(RecognitionLaunchActivity.EXTRA_FLOATING, false))
            bubble.getChildAt(2).performClick()
            assertTrue(shadowOf(service).isStoppedBySelf)
        } finally { controller.destroy() }
        assertFalse(FloatingRecognitionService.running.value)
    }

    @Test fun `notification hide and foreground startup failure stop cleanly`() {
        ShadowSettings.setCanDrawOverlays(true)
        val controller = Robolectric.buildService(FloatingRecognitionService::class.java).create()
        try {
            val service = controller.get()
            shadowOf(service).setThrowInStartForeground(SecurityException("denied"))
            service.onStartCommand(Intent(), 0, 1)
            assertTrue(shadowOf(service).isStoppedBySelf)
            assertFalse(FloatingRecognitionService.running.value)
            assertEquals(android.app.Service.START_NOT_STICKY, service.onStartCommand(Intent().setAction(FloatingRecognitionService.ACTION_HIDE), 0, 2))
        } finally { controller.destroy() }
    }

    @Test fun `bubble remains within display after drag rotation and oversized layout`() {
        assertEquals(0 to 0, constrainFloatingPosition(-50, -20, 500, 800, 112, 56))
        assertEquals(388 to 744, constrainFloatingPosition(800, 900, 500, 800, 112, 56))
        assertEquals(100 to 200, constrainFloatingPosition(100, 200, 500, 800, 112, 56))
        assertEquals(0 to 0, constrainFloatingPosition(100, 200, 100, 40, 112, 56))
    }
}
