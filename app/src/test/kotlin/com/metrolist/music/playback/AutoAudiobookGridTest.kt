/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.playback

import android.os.Bundle
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutoAudiobookGridTest {
    @Test fun `grid groups preserve artwork playback and existing metadata without mutating the source`() {
        val original = MediaItem.Builder().setMediaId("audiobooks/shelf/continue/abs/book")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Book").setArtworkUri(Uri.parse("content://covers/book")).setIsPlayable(true)
                .setExtras(Bundle().apply { putBoolean("existing", true) }).build()).build()
        val grouped = autoGridItem(original, "Continue Listening")
        assertEquals("Continue Listening", grouped.mediaMetadata.extras?.getString(AUTO_GROUP_TITLE))
        assertEquals(2, grouped.mediaMetadata.extras?.getInt(AUTO_SINGLE_STYLE))
        assertEquals(true, grouped.mediaMetadata.extras?.getBoolean("existing"))
        assertEquals(original.mediaId, grouped.mediaId)
        assertEquals(original.mediaMetadata.artworkUri, grouped.mediaMetadata.artworkUri)
        assertEquals(true, grouped.mediaMetadata.isPlayable)
        assertNull(original.mediaMetadata.extras?.getString(AUTO_GROUP_TITLE))
    }
    @Test fun `shelf selections resolve to existing resume routes`() {
        assertEquals(listOf("audiobooks", "abs", "book"), autoAudiobookPlaybackPath(listOf("audiobooks", "shelf", "series", "abs", "book")))
        assertEquals(listOf("audiobooks", "device", "book"), autoAudiobookPlaybackPath(listOf("audiobooks", "device", "book")))
        assertNull(autoAudiobookPlaybackPath(listOf("audiobooks", "shelf", "unknown", "abs", "book")))
        assertNull(autoAudiobookPlaybackPath(listOf("audiobooks", "shelf", "recent", "music", "book")))
        assertNull(autoAudiobookPlaybackPath(listOf("audiobooks", "shelf", "recent")))
    }
}
