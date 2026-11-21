package com.metrolist.music.playback

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.media3.session.CommandButton
import com.metrolist.music.constants.MediaSessionConstants.CommandAddToTargetPlaylist

internal object RadioMediaButtons {
    val AddSongInMiniPlayerKey = booleanPreferencesKey("radioAutoMiniPlayerAddSong")

    fun order(buttons: List<CommandButton>, isRadio: Boolean, addSongFirst: Boolean): List<CommandButton> =
        if (isRadio && addSongFirst) {
            // Prefer Add in the compact slot freed by RadioMetadataPlayer; retain Favourite in full controls.
            buttons.sortedBy { if (it.sessionCommand == CommandAddToTargetPlaylist) 0 else 1 }
        } else {
            buttons
        }
}
