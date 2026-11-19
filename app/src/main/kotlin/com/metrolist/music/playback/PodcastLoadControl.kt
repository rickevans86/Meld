package com.metrolist.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.metrolist.music.podcasts.PodcastBuffer

/** Both policies share the allocator used by the renderers. Time wins over the audio byte limit. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PodcastLoadControl private constructor(
    private val normal: LoadControl,
    private val podcast: LoadControl,
    private val isPodcast: (MediaItem) -> Boolean,
    private val minutes: () -> Int,
) : LoadControl by normal {
    constructor(isPodcast: (MediaItem) -> Boolean, minutes: () -> Int) : this(
        DefaultAllocator(true, 65_536), isPodcast, minutes)
    private constructor(allocator: DefaultAllocator, classify: (MediaItem) -> Boolean, minutes: () -> Int) : this(
        DefaultLoadControl.Builder().setAllocator(allocator).setBufferDurationsMs(50_000, 50_000, 750, 2_000).build(),
        DefaultLoadControl.Builder().setAllocator(allocator).setBufferDurationsMs(1_230_000, 1_230_000, 120_000, 2_000)
            .setPrioritizeTimeOverSizeThresholds(true).build(), classify, minutes)

    private fun applies(p: LoadControl.Parameters): Boolean {
        if (p.timeline.isEmpty) return false
        val period = p.timeline.getPeriodByUid(p.mediaPeriodId.periodUid, Timeline.Period())
        return isPodcast(p.timeline.getWindow(period.windowIndex, Timeline.Window()).mediaItem)
    }
    override fun shouldContinueLoading(p: LoadControl.Parameters): Boolean =
        if (applies(p)) p.bufferedDurationUs < PodcastBuffer.requiredUs(minutes()) + 30_000_000L && podcast.shouldContinueLoading(p)
        else normal.shouldContinueLoading(p)

    override fun shouldStartPlayback(p: LoadControl.Parameters): Boolean =
        if (!applies(p)) normal.shouldStartPlayback(p)
        else if (p.rebuffering) podcast.shouldStartPlayback(p)
        else {
            val period = p.timeline.getPeriodByUid(p.mediaPeriodId.periodUid, Timeline.Period())
            val remaining = (period.durationUs - p.playbackPositionUs).takeIf { period.durationUs > 0 && it > 0 }
            p.bufferedDurationUs >= minOf(PodcastBuffer.requiredUs(minutes()), remaining ?: Long.MAX_VALUE)
        }

    override fun onPrepared(playerId: PlayerId) { normal.onPrepared(playerId); podcast.onPrepared(playerId) }
    override fun onTracksSelected(p: LoadControl.Parameters, groups: TrackGroupArray, selections: Array<out ExoTrackSelection?>) {
        normal.onTracksSelected(p, groups, selections); podcast.onTracksSelected(p, groups, selections)
    }
    override fun onStopped(playerId: PlayerId) { normal.onStopped(playerId); podcast.onStopped(playerId) }
    override fun onReleased(playerId: PlayerId) { normal.onReleased(playerId); podcast.onReleased(playerId) }
}
