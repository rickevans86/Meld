/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import android.os.Bundle
import androidx.media3.session.SessionCommand

object MediaSessionConstants {
    const val ACTION_TOGGLE_LIBRARY = "TOGGLE_LIBRARY"
    const val ACTION_TOGGLE_START_RADIO = "TOGGLE_START_RADIO"
    const val ACTION_TOGGLE_LIKE = "TOGGLE_LIKE"
    const val ACTION_TOGGLE_SHUFFLE = "TOGGLE_SHUFFLE"
    const val ACTION_TOGGLE_REPEAT_MODE = "TOGGLE_REPEAT_MODE"
    const val ACTION_ADD_TO_TARGET_PLAYLIST = "ADD_TO_TARGET_PLAYLIST"
    const val ACTION_RADIO_PREVIOUS_STATION = "RADIO_PREVIOUS_STATION"
    const val ACTION_SKIP_BACK = "SKIP_BACK"
    const val ACTION_SKIP_FORWARD = "SKIP_FORWARD"
    // Dedicated podcast fast-forward / rewind buttons (shown alongside previous/next in Android Auto)
    const val ACTION_SEEK_BACK = "SEEK_BACK"
    const val ACTION_SEEK_FORWARD = "SEEK_FORWARD"

    val CommandToggleLibrary = SessionCommand(ACTION_TOGGLE_LIBRARY, Bundle.EMPTY)
    val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
    val CommandToggleStartRadio = SessionCommand(ACTION_TOGGLE_START_RADIO, Bundle.EMPTY)
    val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val CommandToggleRepeatMode = SessionCommand(ACTION_TOGGLE_REPEAT_MODE, Bundle.EMPTY)
    val CommandAddToTargetPlaylist = SessionCommand(ACTION_ADD_TO_TARGET_PLAYLIST, Bundle.EMPTY)
    val CommandRadioPreviousStation = SessionCommand(ACTION_RADIO_PREVIOUS_STATION, Bundle.EMPTY)
    val CommandSkipBack = SessionCommand(ACTION_SKIP_BACK, Bundle.EMPTY)
    val CommandSkipForward = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)
    val CommandSeekBack = SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY)
    val CommandSeekForward = SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY)

    const val TARGET_PLAYLIST_AUTO = "auto"
}
