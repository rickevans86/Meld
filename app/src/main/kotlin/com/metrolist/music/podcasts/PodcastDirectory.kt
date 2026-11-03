/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import android.content.Context
import android.util.Xml
import com.metrolist.music.constants.PODCAST_EPISODE_ID_PREFIX
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PodcastShow(
    val feedUrl: String,
    val title: String,
    val author: String? = null,
    val artworkUrl: String? = null,
)

data class PodcastEpisode(
    val id: String,
    val title: String,
    val audioUrl: String,
    val publishedAt: Long?,
    val durationSec: Int?,
    val imageUrl: String?,
)

data class PodcastFeed(val show: PodcastShow, val episodes: List<PodcastEpisode>)

/**
 * Podcasts from their public RSS feeds: searching Apple's free podcast directory, reading feeds,
 * following shows, and turning episodes into songs the player can stream.
 * This is also how Spotify podcasts are played, since Spotify doesn't let other apps stream them.
 */
@Singleton
class PodcastDirectory
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val followsFile = File(context.filesDir, "podcast_follows.json")

    private val _followed = MutableStateFlow(loadFollowed())
    val followed = _followed.asStateFlow()

    suspend fun search(term: String): List<PodcastShow> = withContext(Dispatchers.IO) {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "30")
            .addQueryParameter("term", term)
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Podcast search failed (${response.code})")
            response.body?.string().orEmpty()
        }
        json.parseToJsonElement(body).jsonObject["results"]?.jsonArray.orEmpty().mapNotNull { element ->
            val result = element as? JsonObject ?: return@mapNotNull null
            PodcastShow(
                feedUrl = result.string("feedUrl") ?: return@mapNotNull null,
                title = result.string("collectionName") ?: return@mapNotNull null,
                author = result.string("artistName"),
                artworkUrl = result.string("artworkUrl600") ?: result.string("artworkUrl100"),
            )
        }
    }

    /** Finds the public feed for a show known only by name, e.g. one saved on Spotify. */
    suspend fun findFeed(name: String, publisher: String?): PodcastShow? {
        val results = search(name)
        val wanted = normalize(name)
        return results.firstOrNull { normalize(it.title) == wanted && sameAuthor(it.author, publisher) }
            ?: results.firstOrNull { normalize(it.title) == wanted }
            ?: results.firstOrNull {
                wanted.length >= 8 && normalize(it.title).contains(wanted) && sameAuthor(it.author, publisher)
            }
    }

    suspend fun feed(show: PodcastShow): PodcastFeed = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(show.feedUrl).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Couldn't load the podcast feed (${response.code})")
            val stream = response.body?.byteStream() ?: throw IOException("Empty podcast feed")
            parseFeed(show, stream)
        }
    }

    fun isFollowed(feedUrl: String): Boolean = _followed.value.any { it.feedUrl == feedUrl }

    fun setFollowed(show: PodcastShow, follow: Boolean) {
        val current = _followed.value.filterNot { it.feedUrl == show.feedUrl }
        _followed.value = if (follow) listOf(show) + current else current
        runCatching {
            followsFile.writeText(json.encodeToString(ListSerializer(PodcastShow.serializer()), _followed.value))
        }.onFailure { Timber.tag(TAG).w(it, "Could not save followed podcasts") }
    }

    /** Saves episodes as songs the player streams from their audio URL, newest first. */
    suspend fun prepare(feed: PodcastFeed): List<Song> = withContext(Dispatchers.IO) {
        val author = feed.show.author?.takeIf { it.isNotBlank() }
        val ids = feed.episodes.map { episode ->
            val songId = PODCAST_EPISODE_ID_PREFIX + episode.id
            val existing = database.getSongById(songId)?.song
            with(database) {
                if (existing == null) {
                    upsert(
                        SongEntity(
                            id = songId,
                            title = episode.title,
                            duration = episode.durationSec ?: -1,
                            thumbnailUrl = episode.imageUrl ?: feed.show.artworkUrl,
                            albumName = feed.show.title,
                            isEpisode = true,
                            localPath = episode.audioUrl,
                        ),
                    )
                    if (author != null) {
                        val artistId = artistByName(author)?.id ?: ArtistEntity(
                            id = ArtistEntity.generateArtistId(),
                            name = author,
                        ).also { insert(it) }.id
                        insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                    }
                } else if (existing.localPath != episode.audioUrl) {
                    upsert(existing.copy(localPath = episode.audioUrl))
                }
            }
            songId
        }
        val byId = database.getSongsByIds(ids).associateBy { it.id }
        ids.mapNotNull(byId::get)
    }

    private fun parseFeed(show: PodcastShow, input: java.io.InputStream): PodcastFeed {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }
        var title: String? = null
        var author: String? = null
        var artwork: String? = null
        val episodes = mutableListOf<PodcastEpisode>()

        var inItem = false
        var inChannelImage = false
        var itemTitle: String? = null
        var itemGuid: String? = null
        var itemUrl: String? = null
        var itemType: String? = null
        var itemDate: Long? = null
        var itemDuration: Int? = null
        var itemImage: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT && episodes.size < MAX_EPISODES) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        itemTitle = null; itemGuid = null; itemUrl = null; itemType = null
                        itemDate = null; itemDuration = null; itemImage = null
                    }
                    "image" -> if (!inItem) inChannelImage = true
                    "url" -> if (inChannelImage && artwork == null) artwork = parser.nextText().trim()
                    "title" -> {
                        val text = parser.nextText().trim()
                        if (inItem) itemTitle = text else if (!inChannelImage && title == null) title = text
                    }
                    "itunes:author" -> {
                        val text = parser.nextText().trim()
                        if (!inItem && author == null) author = text
                    }
                    "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (inItem) itemImage = href else if (href != null) artwork = href
                    }
                    "guid" -> if (inItem) itemGuid = parser.nextText().trim()
                    "pubDate" -> if (inItem) itemDate = parseDate(parser.nextText().trim())
                    "itunes:duration" -> if (inItem) itemDuration = parseDuration(parser.nextText().trim())
                    "enclosure" -> if (inItem) {
                        itemUrl = parser.getAttributeValue(null, "url")
                        itemType = parser.getAttributeValue(null, "type")
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "image" -> inChannelImage = false
                    "item" -> {
                        inItem = false
                        val url = itemUrl
                        val isAudio = itemType?.startsWith("video/") != true
                        if (url != null && isAudio) {
                            episodes += PodcastEpisode(
                                id = hash(itemGuid?.takeIf { it.isNotBlank() } ?: url),
                                title = itemTitle ?: "Episode",
                                audioUrl = url,
                                publishedAt = itemDate,
                                durationSec = itemDuration,
                                imageUrl = itemImage,
                            )
                        }
                    }
                }
            }
        }
        val resolvedShow = show.copy(
            title = title?.takeIf { it.isNotBlank() } ?: show.title,
            author = show.author ?: author,
            artworkUrl = show.artworkUrl ?: artwork,
        )
        return PodcastFeed(resolvedShow, episodes.sortedByDescending { it.publishedAt ?: 0L })
    }

    private fun parseDate(text: String): Long? =
        DATE_FORMATS.firstNotNullOfOrNull { pattern ->
            runCatching { SimpleDateFormat(pattern, Locale.US).parse(text)?.time }.getOrNull()
        }

    private fun parseDuration(text: String): Int? {
        if (text.isEmpty()) return null
        text.toIntOrNull()?.let { return it }
        val parts = text.split(':').map { it.toIntOrNull() ?: return null }
        return parts.fold(0) { total, part -> total * 60 + part }
    }

    private fun loadFollowed(): List<PodcastShow> = runCatching {
        if (followsFile.exists()) {
            json.decodeFromString(ListSerializer(PodcastShow.serializer()), followsFile.readText())
        } else {
            emptyList()
        }
    }.getOrDefault(emptyList())

    private fun normalize(text: String) = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    private fun sameAuthor(a: String?, b: String?): Boolean =
        a == null || b == null || normalize(a).contains(normalize(b)) || normalize(b).contains(normalize(a))

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val TAG = "PodcastDirectory"
        const val MAX_EPISODES = 300
        val DATE_FORMATS = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, d MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
        )
    }
}
