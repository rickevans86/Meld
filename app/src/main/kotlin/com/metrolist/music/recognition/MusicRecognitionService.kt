/**
 * Music Recognition Feature
 *
 * This feature is based on the original MusicRecognizer project by Aleksey Saenko.
 * Original project: https://github.com/aleksey-saenko/MusicRecognizer
 *
 * Special thanks to Aleksey Saenko for the music recognition implementation.
 */

package com.metrolist.music.recognition

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import com.metrolist.music.utils.dataStore
import com.metrolist.shazamkit.Shazam
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.RecognitionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Records from the microphone and identifies the music.
 *
 * Wavelength changes: instead of one fixed 12s capture and a single query, it listens and re-queries
 * Shazam as the audio builds up (at [QUERY_CHECKPOINTS_MS], up to [MAX_LISTEN_MS]), stopping at the
 * first match — the way the Shazam app behaves, which turns many unlucky single-shot misses into
 * hits. It records from the UNPROCESSED source where available (cleaner than the voice-tuned MIC)
 * and normalises the clip's volume before fingerprinting. If everything misses and the AudD fallback
 * is enabled in Settings with a token, the clip is sent there as a last resort.
 */
object MusicRecognitionService {

    private const val RECORDING_SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val TAG = "MusicRecognitionService"

    /** Query Shazam once the recording reaches each of these, in order. */
    private val QUERY_CHECKPOINTS_MS = longArrayOf(5_000, 8_000, 12_000, 16_000, 20_000)
    private const val MAX_LISTEN_MS = 20_000L

    private val _recognitionStatus = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Ready)
    val recognitionStatus: StateFlow<RecognitionStatus> = _recognitionStatus.asStateFlow()

    /**
     * Set to true by the widget service after it has already persisted the result to the
     * database, so that [RecognitionScreen] skips the duplicate insert.
     * Reset to false by [reset].
     */
    var resultSavedExternally: Boolean = false

    fun hasRecordPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Records audio and identifies the track, re-querying as more audio arrives and, if enabled,
     * falling back to AudD. Updates [recognitionStatus] throughout and returns the final status.
     */
    @SuppressLint("MissingPermission")
    suspend fun recognize(context: Context): RecognitionStatus = withContext(Dispatchers.IO) {
        if (!hasRecordPermission(context)) {
            Timber.tag(TAG).w("Microphone permission not granted, aborting recognition")
            return@withContext RecognitionStatus.Error("Microphone permission not granted")
        }

        _recognitionStatus.value = RecognitionStatus.Listening
        Timber.tag(TAG).d("Starting rolling music recognition")

        val pcm = ByteArrayOutputStream()
        var matched: RecognitionResult? = null

        try {
            val bufferSize = AudioRecord.getMinBufferSize(RECORDING_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val audioRecord = createAudioRecord(bufferSize)
            val buffer = ByteArray(bufferSize)
            var nextCheckpoint = 0
            try {
                audioRecord.startRecording()
                val startTime = System.currentTimeMillis()
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (elapsed >= MAX_LISTEN_MS) break
                    val read = audioRecord.read(buffer, 0, bufferSize)
                    if (read > 0) pcm.write(buffer, 0, read)

                    if (nextCheckpoint < QUERY_CHECKPOINTS_MS.size && elapsed >= QUERY_CHECKPOINTS_MS[nextCheckpoint]) {
                        nextCheckpoint++
                        _recognitionStatus.value = RecognitionStatus.Processing
                        matched = fingerprintAndQuery(pcm.toByteArray())
                        if (matched != null) break
                        _recognitionStatus.value = RecognitionStatus.Listening
                    }
                }
            } finally {
                runCatching { audioRecord.stop() }
                audioRecord.release()
            }

            // If the loop ended between checkpoints, try once more with everything captured.
            if (matched == null && isActive) {
                _recognitionStatus.value = RecognitionStatus.Processing
                matched = fingerprintAndQuery(pcm.toByteArray())
            }

            // Last resort: the optional AudD fallback, when enabled with a token.
            if (matched == null && isActive) {
                matched = tryAudDFallback(context, pcm.toByteArray())
            }

            if (matched != null) {
                val result = matched
                Timber.tag(TAG).i("Recognition successful: '%s' by %s", result.title, result.artist)
                _recognitionStatus.value = RecognitionStatus.Success(result)
                RecognisedSongs.onRecognised(context, result)
            } else {
                _recognitionStatus.value = RecognitionStatus.NoMatch("No matches found. Try again with clearer audio.")
            }
            _recognitionStatus.value
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Recognition failed with exception")
            _recognitionStatus.value = RecognitionStatus.Error(e.message ?: "Recognition failed")
            _recognitionStatus.value
        }
    }

    /** Resamples to 16kHz, normalises the volume, fingerprints and queries Shazam. Null on no match/error. */
    private suspend fun fingerprintAndQuery(pcm44k: ByteArray): RecognitionResult? {
        if (pcm44k.size < RECORDING_SAMPLE_RATE) return null // under ~0.25s of audio: nothing to match yet
        val resampled = resampleTo16k(pcm44k) ?: return null
        normalizeGain(resampled)
        if (resampled.isEmpty() || resampled.size % 2 != 0 || ByteOrder.nativeOrder() != ByteOrder.LITTLE_ENDIAN) return null
        val signature = try {
            VibraSignature.fromI16(resampled)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Fingerprint generation failed")
            return null
        }
        val sampleDurationMs = (resampled.size / 2) * 1000L / VibraSignature.REQUIRED_SAMPLE_RATE
        return Shazam.recognize(signature, sampleDurationMs).getOrNull()
    }

    private suspend fun tryAudDFallback(context: Context, pcm44k: ByteArray): RecognitionResult? {
        val prefs = context.dataStore.data.first()
        if (prefs[AudDRecognizer.EnabledKey] != true) return null
        val token = prefs[AudDRecognizer.TokenKey].orEmpty()
        if (token.isBlank()) return null
        val resampled = resampleTo16k(pcm44k) ?: return null
        normalizeGain(resampled)
        _recognitionStatus.value = RecognitionStatus.Processing
        Timber.tag(TAG).d("Shazam found nothing; trying AudD fallback")
        return AudDRecognizer.recognize(resampled, VibraSignature.REQUIRED_SAMPLE_RATE, token)
    }

    private suspend fun resampleTo16k(pcm44k: ByteArray): ByteArray? {
        val decoded = DecodedAudio(
            data = pcm44k,
            channelCount = 1,
            sampleRate = RECORDING_SAMPLE_RATE,
            pcmEncoding = AUDIO_FORMAT,
        )
        return AudioResampler.resample(decoded, VibraSignature.REQUIRED_SAMPLE_RATE)
            .getOrElse {
                Timber.tag(TAG).w(it, "Resampling failed")
                return null
            }
            .takeIf {
                it.channelCount == 1 &&
                    it.sampleRate == VibraSignature.REQUIRED_SAMPLE_RATE &&
                    it.pcmEncoding == AudioFormat.ENCODING_PCM_16BIT
            }
            ?.data
    }

    /** Scales 16-bit PCM so its loudest sample is near full scale, so quiet captures still fingerprint. */
    private fun normalizeGain(data: ByteArray) {
        var peak = 0
        var i = 0
        while (i + 1 < data.size) {
            val sample = ((data[i].toInt() and 0xff) or (data[i + 1].toInt() shl 8)).toShort().toInt()
            val a = abs(sample)
            if (a > peak) peak = a
            i += 2
        }
        if (peak < 150) return // essentially silence: boosting would only amplify noise
        val gain = 30_000.0 / peak
        if (gain <= 1.05) return // already loud enough
        i = 0
        while (i + 1 < data.size) {
            val sample = ((data[i].toInt() and 0xff) or (data[i + 1].toInt() shl 8)).toShort().toInt()
            val scaled = (sample * gain).roundToInt().coerceIn(-32768, 32767)
            data[i] = (scaled and 0xff).toByte()
            data[i + 1] = ((scaled shr 8) and 0xff).toByte()
            i += 2
        }
    }

    /** UNPROCESSED where the device supports it (cleaner for music), else MIC, else voice recognition. */
    @SuppressLint("MissingPermission")
    private fun createAudioRecord(bufferSize: Int): AudioRecord {
        val sources = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.MIC)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }
        for (source in sources) {
            val record = runCatching {
                AudioRecord(source, RECORDING_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
            }.getOrNull()
            if (record != null && record.state == AudioRecord.STATE_INITIALIZED) {
                Timber.tag(TAG).d("Recording from audio source %d", source)
                return record
            }
            record?.release()
        }
        return AudioRecord(MediaRecorder.AudioSource.MIC, RECORDING_SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize)
    }

    fun reset() {
        Timber.tag(TAG).d("Recognition state reset")
        _recognitionStatus.value = RecognitionStatus.Ready
        resultSavedExternally = false
    }
}
