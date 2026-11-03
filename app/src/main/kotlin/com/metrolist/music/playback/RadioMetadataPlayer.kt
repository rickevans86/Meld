/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.util.concurrent.CopyOnWriteArraySet

/**
 * The player the media session (notification, lock screen, Android Auto) sees. For a radio
 * station it shows the song the station says is playing as the title, with the station as the
 * artist; everything else passes straight through.
 */
class RadioMetadataPlayer(
    player: Player,
    private val nowPlaying: () -> Pair<String, String>?,
) : ForwardingPlayer(player) {
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getMediaMetadata(): MediaMetadata {
        val base = super.getMediaMetadata()
        val id = currentMediaItem?.mediaId ?: return base
        val (stationId, song) = nowPlaying() ?: return base
        if (stationId != id) return base
        val station = base.title
        return base.buildUpon()
            .setTitle(song)
            .setDisplayTitle(song)
            .setArtist(station)
            .setSubtitle(station)
            .build()
    }

    /** Call on the main thread when the station's song changes, so the session refreshes. */
    fun notifyMetadataChanged() {
        val metadata = mediaMetadata
        listeners.forEach { it.onMediaMetadataChanged(metadata) }
    }
}
