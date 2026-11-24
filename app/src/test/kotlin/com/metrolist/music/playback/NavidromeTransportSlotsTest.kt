package com.metrolist.music.playback

import android.app.Application
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class NavidromeTransportSlotsTest {
    @Test fun `Navidrome retains native controls and reserved positions even when the queue ends`() {
        val player = ExoPlayer.Builder(RuntimeEnvironment.getApplication()).build()
        val wrapper = RadioMetadataPlayer(player, { null })
        val session = MediaSession.Builder(RuntimeEnvironment.getApplication(), wrapper).build()
        try {
            session.setSessionExtras(Bundle().apply { putString("unrelated", "keep") })
            player.setMediaItems(listOf("subsonic:a", "subsonic:b", "subsonic:c").map {
                MediaItem.Builder().setMediaId(it).setUri("https://example.com/audio").build()
            }, 1, 0)
            NavidromeTransportSlots.update(session, player.currentMediaItem?.mediaId)
            assertTrue(wrapper.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            assertTrue(wrapper.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertTrue(session.sessionExtras.getBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV))
            assertTrue(session.sessionExtras.getBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT))
            wrapper.seekToNextMediaItem()
            NavidromeTransportSlots.update(session, player.currentMediaItem?.mediaId)
            assertEquals("subsonic:c", player.currentMediaItem?.mediaId)
            assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            assertTrue(session.sessionExtras.getBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT))
            wrapper.seekToPreviousMediaItem()
            assertEquals("subsonic:b", player.currentMediaItem?.mediaId)
            assertEquals("keep", session.sessionExtras.getString("unrelated"))
            for (id in listOf("radio:station", "music", "podcast:episode", "audiobook:book")) {
                NavidromeTransportSlots.update(session, id)
                assertFalse(session.sessionExtras.containsKey(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV))
                assertFalse(session.sessionExtras.containsKey(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT))
                assertEquals("keep", session.sessionExtras.getString("unrelated"))
            }
            NavidromeTransportSlots.update(session, "subsonic:b")
            NavidromeTransportSlots.update(session, null)
            assertFalse(session.sessionExtras.containsKey(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT))
        } finally { session.release(); player.release() }
    }
}
