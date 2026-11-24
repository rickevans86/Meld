package com.metrolist.music.playback

import android.app.Application
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import com.metrolist.music.constants.MediaSessionConstants.CommandAddToTargetPlaylist
import com.metrolist.music.constants.MediaSessionConstants.CommandToggleLike
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RadioMediaButtonsTest {
    private val favourite = CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
        .setDisplayName("Favourite station").setSessionCommand(CommandToggleLike).build()
    private val add = CommandButton.Builder(CommandButton.ICON_PLAYLIST_ADD)
        .setDisplayName("Add song to playlist").setSessionCommand(CommandAddToTargetPlaylist).build()
    private val next = CommandButton.Builder(CommandButton.ICON_NEXT)
        .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).build()

    @Test fun `radio can prefer the real playlist action while retaining favourite and next commands`() {
        val original = listOf(favourite, next, add)
        val preferred = RadioMediaButtons.order(original, isRadio = true, addSongFirst = true)
        assertSame(add, preferred.first())
        assertEquals(CommandAddToTargetPlaylist, preferred.first().sessionCommand)
        assertEquals(CommandButton.ICON_PLAYLIST_ADD, preferred.first().icon)
        assertEquals(listOf(favourite, next), preferred.drop(1))
        assertEquals(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, preferred.last().playerCommand)
        assertEquals(listOf(favourite, next, add), original)
        assertEquals(listOf(favourite, next, add), RadioMediaButtons.order(original, true, false))
    }

    @Test fun `default and other playback keep their actions and missing playlist buttons are not invented`() {
        val buttons = listOf(favourite, add)
        assertSame(buttons, RadioMediaButtons.order(buttons, true, false))
        assertSame(buttons, RadioMediaButtons.order(buttons, false, true))
        assertSame(buttons, RadioMediaButtons.order(buttons, false, false))
        assertEquals(listOf(favourite), RadioMediaButtons.order(listOf(favourite), true, true))
        assertTrue(RadioMediaButtons.order(emptyList(), true, true).isEmpty())
    }
    @Test fun `custom previous changes station instead of restarting live stream and rejects stale music requests`() {
        val player = androidx.media3.exoplayer.ExoPlayer.Builder(org.robolectric.RuntimeEnvironment.getApplication()).build()
        try {
            player.setMediaItems(listOf("radio:a", "radio:b", "radio:c").map {
                androidx.media3.common.MediaItem.Builder().setMediaId(it).setUri("https://example.com/radio").build()
            }, 1, 30_000)
            val button = requireNotNull(RadioMediaButtons.previousStationButton(player, "Previous station"))
            assertTrue(button.isEnabled)
            assertEquals(com.metrolist.music.constants.MediaSessionConstants.CommandRadioPreviousStation, button.sessionCommand)
            assertEquals(CommandButton.ICON_PREVIOUS, button.icon)
            assertEquals(listOf(add, favourite, button), RadioMediaButtons.order(listOf(favourite, add, button), true, true))
            assertTrue(RadioMediaButtons.previousStation(player))
            assertEquals("radio:a", player.currentMediaItem?.mediaId)
            assertEquals(0L, player.currentPosition)
            assertFalse(requireNotNull(RadioMediaButtons.previousStationButton(player, "Previous station")).isEnabled)
            assertFalse(RadioMediaButtons.previousStation(player))
            player.setMediaItem(androidx.media3.common.MediaItem.Builder().setMediaId("music").setUri("https://example.com/music").build())
            assertNull(RadioMediaButtons.previousStationButton(player, "Previous station"))
            assertFalse(RadioMediaButtons.previousStation(player))
        } finally { player.release() }
    }

}
