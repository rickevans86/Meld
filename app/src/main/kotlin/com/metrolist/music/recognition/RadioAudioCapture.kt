package com.metrolist.music.recognition

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Keeps only the latest 12 seconds of decoded radio audio, in memory, before volume/EQ processing. */
internal class RadioAudioCapture : BaseAudioProcessor() {
    private var stationId: String? = null
    private var sampleRate = 0
    private var channels = 0
    private var supported = false
    private var samples = ShortArray(0)
    private var written = 0L
    private var lastInputMs = 0L

    @Synchronized fun setStation(id: String?) {
        if (stationId == id) return
        stationId = id
        clearCapture()
    }

    @Synchronized override fun onConfigure(format: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = format.sampleRate
        channels = format.channelCount
        supported = format.encoding == C.ENCODING_PCM_16BIT && sampleRate in 8000..192000 && channels in 1..8
        return if (supported) format else AudioProcessor.AudioFormat.NOT_SET
    }

    @Synchronized override fun onFlush() = clearCapture()

    private fun clearCapture() {
        samples = if (stationId != null && supported) ShortArray(sampleRate * 12) else ShortArray(0)
        written = 0
        lastInputMs = 0
    }

    @Synchronized override fun queueInput(inputBuffer: ByteBuffer) {
        if (stationId != null && samples.isNotEmpty()) {
            val input = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            while (input.remaining() >= channels * 2) {
                var sum = 0
                repeat(channels) { sum += input.short.toInt() }
                samples[(written % samples.size).toInt()] = (sum / channels).toShort()
                written++
            }
            lastInputMs = SystemClock.elapsedRealtime()
        }
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    @Synchronized fun snapshot(id: String): DecodedAudio? {
        if (id != stationId || written < sampleRate * 5L || samples.isEmpty() ||
            SystemClock.elapsedRealtime() - lastInputMs > 5000) return null
        val count = minOf(written, samples.size.toLong()).toInt()
        val output = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        val start = written - count
        repeat(count) { output.putShort(samples[((start + it) % samples.size).toInt()]) }
        return DecodedAudio(output.array(), 1, sampleRate, C.ENCODING_PCM_16BIT)
    }

    @Synchronized override fun onReset() {
        stationId = null
        clearCapture()
    }
}
