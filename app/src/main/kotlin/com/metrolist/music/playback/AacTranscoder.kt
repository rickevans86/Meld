/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException

/**
 * Re-encodes any audio file Android can decode (Opus/WebM, Vorbis, MP3, …) to AAC in an
 * .m4a container, using only the platform's built-in codecs.
 */
internal object AacTranscoder {
    private const val TIMEOUT_US = 10_000L
    private const val BIT_RATE = 256_000
    private const val MAX_IDLE_LOOPS = 3_000

    fun transcode(input: File, output: File) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track in ${input.name}")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)

            val dec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder = dec
            dec.configure(inputFormat, null, null, 0)
            dec.start()
            val mux = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux

            val pcm = ArrayDeque<ByteArray>()
            var headOffset = 0
            var sampleRate = 0
            var channels = 0
            var framesQueued = 0L
            var muxerTrack = -1
            var extractorDone = false
            var decoderDone = false
            var encoderInputDone = false
            var encoderDone = false
            var idleLoops = 0
            val info = MediaCodec.BufferInfo()

            fun startEncoder(format: MediaFormat) {
                if (encoder != null) return
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                val aac = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
                }
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                    configure(aac, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    start()
                }
            }

            while (!encoderDone) {
                var progressed = false

                if (!extractorDone) {
                    val index = dec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = dec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            dec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            dec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        progressed = true
                    }
                }

                if (!decoderDone && pcm.size < 64) {
                    val index = dec.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            startEncoder(dec.outputFormat)
                            progressed = true
                        }
                        index >= 0 -> {
                            startEncoder(dec.outputFormat)
                            if (info.size > 0) {
                                val buffer = dec.getOutputBuffer(index)!!
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                pcm.addLast(ByteArray(info.size).also { buffer.get(it) })
                            }
                            dec.releaseOutputBuffer(index, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) decoderDone = true
                            progressed = true
                        }
                    }
                }

                val enc = encoder
                if (enc != null && !encoderInputDone && (pcm.isNotEmpty() || decoderDone)) {
                    val index = enc.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = enc.getInputBuffer(index)!!
                        buffer.clear()
                        val timeUs = framesQueued * 1_000_000L / sampleRate
                        if (pcm.isEmpty()) {
                            enc.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encoderInputDone = true
                        } else {
                            var written = 0
                            while (pcm.isNotEmpty() && buffer.remaining() > 0) {
                                val head = pcm.first()
                                val count = minOf(head.size - headOffset, buffer.remaining())
                                buffer.put(head, headOffset, count)
                                headOffset += count
                                written += count
                                if (headOffset == head.size) {
                                    pcm.removeFirst()
                                    headOffset = 0
                                }
                            }
                            framesQueued += written / (2 * channels)
                            enc.queueInputBuffer(index, 0, written, timeUs, 0)
                        }
                        progressed = true
                    }
                }

                if (enc != null) {
                    val index = enc.dequeueOutputBuffer(info, TIMEOUT_US)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            muxerTrack = mux.addTrack(enc.outputFormat)
                            mux.start()
                            muxerStarted = true
                            progressed = true
                        }
                        index >= 0 -> {
                            val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig && info.size > 0 && muxerStarted) {
                                val buffer = enc.getOutputBuffer(index)!!
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                mux.writeSampleData(muxerTrack, buffer, info)
                            }
                            enc.releaseOutputBuffer(index, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encoderDone = true
                            progressed = true
                        }
                    }
                }

                idleLoops = if (progressed) 0 else idleLoops + 1
                if (idleLoops > MAX_IDLE_LOOPS) throw IOException("Audio conversion stalled")
            }
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }
}
