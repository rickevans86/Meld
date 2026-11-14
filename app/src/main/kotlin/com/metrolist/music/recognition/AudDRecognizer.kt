/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.recognition

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.shazamkit.models.RecognitionResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Wavelength: optional fallback recogniser. When the Shazam path finds nothing and the user has
 * turned this on in Settings and entered their own AudD API token, the recorded clip is sent to
 * AudD (audd.io), which has a different fingerprint database and can catch tracks Shazam misses.
 */
object AudDRecognizer {
    private const val TAG = "AudDRecognizer"
    private const val ENDPOINT = "https://api.audd.io/"

    /** Turn the fallback on; needs [TokenKey] set too. */
    val EnabledKey = booleanPreferencesKey("recognitionAudDFallbackEnabled")

    /** The user's own AudD API token (free from dashboard.audd.io). */
    val TokenKey = stringPreferencesKey("recognitionAudDToken")

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    private val client by lazy {
        HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
        }
    }

    /**
     * Recognises [pcm16] (mono, 16-bit little-endian at [sampleRate]) via AudD. Returns null on no
     * match, a missing token, or any error — the caller just keeps the Shazam "no match" result.
     */
    suspend fun recognize(pcm16: ByteArray, sampleRate: Int, token: String): RecognitionResult? {
        if (token.isBlank() || pcm16.isEmpty()) return null
        val wav = pcm16ToWav(pcm16, sampleRate)
        return try {
            val response = client.post(ENDPOINT) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("api_token", token)
                            append("return", "apple_music,spotify")
                            append(
                                "file", wav,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "audio/wav")
                                    append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter(ContentDisposition.Parameters.FileName, "sample.wav").toString())
                                },
                            )
                        },
                    ),
                )
            }
            if (!response.status.isSuccess()) {
                Timber.tag(TAG).w("AudD HTTP ${response.status}")
                return null
            }
            val parsed = json.decodeFromString<AudDResponse>(response.body())
            if (parsed.status != "success" || parsed.result == null) {
                parsed.error?.let { Timber.tag(TAG).w("AudD error ${it.errorCode}: ${it.errorMessage}") }
                return null
            }
            parsed.result.toRecognitionResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "AudD request failed")
            null
        }
    }

    private fun AudDResult.toRecognitionResult(): RecognitionResult? {
        val title = title?.takeIf { it.isNotBlank() } ?: return null
        val artist = artist?.takeIf { it.isNotBlank() } ?: return null
        val isrc = appleMusic?.isrc
        val cover = spotify?.album?.images?.firstOrNull()?.url
            ?: appleMusic?.artwork?.url?.replace("{w}", "544")?.replace("{h}", "544")
        return RecognitionResult(
            trackId = isrc ?: "audd:$artist-$title",
            title = title,
            artist = artist,
            album = album,
            coverArtUrl = cover,
            coverArtHqUrl = cover,
            genre = appleMusic?.genreNames?.firstOrNull(),
            releaseDate = releaseDate,
            label = label,
            lyrics = null,
            shazamUrl = songLink,
            appleMusicUrl = appleMusic?.url,
            spotifyUrl = spotify?.externalUrls?.spotify,
            isrc = isrc,
        )
    }

    /** Wraps raw PCM in a minimal WAV container so AudD accepts it as an audio file. */
    private fun pcm16ToWav(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val byteRate = sampleRate * channels * 2
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun str(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun i32(v: Int) { out.write(v); out.write(v shr 8); out.write(v shr 16); out.write(v shr 24) }
        fun i16(v: Int) { out.write(v); out.write(v shr 8) }
        str("RIFF"); i32(36 + pcm.size); str("WAVE")
        str("fmt "); i32(16); i16(1); i16(channels); i32(sampleRate); i32(byteRate); i16(channels * 2); i16(16)
        str("data"); i32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}

@Serializable
private data class AudDResponse(
    val status: String? = null,
    val result: AudDResult? = null,
    val error: AudDError? = null,
)

@Serializable
private data class AudDError(
    @SerialName("error_code") val errorCode: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
private data class AudDResult(
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val label: String? = null,
    @SerialName("song_link") val songLink: String? = null,
    @SerialName("apple_music") val appleMusic: AudDApple? = null,
    val spotify: AudDSpotify? = null,
)

@Serializable
private data class AudDApple(
    val url: String? = null,
    val isrc: String? = null,
    val artwork: AudDArtwork? = null,
    val genreNames: List<String>? = null,
)

@Serializable
private data class AudDArtwork(val url: String? = null)

@Serializable
private data class AudDSpotify(
    @SerialName("external_urls") val externalUrls: AudDExternalUrls? = null,
    val album: AudDSpotifyAlbum? = null,
)

@Serializable
private data class AudDExternalUrls(val spotify: String? = null)

@Serializable
private data class AudDSpotifyAlbum(val images: List<AudDImage>? = null)

@Serializable
private data class AudDImage(val url: String? = null)
