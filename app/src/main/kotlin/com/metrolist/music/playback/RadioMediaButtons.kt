package com.metrolist.music.playback

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.session.CommandButton
import androidx.media3.common.Player
import com.metrolist.music.R
import com.metrolist.music.constants.isRadioId
import com.metrolist.music.constants.MediaSessionConstants.CommandRadioPreviousStation
import com.metrolist.music.constants.MediaSessionConstants.CommandAddToTargetPlaylist

internal object RadioMediaButtons {
    val AddSongInMiniPlayerKey = booleanPreferencesKey("radioAutoMiniPlayerAddSong")

    fun previousStationButton(player: Player, label: String): CommandButton? {
        if (!isRadioId(player.currentMediaItem?.mediaId)) return null
        return CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName(label)
            .setIconResId(R.drawable.skip_previous)
            .setSessionCommand(CommandRadioPreviousStation)
            .setEnabled(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
            .build()
    }

    /** Use the underlying player: the session wrapper intentionally hides native Previous for radio. */
    fun previousStation(player: Player): Boolean {
        if (!isRadioId(player.currentMediaItem?.mediaId) ||
            !player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) return false
        player.seekToPreviousMediaItem()
        return true
    }

    fun order(buttons: List<CommandButton>, isRadio: Boolean, addSongFirst: Boolean): List<CommandButton> =
        if (isRadio && addSongFirst) {
            // Prefer Add in the compact slot freed by RadioMetadataPlayer; retain Favourite in full controls.
            buttons.sortedBy { if (it.sessionCommand == CommandAddToTargetPlaylist) 0 else 1 }
        } else {
            buttons
        }
}
