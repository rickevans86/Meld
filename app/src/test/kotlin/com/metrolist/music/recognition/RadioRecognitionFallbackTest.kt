package com.metrolist.music.recognition

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RadioRecognitionFallbackTest {
    @Test fun `metadata wins and missing unknown unmatched or failed lookup recognises once`() = runBlocking {
        var calls = 0
        suspend fun resolve(text: String?, found: String?) = RecognisedSongs.radioSongWithFallback(text,
            metadata = { found }, recognize = { calls++; "recognised" })
        assertEquals("metadata", resolve("Artist - Song", "metadata"))
        assertEquals(0, calls)
        assertEquals("recognised", resolve(null, "must not use"))
        assertEquals("recognised", resolve("Unknown - Unknown", "must not use"))
        assertEquals("recognised", resolve("not found", null))
        assertEquals("recognised", RecognisedSongs.radioSongWithFallback("Artist - Song",
            metadata = { error("search unavailable") }, recognize = { calls++; "recognised" }))
        assertEquals(4, calls)
        assertNull(RecognisedSongs.radioSongWithFallback<String>(null, { "wrong" }, { null }))
        assertNull(RecognisedSongs.radioSongWithFallback<String>(null, { "wrong" }, { error("recognition unavailable") }))
    }

    @Test fun `cancellation never starts recognition and unrelated songs do not qualify as metadata matches`() = runBlocking {
        var recognised = false
        try {
            RecognisedSongs.radioSongWithFallback<String>("Artist - Song", { throw CancellationException() }, {
                recognised = true; null
            })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertFalse(recognised)
        assertFalse(RecognisedSongs.sameSong("Another song", listOf("Artist"), "Song", "Artist"))
        assertFalse(RecognisedSongs.sameSong("Song", listOf("Wrong artist"), "Song", "Artist Name"))
        assertTrue(RecognisedSongs.sameSong("Song (Official Audio)", listOf("Artist"), "Song", "Artist"))
    }

    @Test fun `capture passes audio unchanged downmixes and retains only the latest twelve seconds`() {
        val capture = RadioAudioCapture()
        capture.setStation("station")
        capture.configure(AudioProcessor.AudioFormat(8000, 2, C.ENCODING_PCM_16BIT))
        capture.flush()
        val input = ByteBuffer.allocateDirect(14 * 8000 * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(14 * 8000) {
            val value = it % 30000
            input.putShort((value - 2).toShort()).putShort((value + 2).toShort())
        }
        input.flip()
        val original = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        capture.queueInput(input)
        val output = capture.output
        val actual = ByteArray(output.remaining()).also { output.get(it) }
        assertArrayEquals(original, actual)
        assertFalse(input.hasRemaining())
        val snapshot = capture.snapshot("station")!!
        assertEquals(12 * 8000 * 2, snapshot.data.size)
        assertEquals(1, snapshot.channelCount)
        assertEquals(8000, snapshot.sampleRate)
        val mono = ByteBuffer.wrap(snapshot.data).order(ByteOrder.LITTLE_ENDIAN)
        repeat(12 * 8000) { assertEquals(((2 * 8000 + it) % 30000).toShort(), mono.short) }
        assertNull(capture.snapshot("another station"))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6))
        assertNull(capture.snapshot("station"))
        capture.setStation("another station")
        assertNull(capture.snapshot("another station"))
    }

    @Test fun `non radio short clips and reset never expose audio`() {
        val capture = RadioAudioCapture()
        capture.configure(AudioProcessor.AudioFormat(8000, 1, C.ENCODING_PCM_16BIT))
        capture.flush()
        capture.queueInput(ByteBuffer.allocateDirect(8000 * 2 * 6))
        assertNull(capture.snapshot("station"))
        capture.setStation("station")
        capture.queueInput(ByteBuffer.allocateDirect(8000 * 2 * 4))
        assertNull(capture.snapshot("station"))
        capture.reset()
        assertNull(capture.snapshot("station"))
    }
}
