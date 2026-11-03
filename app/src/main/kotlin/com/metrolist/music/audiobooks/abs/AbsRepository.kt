/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.audiobooks.abs

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import com.metrolist.music.constants.ABS_TRACK_ID_PREFIX
import com.metrolist.music.constants.AbsLibraryKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
internal data class AbsTrackInfo(
    val itemId: String,
    val startOffsetSec: Double,
    val bookDurationSec: Double,
    val isLast: Boolean,
)

data class PreparedAbsBook(val book: AbsBook, val songs: List<Song>, val startIndex: Int)

/**
 * Audiobookshelf connection: sign-in, browsing, preparing a book for the player and
 * syncing the listening position back to the server.
 *
 * Each audio file of a book becomes a song with an [ABS_TRACK_ID_PREFIX] id whose path is the
 * server's file URL without credentials; the player adds the Authorization header when it opens it.
 */
@Singleton
class AbsRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val client = AbsClient()
    private val authStore = AbsAuthStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val trackFile = File(context.filesDir, "audiobookshelf_tracks.json")
    private val tracks = ConcurrentHashMap<String, AbsTrackInfo>()
    private val lastReport = ConcurrentHashMap<String, Long>()
    private val pendingReport = ConcurrentHashMap<String, Double>()

    private val _session = MutableStateFlow<AbsSession?>(null)
    val session = _session.asStateFlow()

    /** Set when the server stopped accepting our sign-in and the user needs to sign in again. */
    private val _signInExpired = MutableStateFlow(false)
    val signInExpired = _signInExpired.asStateFlow()

    init {
        _session.value = authStore.load()
        scope.launch { loadTracks() }
    }

    fun selectedLibraryId(): String? = context.dataStore[AbsLibraryKey]

    suspend fun selectLibrary(id: String) {
        context.dataStore.edit { it[AbsLibraryKey] = id }
    }

    suspend fun signIn(server: String, username: String, password: String) {
        val session = client.login(normalizeServer(server), username.trim(), password)
        store(session)
    }

    suspend fun signInWithApiKey(server: String, apiKey: String) {
        val url = normalizeServer(server)
        val key = apiKey.trim()
        val username = client.username(url, key)
        store(AbsSession(url, username, key, refreshToken = null))
    }

    suspend fun signOut() {
        authStore.clear()
        _session.value = null
        _signInExpired.value = false
        context.dataStore.edit { it.remove(AbsLibraryKey) }
    }

    fun coverUrl(itemId: String): String? =
        _session.value?.let { "${it.serverUrl}/api/items/$itemId/cover?token=${Uri.encode(it.accessToken)}" }

    suspend fun libraries() = authed { s -> client.libraries(s.serverUrl, s.accessToken) }

    suspend fun books(libraryId: String, page: Int, limit: Int) =
        authed { s -> client.books(s.serverUrl, s.accessToken, libraryId, page, limit) }

    suspend fun search(libraryId: String, query: String) =
        authed { s -> client.search(s.serverUrl, s.accessToken, libraryId, query) }

    suspend fun inProgress() = authed { s -> client.inProgress(s.serverUrl, s.accessToken) }

    suspend fun allProgress() = authed { s -> client.allProgress(s.serverUrl, s.accessToken) }

    suspend fun book(itemId: String) = authed { s -> client.book(s.serverUrl, s.accessToken, itemId) }

    suspend fun progress(itemId: String) = authed { s -> client.progress(s.serverUrl, s.accessToken, itemId) }

    /**
     * Saves the book's files as songs and positions them so the player starts at [startAtSec],
     * or where the server says the listener stopped when it's null.
     */
    suspend fun prepare(itemId: String, startAtSec: Double?): PreparedAbsBook = withContext(Dispatchers.IO) {
        val session = _session.value ?: throw IllegalStateException("Not signed in")
        val book = book(itemId)
        if (book.tracks.isEmpty()) throw IllegalStateException("This book has no audio files")

        val target = startAtSec ?: progress(itemId)?.takeIf { !it.isFinished }?.currentTimeSec ?: 0.0
        val startIndex = book.tracks.indexOfLast { it.startOffsetSec <= target + 0.5 }.coerceAtLeast(0)
        val cover = cacheCover(itemId)
        val multiFile = book.tracks.size > 1

        book.tracks.forEachIndexed { position, track ->
            val songId = "$ABS_TRACK_ID_PREFIX$itemId:${track.index}"
            tracks[songId] = AbsTrackInfo(itemId, track.startOffsetSec, book.durationSec, position == book.tracks.lastIndex)
            val path = session.serverUrl + track.contentUrl
            val title = if (multiFile) track.title.substringBeforeLast('.').ifBlank { book.title } else book.title
            val existing = database.getSongById(songId)?.song
            with(database) {
                if (existing == null) {
                    upsert(
                        SongEntity(
                            id = songId,
                            title = title,
                            duration = track.durationSec.toInt(),
                            thumbnailUrl = cover,
                            albumName = book.title,
                            isLocal = true,
                            localPath = path,
                        ),
                    )
                    book.author?.let { author ->
                        val artistId = artistByName(author)?.id ?: ArtistEntity(
                            id = ArtistEntity.generateArtistId(),
                            name = author,
                        ).also { insert(it) }.id
                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                    }
                } else if (existing.localPath != path || existing.thumbnailUrl != cover) {
                    upsert(existing.copy(localPath = path, thumbnailUrl = cover ?: existing.thumbnailUrl))
                }
                val positionMs = if (position == startIndex) ((target - track.startOffsetSec) * 1000).toLong() else 0L
                updatePlaybackPosition(songId, positionMs.takeIf { it > 1000 })
            }
        }
        saveTracks()

        val ids = book.tracks.map { "$ABS_TRACK_ID_PREFIX$itemId:${it.index}" }
        val byId = database.getSongsByIds(ids).associateBy { it.id }
        PreparedAbsBook(book, ids.mapNotNull(byId::get), startIndex)
    }

    /**
     * Called by the player whenever it records a position for an Audiobookshelf track.
     * Sends at most one update per [REPORT_INTERVAL_MS] per book; the latest position is always sent.
     */
    fun reportPosition(trackId: String, positionMs: Long) {
        val info = tracks[trackId] ?: return
        val currentTime = info.startOffsetSec + positionMs / 1000.0
        val alreadyQueued = pendingReport.put(info.itemId, currentTime) != null
        if (alreadyQueued) return
        scope.launch {
            val wait = REPORT_INTERVAL_MS - (System.currentTimeMillis() - (lastReport[info.itemId] ?: 0L))
            if (wait > 0) delay(wait)
            val latest = pendingReport.remove(info.itemId) ?: return@launch
            lastReport[info.itemId] = System.currentTimeMillis()
            runCatching {
                authed { s ->
                    client.updateProgress(
                        s.serverUrl, s.accessToken, info.itemId,
                        currentTimeSec = latest,
                        durationSec = info.bookDurationSec,
                        isFinished = false,
                    )
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not sync progress for ${info.itemId}") }
        }
    }

    fun onTrackFinished(trackId: String) {
        val info = tracks[trackId] ?: return
        if (!info.isLast) return
        pendingReport.remove(info.itemId)
        scope.launch {
            runCatching {
                authed { s ->
                    client.updateProgress(
                        s.serverUrl, s.accessToken, info.itemId,
                        currentTimeSec = info.bookDurationSec,
                        durationSec = info.bookDurationSec,
                        isFinished = true,
                    )
                }
            }.onFailure { Timber.tag(TAG).w(it, "Could not mark ${info.itemId} finished") }
        }
    }

    /** Headers the player sends when streaming; refreshes the token first if it is about to expire. */
    fun streamHeaders(): Map<String, String> {
        val current = _session.value ?: return emptyMap()
        val token = if (expiresSoon(current.accessToken) && current.refreshToken != null) {
            runBlocking { runCatching { refreshSession(current) }.getOrNull()?.accessToken } ?: current.accessToken
        } else {
            current.accessToken
        }
        return mapOf("Authorization" to "Bearer $token")
    }

    private suspend fun <T> authed(block: suspend (AbsSession) -> T): T {
        val current = _session.value ?: throw IllegalStateException("Not signed in")
        val session = if (expiresSoon(current.accessToken) && current.refreshToken != null) {
            runCatching { refreshSession(current) }.getOrDefault(current)
        } else {
            current
        }
        return try {
            block(session)
        } catch (e: AbsHttpException) {
            if (e.code != 401) throw e
            val refreshed = session.refreshToken?.let { runCatching { refreshSession(session) }.getOrNull() }
            if (refreshed == null) {
                _signInExpired.value = true
                throw e
            }
            block(refreshed)
        }
    }

    private suspend fun refreshSession(stale: AbsSession): AbsSession = refreshLock.withLock {
        val latest = _session.value ?: stale
        if (latest.accessToken != stale.accessToken) return@withLock latest
        val refreshed = client.refresh(latest.serverUrl, latest.refreshToken ?: error("No refresh token"))
            .copy(username = latest.username)
        store(refreshed)
        refreshed
    }

    private fun store(session: AbsSession) {
        authStore.save(session)
        _session.value = session
        _signInExpired.value = false
    }

    private suspend fun cacheCover(itemId: String): String? {
        val file = File(File(context.cacheDir, "artwork").apply { mkdirs() }, "abs_$itemId.jpg")
        if (file.exists()) return Uri.fromFile(file).toString()
        val session = _session.value ?: return null
        return runCatching {
            file.writeBytes(client.download("${session.serverUrl}/api/items/$itemId/cover", session.accessToken))
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun loadTracks() {
        runCatching {
            if (trackFile.exists()) {
                tracks.putAll(json.decodeFromString(trackMapSerializer, trackFile.readText()))
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not read track list") }
    }

    private fun saveTracks() {
        runCatching { trackFile.writeText(json.encodeToString(trackMapSerializer, HashMap(tracks))) }
            .onFailure { Timber.tag(TAG).w(it, "Could not save track list") }
    }

    /** Reads the JWT expiry; API keys and old tokens without one never count as expiring. */
    private fun expiresSoon(token: String): Boolean = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return false
        val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val exp = json.parseToJsonElement(decoded).jsonObject["exp"]?.jsonPrimitive?.longOrNull ?: return false
        exp * 1000 - System.currentTimeMillis() < 60_000
    }.getOrDefault(false)

    private companion object {
        const val TAG = "Audiobookshelf"
        const val REPORT_INTERVAL_MS = 10_000L
        val trackMapSerializer = MapSerializer(String.serializer(), AbsTrackInfo.serializer())

        fun normalizeServer(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
