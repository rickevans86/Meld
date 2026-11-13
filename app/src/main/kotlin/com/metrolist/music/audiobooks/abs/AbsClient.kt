/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.audiobooks.abs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AbsLibrary(val id: String, val name: String)

data class AbsBookSummary(
    val id: String,
    val title: String,
    val author: String?,
    val durationSec: Double,
)

@Serializable
data class AbsTrack(
    val index: Int,
    val title: String,
    val startOffsetSec: Double,
    val durationSec: Double,
    val contentUrl: String,
)

@Serializable
data class AbsChapter(val title: String, val startSec: Double, val endSec: Double)

@Serializable
data class AbsBook(
    val id: String,
    val title: String,
    val author: String?,
    val narrator: String?,
    val durationSec: Double,
    val tracks: List<AbsTrack>,
    val chapters: List<AbsChapter>,
)

data class AbsProgress(
    val libraryItemId: String,
    val currentTimeSec: Double,
    val durationSec: Double,
    val progress: Double,
    val isFinished: Boolean,
    val lastUpdate: Long,
)

data class AbsPage(val books: List<AbsBookSummary>, val total: Int)

enum class AbsBrowseKind(val path: String) { SERIES("series"), COLLECTIONS("collections"), PLAYLISTS("playlists") }
data class AbsBookGroup(val id: String, val name: String, val books: List<AbsBookSummary>)
data class AbsGroupPage(val groups: List<AbsBookGroup>, val total: Int)

internal fun parseAbsShelves(value: JsonElement): Map<String, List<AbsBookSummary>> =
    (value as? JsonArray).orEmpty().mapNotNull { element ->
        val shelf = element.obj ?: return@mapNotNull null
        if (shelf.string("type") != "book") return@mapNotNull null
        val id = shelf.string("id") ?: return@mapNotNull null
        id to shelf.array("entities").orEmpty().mapNotNull { it.obj?.toSummary() }.distinctBy { it.id }
    }.toMap()

internal fun parseAbsGroups(value: JsonElement, kind: AbsBrowseKind): AbsGroupPage {
    val root = value.obj
    val groups = root?.array("results").orEmpty().mapNotNull { element ->
        val group = element.obj ?: return@mapNotNull null
        val id = group.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val books = if (kind == AbsBrowseKind.PLAYLISTS) {
            group.array("items").orEmpty().mapNotNull { it.obj?.obj("libraryItem")?.toSummary() }
        } else group.array("books").orEmpty().mapNotNull { it.obj?.toSummary() }
        AbsBookGroup(id, group.string("name").orEmpty(), books.distinctBy { it.id })
    }
    return AbsGroupPage(groups, root?.get("total")?.primitive?.intOrNull ?: groups.size)
}

class AbsHttpException(val code: Int, message: String) : IOException(message)

/** Thin client for the Audiobookshelf REST API. All calls run on the IO dispatcher. */
internal class AbsClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    /** Returns username, access token and (on servers that issue them) a refresh token. */
    suspend fun login(server: String, username: String, password: String): AbsSession {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url(endpoint(server, "login"))
            .header("x-return-tokens", "true")
            .post(body)
            .build()
        return sessionFrom(server, execute(request), fallbackUsername = username)
    }

    suspend fun refresh(server: String, refreshToken: String): AbsSession {
        val request = Request.Builder()
            .url(endpoint(server, "auth/refresh"))
            .header("x-refresh-token", refreshToken)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        val session = sessionFrom(server, execute(request), fallbackUsername = "")
        return if (session.refreshToken.isNullOrBlank()) session.copy(refreshToken = refreshToken) else session
    }

    /** Validates an API key and returns the user it belongs to. */
    suspend fun username(server: String, token: String): String =
        get(server, token, "api/me").obj?.string("username").orEmpty()

    suspend fun libraries(server: String, token: String): List<AbsLibrary> =
        get(server, token, "api/libraries").obj?.array("libraries").orEmpty()
            .mapNotNull { it.obj }
            .filter { it.string("mediaType") == "book" }
            .mapNotNull { lib ->
                AbsLibrary(lib.string("id") ?: return@mapNotNull null, lib.string("name").orEmpty())
            }

    suspend fun books(server: String, token: String, libraryId: String, page: Int, limit: Int): AbsPage {
        val result = get(
            server, token, "api/libraries/$libraryId/items",
            "limit" to limit.toString(),
            "page" to page.toString(),
            "sort" to "media.metadata.title",
            "minified" to "1",
        ).obj
        return AbsPage(
            books = result?.array("results").orEmpty().mapNotNull { it.obj?.toSummary() },
            total = result?.get("total")?.primitive?.intOrNull ?: 0,
        )
    }

    suspend fun shelves(server: String, token: String, libraryId: String) =
        parseAbsShelves(get(server, token, "api/libraries/$libraryId/personalized"))

    suspend fun groups(server: String, token: String, libraryId: String, kind: AbsBrowseKind, page: Int, limit: Int) =
        parseAbsGroups(get(server, token, "api/libraries/$libraryId/${kind.path}", "page" to page.toString(), "limit" to limit.toString(), "minified" to "1"), kind)

    suspend fun search(server: String, token: String, libraryId: String, query: String): List<AbsBookSummary> =
        get(server, token, "api/libraries/$libraryId/search", "q" to query, "limit" to "30").obj
            ?.array("book").orEmpty()
            .mapNotNull { it.obj?.obj("libraryItem")?.toSummary() }

    suspend fun inProgress(server: String, token: String): List<AbsBookSummary> =
        get(server, token, "api/me/items-in-progress", "limit" to "25").obj
            ?.array("libraryItems").orEmpty()
            .mapNotNull { it.obj }
            .filter { it.string("mediaType") == "book" }
            .mapNotNull { it.toSummary() }

    suspend fun allProgress(server: String, token: String): List<AbsProgress> =
        get(server, token, "api/me").obj?.array("mediaProgress").orEmpty()
            .mapNotNull { it.obj?.toProgress() }

    suspend fun progress(server: String, token: String, itemId: String): AbsProgress? =
        try {
            get(server, token, "api/me/progress/$itemId").obj?.toProgress()
        } catch (e: AbsHttpException) {
            if (e.code == 404) null else throw e
        }

    suspend fun book(server: String, token: String, itemId: String): AbsBook {
        val item = get(server, token, "api/items/$itemId", "expanded" to "1").obj
            ?: throw IOException("Empty response for $itemId")
        val media = item.obj("media")
        val metadata = media?.obj("metadata")
        return AbsBook(
            id = itemId,
            title = metadata?.string("title").orEmpty().ifBlank { "Untitled" },
            author = metadata?.string("authorName")?.takeIf { it.isNotBlank() }
                ?: metadata?.array("authors")?.mapNotNull { it.obj?.string("name") }?.joinToString(", ")?.takeIf { it.isNotBlank() },
            narrator = metadata?.string("narratorName")?.takeIf { it.isNotBlank() },
            durationSec = media?.double("duration") ?: 0.0,
            tracks = media?.array("tracks").orEmpty().mapIndexedNotNull { position, element ->
                val track = element.obj ?: return@mapIndexedNotNull null
                AbsTrack(
                    index = position,
                    title = track.string("title").orEmpty(),
                    startOffsetSec = track.double("startOffset") ?: 0.0,
                    durationSec = track.double("duration") ?: 0.0,
                    contentUrl = track.string("contentUrl") ?: return@mapIndexedNotNull null,
                )
            },
            chapters = media?.array("chapters").orEmpty().mapNotNull { element ->
                val chapter = element.obj ?: return@mapNotNull null
                AbsChapter(
                    title = chapter.string("title").orEmpty(),
                    startSec = chapter.double("start") ?: return@mapNotNull null,
                    endSec = chapter.double("end") ?: 0.0,
                )
            },
        )
    }

    suspend fun updateProgress(
        server: String,
        token: String,
        itemId: String,
        currentTimeSec: Double,
        durationSec: Double,
        isFinished: Boolean,
    ) {
        val body = buildJsonObject {
            put("currentTime", currentTimeSec)
            put("duration", durationSec)
            put("progress", if (durationSec > 0) (currentTimeSec / durationSec).coerceIn(0.0, 1.0) else 0.0)
            put("isFinished", isFinished)
        }.toString().toRequestBody(jsonType)
        execute(
            Request.Builder()
                .url(endpoint(server, "api/me/progress/$itemId"))
                .header("Authorization", "Bearer $token")
                .patch(body)
                .build(),
        )
    }

    /** Streams [url] into [target], reporting bytes written and the total size when the server sends it. */
    suspend fun downloadTo(
        url: String,
        token: String,
        target: File,
        onProgress: (written: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw AbsHttpException(response.code, "HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty download")
            val total = body.contentLength()
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var written = 0L
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, total)
                    }
                }
            }
        }
    }

    suspend fun download(url: String, token: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build()).execute().use {
            if (!it.isSuccessful) throw AbsHttpException(it.code, "HTTP ${it.code}")
            it.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun get(
        server: String,
        token: String,
        path: String,
        vararg query: Pair<String, String>,
    ): JsonElement {
        val url = endpoint(server, path).newBuilder().apply {
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        return execute(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
    }

    private suspend fun execute(request: Request): JsonElement = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AbsHttpException(response.code, text.take(200).ifBlank { "HTTP ${response.code}" })
            }
            if (text.isBlank() || text == "OK") JsonNull else runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
        }
    }

    private fun endpoint(server: String, path: String) =
        "${server.trimEnd('/')}/$path".toHttpUrlOrNull() ?: throw IOException("Invalid server address")

    private fun sessionFrom(server: String, response: JsonElement, fallbackUsername: String): AbsSession {
        val user = response.obj?.obj("user") ?: throw IOException("Unexpected sign-in response")
        val token = user.string("accessToken")?.takeIf { it.isNotBlank() }
            ?: user.string("token")?.takeIf { it.isNotBlank() }
            ?: throw IOException("Server did not return a token")
        return AbsSession(
            serverUrl = server.trimEnd('/'),
            username = user.string("username") ?: fallbackUsername,
            accessToken = token,
            refreshToken = user.string("refreshToken")?.takeIf { it.isNotBlank() },
        )
    }

    private fun JsonObject.toProgress(): AbsProgress? = AbsProgress(
        libraryItemId = string("libraryItemId") ?: return null,
        currentTimeSec = double("currentTime") ?: 0.0,
        durationSec = double("duration") ?: 0.0,
        progress = double("progress") ?: 0.0,
        isFinished = get("isFinished")?.primitive?.booleanOrNull ?: false,
        lastUpdate = get("lastUpdate")?.primitive?.content?.toLongOrNull() ?: 0L,
    )
}

private val JsonElement.obj: JsonObject? get() = this as? JsonObject
private val JsonElement.primitive: JsonPrimitive? get() = this as? JsonPrimitive
private fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = get(key) as? JsonArray
private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonObject.double(key: String): Double? = (get(key) as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.toSummary(): AbsBookSummary? {
    if (string("mediaType")?.let { it != "book" } == true) return null
    val media = obj("media")
    val metadata = media?.obj("metadata")
    return AbsBookSummary(
        id = string("id")?.takeIf { it.isNotBlank() } ?: return null,
        title = metadata?.string("title").orEmpty().ifBlank { "Untitled" },
        author = metadata?.string("authorName")?.takeIf { it.isNotBlank() }
            ?: metadata?.array("authors")?.mapNotNull { it.obj?.string("name") }?.joinToString(", ")?.takeIf { it.isNotBlank() },
        durationSec = media?.double("duration") ?: 0.0,
    )
}
