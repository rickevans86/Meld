package com.metrolist.music.playback

import android.app.Application
import android.os.Looper
import androidx.media3.session.legacy.PlaybackStateCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import com.metrolist.music.R
import com.metrolist.music.constants.MediaSessionConstants.CommandAddToTargetPlaylist
import com.metrolist.music.constants.MediaSessionConstants.CommandToggleLike
import org.junit.Assert.*
import org.robolectric.util.ReflectionHelpers
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioCompactControlsTest {
    @Test fun `radio exposes chosen custom action instead of previous and restores music controls`() {
        val context = RuntimeEnvironment.getApplication()
        val player = ExoPlayer.Builder(context).build()
        var skip: Pair<Int, Int>? = null
        val wrapper = RadioMetadataPlayer(player, { null }, { skip })
        val session = MediaSession.Builder(context, wrapper).build()
        // Inspect Media3's legacy state builder directly: Robolectric's platform controller has no binder state.
        val impl: Any = ReflectionHelpers.getField(session, "impl")
        val legacy: Any = ReflectionHelpers.getField(impl, "sessionLegacyStub")
        // The service grants these commands to its notification controller on connection.
        val permissions = androidx.media3.session.SessionCommands.Builder()
            .add(CommandToggleLike).add(CommandAddToTargetPlaylist).build()
        legacy.javaClass.getDeclaredMethod("setAvailableCommands", permissions.javaClass, Player.Commands::class.java)
            .apply { isAccessible = true }.invoke(legacy, permissions, Player.Commands.Builder().addAllCommands().build())
        val favourite = CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
            .setDisplayName("Favourite").setIconResId(R.drawable.ic_heart_outline)
            .setSessionCommand(CommandToggleLike).build()
        val add = CommandButton.Builder(CommandButton.ICON_PLAYLIST_ADD)
            .setDisplayName("Add song").setIconResId(R.drawable.playlist_add)
            .setSessionCommand(CommandAddToTargetPlaylist).build()
        fun state() : PlaybackStateCompat {
            wrapper.notifyAvailableCommandsChanged()
            shadowOf(Looper.getMainLooper()).idle()
            val sessionPlayer: Any = ReflectionHelpers.getField(impl, "playerWrapper")
            val method = legacy.javaClass.getDeclaredMethod("createPlaybackStateCompat", sessionPlayer.javaClass)
            method.isAccessible = true
            return method.invoke(legacy, sessionPlayer) as PlaybackStateCompat
        }
        try {
            player.setMediaItems(listOf("music:first", "radio:station", "music:last").map {
                MediaItem.Builder().setMediaId(it).setUri("https://example.com/audio").build()
            }, 1, 0)
            session.setCustomLayout(RadioMediaButtons.order(listOf(favourite, add), true, true))
            var state = state()
            assertEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            assertNotEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            assertEquals(CommandAddToTargetPlaylist.customAction, state.customActions.first().action)
            assertTrue(state.customActions.any { it.action == CommandToggleLike.customAction })
            assertFalse(wrapper.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            assertFalse(wrapper.availableCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))

            session.setCustomLayout(RadioMediaButtons.order(listOf(favourite, add), true, false))
            state = state()
            assertEquals(CommandToggleLike.customAction, state.customActions.first().action)
            assertEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)

            player.seekToNextMediaItem()
            state = state()
            assertNotEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            assertTrue(wrapper.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            skip = 15 to 30
            state = state()
            assertEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
            assertEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
            skip = null
            state = state()
            assertNotEquals(0L, state.actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        } finally {
            session.release()
            player.release()
        }
    }
}
