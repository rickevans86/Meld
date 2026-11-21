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
}
