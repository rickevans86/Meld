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
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioMetadataPlayer(
    player: Player,
    private val nowPlaying: () -> Pair<String, String>?,
    private val skip: () -> Pair<Int, Int>? = { null },
) : ForwardingPlayer(player) {
    /** Each listener added, and the wrapper that filters the commands it's told about. */
    private val listeners = ConcurrentHashMap<Player.Listener, Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        val wrapper = FilteringListener(listener)
        listeners[listener] = wrapper
        super.addListener(wrapper)
    }

    /**
     * Passes every event on, filtering the available commands. Written out in full: Kotlin's `by`
     * delegation skips Java default methods, so a delegated listener would hear nothing (Android Auto
     * then showed "Tap to open" while playing).
     */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private inner class FilteringListener(private val l: Player.Listener) : Player.Listener {
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) =
            l.onAvailableCommandsChanged(filter(availableCommands))
        override fun onEvents(player: Player, events: Player.Events) = l.onEvents(player, events)
        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) = l.onTimelineChanged(timeline, reason)
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) = l.onMediaItemTransition(mediaItem, reason)
        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) = l.onTracksChanged(tracks)
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = l.onMediaMetadataChanged(mediaMetadata)
        override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) = l.onPlaylistMetadataChanged(mediaMetadata)
        override fun onIsLoadingChanged(isLoading: Boolean) = l.onIsLoadingChanged(isLoading)
        override fun onLoadingChanged(isLoading: Boolean) = l.onLoadingChanged(isLoading)
        override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) =
            l.onTrackSelectionParametersChanged(parameters)
        override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) = l.onPlayerStateChanged(playWhenReady, playbackState)
        override fun onPlaybackStateChanged(playbackState: Int) = l.onPlaybackStateChanged(playbackState)
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = l.onPlayWhenReadyChanged(playWhenReady, reason)
        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) =
            l.onPlaybackSuppressionReasonChanged(playbackSuppressionReason)
        override fun onIsPlayingChanged(isPlaying: Boolean) = l.onIsPlayingChanged(isPlaying)
        override fun onRepeatModeChanged(repeatMode: Int) = l.onRepeatModeChanged(repeatMode)
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = l.onShuffleModeEnabledChanged(shuffleModeEnabled)
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) = l.onPlayerError(error)
        override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) = l.onPlayerErrorChanged(error)
        override fun onPositionDiscontinuity(reason: Int) = l.onPositionDiscontinuity(reason)
        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) =
            l.onPositionDiscontinuity(oldPosition, newPosition, reason)
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) =
            l.onPlaybackParametersChanged(playbackParameters)
        override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) = l.onSeekBackIncrementChanged(seekBackIncrementMs)
        override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) = l.onSeekForwardIncrementChanged(seekForwardIncrementMs)
        override fun onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs: Long) =
            l.onMaxSeekToPreviousPositionChanged(maxSeekToPreviousPositionMs)
        override fun onAudioSessionIdChanged(audioSessionId: Int) = l.onAudioSessionIdChanged(audioSessionId)
        override fun onAudioAttributesChanged(audioAttributes: androidx.media3.common.AudioAttributes) = l.onAudioAttributesChanged(audioAttributes)
        override fun onVolumeChanged(volume: Float) = l.onVolumeChanged(volume)
        override fun onSkipSilenceEnabledChanged(skipSilenceEnabled: Boolean) = l.onSkipSilenceEnabledChanged(skipSilenceEnabled)
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) = l.onDeviceInfoChanged(deviceInfo)
        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = l.onDeviceVolumeChanged(volume, muted)
        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = l.onVideoSizeChanged(videoSize)
        override fun onSurfaceSizeChanged(width: Int, height: Int) = l.onSurfaceSizeChanged(width, height)
        override fun onRenderedFirstFrame() = l.onRenderedFirstFrame()
        override fun onCues(cues: MutableList<androidx.media3.common.text.Cue>) = l.onCues(cues)
        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) = l.onCues(cueGroup)
        override fun onMetadata(metadata: androidx.media3.common.Metadata) = l.onMetadata(metadata)
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
