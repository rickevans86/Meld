package com.metrolist.music.playback

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.AudioDeviceInfoBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AudioOutputChangeMonitorTest {
    private val context = RuntimeEnvironment.getApplication()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter

    @Test fun `initial devices and input-only changes do not pause but output connection and removal do`() {
        val speaker = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER).build()
        shadowOf(audioManager).setOutputDevices(listOf(speaker))
        var changes = 0
        val monitor = AudioOutputChangeMonitor(audioManager, router) { changes++ }
        monitor.start()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, changes)

        val mic = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BUILTIN_MIC).build()
        shadowOf(audioManager).addInputDevice(mic, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, changes)

        shadowOf(audioManager).removeOutputDevice(speaker, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, changes)
        val bluetooth = AudioDeviceInfoBuilder.newBuilder().setType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP).build()
        shadowOf(audioManager).addOutputDevice(bluetooth, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, changes)

        monitor.stop()
        shadowOf(audioManager).removeOutputDevice(bluetooth, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, changes)
    }

    @Test fun `switching connected routes pauses pending playback and preserves queue and position`() {
        val player = ExoPlayer.Builder(context).build()
        val monitor = AudioOutputChangeMonitor(audioManager, router, player::pause)
        try {
            val item = MediaItem.fromUri("https://example.test/podcast")
            player.setMediaItem(item)
            player.seekTo(12345)
            player.play()
            monitor.start()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(player.playWhenReady)

            shadowOf(router).addBluetoothRoute()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(player.playWhenReady)
            assertEquals(item, player.currentMediaItem)
            assertEquals(12345L, player.currentPosition)

            player.play()
            assertTrue(player.playWhenReady)
            router.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO).requestSetVolume(3)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(player.playWhenReady)
            router.selectRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, router.defaultRoute)
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(player.playWhenReady)
            assertEquals(12345L, player.currentPosition)
        } finally {
            monitor.stop()
            player.release()
        }
    }
}
