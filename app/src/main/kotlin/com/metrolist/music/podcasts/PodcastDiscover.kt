/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A podcast from the charts, before its feed has been looked up. */
data class DiscoverShow(val appleId: String, val name: String, val artist: String?, val artworkUrl: String?)

/** One row on the Discover page, e.g. "True Crime". */
data class DiscoverRow(val title: String, val shows: List<DiscoverShow>)

/**
 * Podcasts to discover, from Apple Podcasts' public top charts for the phone's country:
 * the overall top shows, then one row per category. Cached for a few hours.
 */
@Singleton
class PodcastDiscover
@Inject
constructor() {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cached: Pair<Long, List<DiscoverRow>>? = null

    val country: String
        get() = Locale.getDefault().country.lowercase(Locale.ROOT).takeIf { it.length == 2 } ?: "us"

    val countryName: String
        get() = Locale("", country.uppercase(Locale.ROOT)).displayCountry.ifBlank { country.uppercase(Locale.ROOT) }

    suspend fun rows(refresh: Boolean = false): List<DiscoverRow> = withContext(Dispatchers.IO) {
        cached?.takeIf { !refresh && System.currentTimeMillis() - it.first < CACHE_MS }?.let { return@withContext it.second }
        val cc = country
        val rows = coroutineScope {
            val top = async { DiscoverRow(TOP_TITLE, topShows(cc, null)) }
            val genres = GENRES.map { (title, id) -> async { DiscoverRow(title, topShows(cc, id)) } }
            (listOf(top) + genres).awaitAll()
        }.filter { it.shows.isNotEmpty() }
        if (rows.isNotEmpty()) cached = System.currentTimeMillis() to rows
        rows
    }

    /** Looks up a chart show's public feed so it can be opened or followed. */
    suspend fun resolve(show: DiscoverShow): PodcastShow? = withContext(Dispatchers.IO) {
        runCatching {
            val result = get("https://itunes.apple.com/lookup?id=${show.appleId}")
                ?.jsonObject?.get("results")?.jsonArray?.firstOrNull() as? JsonObject ?: return@runCatching null
            val feed = result.string("feedUrl") ?: return@runCatching null
            PodcastShow(
                feedUrl = feed,
                title = result.string("collectionName") ?: show.name,
                author = result.string("artistName") ?: show.artist,
                artworkUrl = result.string("artworkUrl600") ?: show.artworkUrl,
            )
        }.onFailure { Timber.tag(TAG).w(it, "Lookup failed for ${show.name}") }.getOrNull()
    }

    private suspend fun topShows(cc: String, genre: Int?): List<DiscoverShow> = withTimeoutOrNull(20_000) {
        runCatching {
            val genrePart = genre?.let { "/genre=$it" }.orEmpty()
            val feed = get("https://itunes.apple.com/$cc/rss/toppodcasts/limit=$PER_ROW$genrePart/json")
                ?.jsonObject?.get("feed") as? JsonObject
            // One entry comes back as an object rather than a list
            val entries = when (val entry = feed?.get("entry")) {
                is JsonArray -> entry.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(entry)
                else -> emptyList()
            }
            entries.mapNotNull { entry ->
                val id = (entry["id"] as? JsonObject)?.obj("attributes")?.string("im:id") ?: return@mapNotNull null
                val name = entry.obj("im:name").string("label") ?: return@mapNotNull null
                val image = (entry["im:image"] as? JsonArray)?.lastOrNull()?.let { (it as? JsonObject)?.string("label") }
                DiscoverShow(id, name, entry.obj("im:artist").string("label"), image?.let(::bigger))
            }.ifEmpty { if (genre == null) marketingTop(cc) else emptyList() }
        }.onFailure { Timber.tag(TAG).w(it, "Chart failed ($genre)") }
            .getOrElse { if (genre == null) runCatching { marketingTop(cc) }.getOrDefault(emptyList()) else emptyList() }
    }.orEmpty()

    /** Apple's newer chart feed, used if the classic one returns nothing. */
    private fun marketingTop(cc: String): List<DiscoverShow> {
        val results = (get("https://rss.applemarketingtools.com/api/v2/$cc/podcasts/top/$PER_ROW/podcasts.json")
            ?.jsonObject?.get("feed") as? JsonObject)?.get("results") as? JsonArray ?: return emptyList()
        return results.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            DiscoverShow(
                appleId = item.string("id") ?: return@mapNotNull null,
                name = item.string("name") ?: return@mapNotNull null,
                artist = item.string("artistName"),
                artworkUrl = item.string("artworkUrl100")?.let(::bigger),
            )
        }
    }

    private fun get(url: String): JsonElement? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            json.parseToJsonElement(response.body?.string().orEmpty())
        }

    /** Chart artwork is small; Apple serves any size by changing the size in the address. */
    private fun bigger(url: String) = url.replace(Regex("/\\d+x\\d+bb\\."), "/400x400bb.")

    private fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

    companion object {
        private const val TAG = "PodcastDiscover"
        private const val PER_ROW = 25
        private const val CACHE_MS = 6 * 60 * 60 * 1000L

        /** Placeholder replaced with "Top podcasts in <country>" on screen. */
        const val TOP_TITLE = "__top__"

        /** Apple Podcasts category ids. */
        val GENRES = listOf(
            "News" to 1489,
            "True Crime" to 1488,
            "Comedy" to 1303,
            "Society & Culture" to 1324,
            "History" to 1487,
            "Science" to 1533,
            "Sports" to 1545,
            "Technology" to 1318,
            "Business" to 1321,
            "Health & Fitness" to 1512,
            "Fiction" to 1483,
            "Kids & Family" to 1305,
            "TV & Film" to 1309,
            "Music" to 1310,
            "Education" to 1304,
            "Arts" to 1301,
        )
    }
}
