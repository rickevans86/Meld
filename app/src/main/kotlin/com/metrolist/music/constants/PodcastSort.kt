/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.MediaItem
import com.metrolist.music.db.entities.PodcastEntity
import com.metrolist.music.podcasts.PodcastShow
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import java.time.ZoneId

/** How podcast shows are listed, in the Podcasts tab and on Android Auto's Podcasts page. */
enum class PodcastShowSort { NAME, LATEST_EPISODE, RECENTLY_FOLLOWED, PUBLISHER }

val PodcastShowSortKey = stringPreferencesKey("podcastShowSort")
val PodcastShowSortReversedKey = booleanPreferencesKey("podcastShowSortReversed")

/** What a show is sorted by. Dates are epoch millis; missing ones sort last. */
data class ShowSortInfo(val title: String, val publisher: String?, val latestAt: Long?, val followedAt: Long?)

/**
 * Sorts shows. Name and publisher go A–Z; latest episode and recently followed go newest first.
 * [reversed] flips the order.
 */
fun <T> List<T>.sortedShows(sort: PodcastShowSort, reversed: Boolean, info: (T) -> ShowSortInfo): List<T> {
    val byTitle = compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { info(it).title }
    val comparator: Comparator<T> = when (sort) {
        PodcastShowSort.NAME -> byTitle
        PodcastShowSort.PUBLISHER ->
            compareBy<T, String>(String.CASE_INSENSITIVE_ORDER) { info(it).publisher?.takeIf { p -> p.isNotBlank() } ?: "￿" }
                .then(byTitle)
        PodcastShowSort.LATEST_EPISODE -> compareByDescending<T> { info(it).latestAt ?: Long.MIN_VALUE }.then(byTitle)
        PodcastShowSort.RECENTLY_FOLLOWED -> compareByDescending<T> { info(it).followedAt ?: Long.MIN_VALUE }.then(byTitle)
    }
    val sorted = sortedWith(comparator)
    return if (reversed) sorted.reversed() else sorted
}

fun PodcastShow.sortInfo() = ShowSortInfo(title, author, latestEpisodeAt, followedAt)

fun PodcastEntity.sortInfo() = ShowSortInfo(
    title = title,
    publisher = author,
    latestAt = lastUpdateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
    followedAt = bookmarkedAt?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
)

suspend fun podcastSortSetting(context: Context): Pair<PodcastShowSort, Boolean> {
    val prefs = context.dataStore.data.first()
    val sort = prefs[PodcastShowSortKey]?.let { name -> PodcastShowSort.entries.firstOrNull { it.name == name } }
        ?: PodcastShowSort.RECENTLY_FOLLOWED
    return sort to (prefs[PodcastShowSortReversedKey] ?: false)
}

/** Android Auto: YouTube channels and followed feeds as one list in the chosen order. */
suspend fun sortPodcastFolders(
    context: Context,
    channelItems: List<MediaItem>,
    feedItems: List<MediaItem>,
    channels: List<PodcastEntity>,
    feeds: List<PodcastShow>,
): List<MediaItem> {
    val (sort, reversed) = podcastSortSetting(context)
    val entries = channelItems.zip(channels.map { it.sortInfo() }) + feedItems.zip(feeds.map { it.sortInfo() })
    return entries.sortedShows(sort, reversed) { it.second }.map { it.first }
}
