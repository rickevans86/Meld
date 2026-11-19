package com.metrolist.music.playback

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import com.metrolist.music.podcasts.PodcastBuffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PodcastLoadControlTest {
    private fun params(bufferUs: Long, duration: Long = 900_000_000L, rebuffer: Boolean = false): LoadControl.Parameters {
        val timeline = SinglePeriodTimeline(duration, true, false, false, null, MediaItem.fromUri("https://example.test/episode"))
        return LoadControl.Parameters(PlayerId.UNSET, timeline, MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
            0, bufferUs, 1f, true, rebuffer, C.TIME_UNSET, C.TIME_UNSET)
    }
    @Test fun `two minute default waits for required media time and supports changed settings`() {
        var minutes = 2
        val control = PodcastLoadControl({ true }, { minutes }); control.onPrepared(PlayerId.UNSET)
        assertFalse(control.shouldStartPlayback(params(119_999_999)))
        assertTrue(control.shouldStartPlayback(params(120_000_000)))
        assertTrue(control.shouldContinueLoading(params(119_999_999)))
        minutes = 4
        assertFalse(control.shouldStartPlayback(params(120_000_000)))
        assertTrue(control.shouldStartPlayback(params(240_000_000)))
        assertFalse(control.shouldContinueLoading(params(271_000_000)))
    }
    @Test fun `short episode starts when fully buffered and recovery keeps the short threshold`() {
        val control = PodcastLoadControl({ true }, { 2 }); control.onPrepared(PlayerId.UNSET)
        assertFalse(control.shouldStartPlayback(params(29_000_000, 30_000_000)))
        assertTrue(control.shouldStartPlayback(params(30_000_000, 30_000_000)))
        assertTrue(control.shouldStartPlayback(params(2_000_000, rebuffer = true)))
    }
    @Test fun `music and downloaded or local items use original startup policy`() {
        val control = PodcastLoadControl({ false }, { 20 }); control.onPrepared(PlayerId.UNSET)
        assertTrue(control.shouldStartPlayback(params(750_000)))
    }
    @Test fun `settings retain two minute increments and validate restored preferences`() {
        assertEquals(2, PodcastBuffer.DEFAULT_MINUTES)
        assertEquals((2..20 step 2).toList(), PodcastBuffer.choices)
        assertEquals(2, PodcastBuffer.minutes(-1)); assertEquals(4, PodcastBuffer.minutes(5))
        assertEquals(20, PodcastBuffer.minutes(Int.MAX_VALUE))
    }
    @Test fun `ordinary YouTube videos in selected podcast playlists get the same buffer`() {
        val selected = listOf("yt:PLshow\tShow\thttps://example.test/art")
        assertTrue(PodcastBuffer.isPodcast("video", false, "VLPLshow", selected, emptySet(), true))
        assertTrue(PodcastBuffer.isPodcast("video", false, null, selected, setOf("video"), true))
        assertTrue(PodcastBuffer.isPodcast("video", false, "PLlocal", listOf("local-id"), setOf("PLlocal"), true))
        assertTrue(PodcastBuffer.isPodcast("video", false, "PLshow", selected, emptySet(), false))
        assertFalse(PodcastBuffer.isPodcast("music", false, "PLmusic", selected, emptySet(), true))
        assertTrue(PodcastBuffer.isPodcast("podcast:rss", false, null, emptyList(), emptySet(), true))
        assertTrue(PodcastBuffer.isPodcast("youtube-episode", true, null, emptyList(), emptySet(), true))
    }

    @Test fun `real player creation and release support all startup callbacks`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        for (podcast in listOf(false, true)) {
            val control = PodcastLoadControl({ podcast }, { 2 })
            assertEquals(0L, control.getBackBufferDurationUs(PlayerId.UNSET))
            assertFalse(control.retainBackBufferFromKeyframe(PlayerId.UNSET))
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context).setLoadControl(control).build()
            try { assertEquals(androidx.media3.common.Player.STATE_IDLE, player.playbackState) }
            finally { player.release() }
        }
    }

}
