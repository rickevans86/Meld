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
import kotlinx.coroutines.Job
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
    val chapters: List<AbsChapter> = emptyList(),
)

data class PreparedAbsBook(val book: AbsBook, val songs: List<Song>, val startIndex: Int)

@Serializable
internal data class AbsLocalPosition(
    val currentTimeSec: Double,
    val updatedAt: Long,
    val finished: Boolean = false,
)

/** A book saved for offline listening: [progress] runs 0..1 while downloading. */
data class AbsDownloadState(
    val progress: Float,
    val done: Boolean,
    val failed: Boolean = false,
)

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
    private val downloadsDir = File(context.filesDir, "audiobookshelf")
    private val positionsFile = File(context.filesDir, "audiobookshelf_positions.json")
    private val localPositions = ConcurrentHashMap<String, AbsLocalPosition>()
    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, AbsDownloadState>>(emptyMap())
    val downloads = _downloads.asStateFlow()

    private val _session = MutableStateFlow<AbsSession?>(null)
    val session = _session.asStateFlow()

    /** Set when the server stopped accepting our sign-in and the user needs to sign in again. */
    private val _signInExpired = MutableStateFlow(false)
    val signInExpired = _signInExpired.asStateFlow()

    init {
        _session.value = authStore.load()
        scope.launch {
            loadTracks()
            loadPositions()
            _downloads.value = downloadedIds().associateWith { AbsDownloadState(1f, done = true) }
        }
    }

    fun chaptersForTrack(trackId: String?): List<com.metrolist.music.audiobooks.AudiobookChapter> =
        absChapterNavigation(trackId, tracks)

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

    fun coverUrl(itemId: String): String? {
        val cached = coverFile(itemId)
        if (cached.exists()) return Uri.fromFile(cached).toString()
        return _session.value?.let { "${it.serverUrl}/api/items/$itemId/cover?token=${Uri.encode(it.accessToken)}" }
    }

    suspend fun libraries() = authed { s -> client.libraries(s.serverUrl, s.accessToken) }

    suspend fun books(libraryId: String, page: Int, limit: Int) =
        authed { s -> client.books(s.serverUrl, s.accessToken, libraryId, page, limit) }

    suspend fun search(libraryId: String, query: String) =
        authed { s -> client.search(s.serverUrl, s.accessToken, libraryId, query) }

    suspend fun inProgress() = authed { s -> client.inProgress(s.serverUrl, s.accessToken) }

    suspend fun allProgress() = authed { s -> client.allProgress(s.serverUrl, s.accessToken) }

    /** The book from the server, or from its download when the server can't be reached. */
    suspend fun book(itemId: String): AbsBook =
        try {
            authed { s -> client.book(s.serverUrl, s.accessToken, itemId) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (e is AbsHttpException && e.code != 401) throw e
            downloadedBook(itemId) ?: throw e
        }

    suspend fun progress(itemId: String) = authed { s -> client.progress(s.serverUrl, s.accessToken, itemId) }

    /**
     * Saves the book's files as songs and positions them so the player starts at [startAtSec],
     * or where the server says the listener stopped when it's null.
     */
    suspend fun prepare(itemId: String, startAtSec: Double?): PreparedAbsBook = withContext(Dispatchers.IO) {
        val session = _session.value ?: throw IllegalStateException("Not signed in")
        val book = book(itemId)
        if (book.tracks.isEmpty()) throw IllegalStateException("This book has no audio files")

        val target = startAtSec ?: resumePosition(itemId)
        val startIndex = book.tracks.indexOfLast { it.startOffsetSec <= target + 0.5 }.coerceAtLeast(0)
        val cover = cacheCover(itemId)
        val multiFile = book.tracks.size > 1

        book.tracks.forEachIndexed { position, track ->
            val songId = "$ABS_TRACK_ID_PREFIX$itemId:${track.index}"
            tracks[songId] = AbsTrackInfo(itemId, track.startOffsetSec, book.durationSec, position == book.tracks.lastIndex, if (position == 0) book.chapters else emptyList())
            val path = downloadedTrack(itemId, track.index)?.let { Uri.fromFile(it).toString() }
                ?: (session.serverUrl + track.contentUrl)
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
        localPositions[info.itemId] = AbsLocalPosition(currentTime, System.currentTimeMillis())
        val alreadyQueued = pendingReport.put(info.itemId, currentTime) != null
        if (alreadyQueued) return
        scope.launch {
            val wait = REPORT_INTERVAL_MS - (System.currentTimeMillis() - (lastReport[info.itemId] ?: 0L))
            if (wait > 0) delay(wait)
            val latest = pendingReport.remove(info.itemId) ?: return@launch
            lastReport[info.itemId] = System.currentTimeMillis()
            savePositions()
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

    /** Marks a book finished on the server, or back to not started (it then plays from the beginning). */
    suspend fun setFinished(book: AbsBook, finished: Boolean) = withContext(Dispatchers.IO) {
        pendingReport.remove(book.id)
        val position = if (finished) book.durationSec else 0.0
        localPositions[book.id] = AbsLocalPosition(position, System.currentTimeMillis(), finished = finished)
        savePositions()
        authed { s ->
            client.updateProgress(
                s.serverUrl, s.accessToken, book.id,
                currentTimeSec = position,
                durationSec = book.durationSec,
                isFinished = finished,
            )
        }
    }

    fun onTrackFinished(trackId: String) {
        val info = tracks[trackId] ?: return
        if (!info.isLast) return
        pendingReport.remove(info.itemId)
        localPositions[info.itemId] = AbsLocalPosition(info.bookDurationSec, System.currentTimeMillis(), finished = true)
        scope.launch {
            savePositions()
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

    fun download(itemId: String) {
        if (downloadJobs[itemId]?.isActive == true) return
        setDownload(itemId, AbsDownloadState(0f, done = false))
        downloadJobs[itemId] = scope.launch {
            try {
                val book = authed { s -> client.book(s.serverUrl, s.accessToken, itemId) }
                val dir = File(downloadsDir, itemId).apply { mkdirs() }
                File(dir, COMPLETE_MARKER).delete()
                val totalSec = book.tracks.sumOf { it.durationSec }.coerceAtLeast(1.0)
                var doneSec = 0.0
                for (track in book.tracks) {
                    val target = File(dir, trackFileName(track))
                    if (!target.exists()) {
                        val partial = File(dir, target.name + ".part")
                        authed { s ->
                            client.downloadTo(s.serverUrl + track.contentUrl, s.accessToken, partial) { written, total ->
                                val fraction = if (total > 0) written.toDouble() / total else 0.0
                                val overall = ((doneSec + track.durationSec * fraction) / totalSec).toFloat()
                                val current = _downloads.value[itemId]?.progress ?: 0f
                                if (overall - current >= 0.01f) setDownload(itemId, AbsDownloadState(overall, done = false))
                            }
                        }
                        if (!partial.renameTo(target)) throw java.io.IOException("Could not save ${target.name}")
                    }
                    doneSec += track.durationSec
                    setDownload(itemId, AbsDownloadState((doneSec / totalSec).toFloat(), done = false))
                }
                cacheCover(itemId)
                File(dir, BOOK_FILE).writeText(json.encodeToString(AbsBook.serializer(), book))
                File(dir, COMPLETE_MARKER).writeText(System.currentTimeMillis().toString())
                setDownload(itemId, AbsDownloadState(1f, done = true))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Download of $itemId failed")
                setDownload(itemId, AbsDownloadState(_downloads.value[itemId]?.progress ?: 0f, done = false, failed = true))
            } finally {
                downloadJobs.remove(itemId)
            }
        }
    }

    /** Stops a download in progress, or deletes a finished one. */
    fun removeDownload(itemId: String) {
        downloadJobs.remove(itemId)?.cancel()
        _downloads.value = _downloads.value - itemId
        scope.launch { File(downloadsDir, itemId).deleteRecursively() }
    }

    fun isDownloaded(itemId: String): Boolean = File(File(downloadsDir, itemId), COMPLETE_MARKER).exists()

    fun downloadedBooks(): List<AbsBook> = downloadedIds().mapNotNull(::downloadedBook)

    /** The downloaded file for a track id, when the book has been saved. */
    fun offlineUri(trackId: String): Uri? {
        val (itemId, index) = parseTrackId(trackId) ?: return null
        return downloadedTrack(itemId, index)?.let(Uri::fromFile)
    }

    /**
     * Where to resume: the newer of the server's position and the one recorded on this phone,
     * which covers listening while offline. A newer local position is pushed to the server.
     */
    private suspend fun resumePosition(itemId: String): Double {
        val server = runCatching { progress(itemId) }
        val remote = server.getOrNull()
        val local = localPositions[itemId]
        if (local != null && (remote == null || local.updatedAt > remote.lastUpdate)) {
            if (server.isSuccess) {
                scope.launch {
                    runCatching {
                        authed { s ->
                            val duration = remote?.durationSec?.takeIf { it > 0 }
                                ?: tracks.values.firstOrNull { it.itemId == itemId }?.bookDurationSec ?: 0.0
                            client.updateProgress(s.serverUrl, s.accessToken, itemId, local.currentTimeSec, duration, local.finished)
                        }
                    }
                }
            }
            return if (local.finished) 0.0 else local.currentTimeSec
        }
        return remote?.takeIf { !it.isFinished }?.currentTimeSec ?: 0.0
    }

    private fun setDownload(itemId: String, state: AbsDownloadState) {
        _downloads.value = _downloads.value + (itemId to state)
    }

    private fun downloadedIds(): List<String> =
        downloadsDir.listFiles()?.filter { File(it, COMPLETE_MARKER).exists() }?.map { it.name }.orEmpty()

    private fun downloadedBook(itemId: String): AbsBook? {
        if (!isDownloaded(itemId)) return null
        return runCatching {
            json.decodeFromString(AbsBook.serializer(), File(File(downloadsDir, itemId), BOOK_FILE).readText())
        }.getOrNull()
    }

    private fun downloadedTrack(itemId: String, index: Int): File? {
        if (!isDownloaded(itemId)) return null
        val dir = File(downloadsDir, itemId)
        return dir.listFiles()?.firstOrNull { it.name.substringBefore('.') == index.toString() && !it.name.endsWith(".part") }
    }

    private fun trackFileName(track: AbsTrack): String {
        val extension = track.title.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: "audio"
        return "${track.index}.$extension"
    }

    private fun parseTrackId(trackId: String): Pair<String, Int>? {
        if (!trackId.startsWith(ABS_TRACK_ID_PREFIX)) return null
        val rest = trackId.removePrefix(ABS_TRACK_ID_PREFIX)
        val index = rest.substringAfterLast(':').toIntOrNull() ?: return null
        return rest.substringBeforeLast(':') to index
    }

    private fun coverFile(itemId: String) = File(File(context.cacheDir, "artwork"), "abs_$itemId.jpg")

    private fun loadPositions() {
        runCatching {
            if (positionsFile.exists()) {
                localPositions.putAll(json.decodeFromString(positionMapSerializer, positionsFile.readText()))
            }
        }.onFailure { Timber.tag(TAG).w(it, "Could not read local positions") }
    }

    private fun savePositions() {
        runCatching { positionsFile.writeText(json.encodeToString(positionMapSerializer, HashMap(localPositions))) }
            .onFailure { Timber.tag(TAG).w(it, "Could not save local positions") }
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
        val file = coverFile(itemId).also { it.parentFile?.mkdirs() }
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
        val positionMapSerializer = MapSerializer(String.serializer(), AbsLocalPosition.serializer())
        const val BOOK_FILE = "book.json"
        const val COMPLETE_MARKER = ".complete"

        fun normalizeServer(input: String): String {
            val trimmed = input.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }
}
