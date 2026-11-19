package com.metrolist.music.podcasts

import androidx.datastore.preferences.core.intPreferencesKey

fun podcastPlaylistCountKey(id: String) = intPreferencesKey("podcastPlaylistEpisodeCount:${id.removePrefix("VL")}")

/** Only exact metadata counts or a complete page are totals; a partial page is not a total. */
fun podcastPlaylistEpisodeCount(text: String?, pageSize: Int, hasMore: Boolean): Int? {
    val number = text?.trim()?.let {
        Regex("^([0-9]+(?:[,.\\s][0-9]{3})*)\\s+(?![KMB](?:\\s|$))[^0-9].*$", RegexOption.IGNORE_CASE)
            .matchEntire(it)?.groupValues?.get(1)
    }?.filter(Char::isDigit)?.toIntOrNull()
    return number ?: pageSize.takeIf { !hasMore && it > 0 }
}
