/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * The player the media session (notification, lock screen, Android Auto) sees. For a radio
 * station it shows the song the station says is playing as the title, with the station as the
 * artist; everything else passes straight through.
 *
 * Podcast skip buttons: while [skip] returns (back, forward) seconds, next and previous jump inside
 * the episode, and the session isn't told it can change track, so Android Auto and the notification
 * show the skip buttons from the custom layout in place of next and previous.
 */
class RadioMetadataPlayer(
    player: Player,
    private val nowPlaying: () -> Pair<String, String>?,
    private val skip: () -> Pair<Int, Int>? = { null },
) : ForwardingPlayer(player) {
    /** Each listener added, and the wrapper that filters the commands it's told about. */
    private val listeners = ConcurrentHashMap<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        val wrapper = object : Player.Listener by listener {
            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(filter(availableCommands))
            }
        }
        listeners[listener] = wrapper
        super.addListener(wrapper)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)?.let { super.removeListener(it) }
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
        listeners.keys.forEach { it.onMediaMetadataChanged(metadata) }
    }

    // ---- Podcast skip buttons ----

    private val trackCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    private fun filter(commands: Player.Commands): Player.Commands =
        if (skip() == null) commands else commands.buildUpon().removeAll(*trackCommands).build()

    override fun getAvailableCommands(): Player.Commands = filter(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean =
        if (skip() != null && command in trackCommands) false else super.isCommandAvailable(command)

    /** Call on the main thread when the skip buttons start or stop applying (new track, setting changed). */
    fun notifyAvailableCommandsChanged() {
        val commands = availableCommands
        listeners.keys.forEach { it.onAvailableCommandsChanged(commands) }
    }

    /** Jumps [seconds] inside the current episode (negative goes back). */
    fun skipBy(seconds: Int) {
        val duration = duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        seekTo((currentPosition + seconds * 1000L).coerceIn(0L, duration))
    }

    override fun seekToNext() {
        skip()?.let { skipBy(it.second) } ?: super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        skip()?.let { skipBy(it.second) } ?: super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        skip()?.let { skipBy(-it.first) } ?: super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        skip()?.let { skipBy(-it.first) } ?: super.seekToPreviousMediaItem()
    }
}
