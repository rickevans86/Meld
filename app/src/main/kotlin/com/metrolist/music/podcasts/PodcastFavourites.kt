/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.security.MessageDigest

object PodcastFavourites {
    val Key = stringSetPreferencesKey("podcastFavouriteShows")

    suspend fun setFavourite(store: DataStore<Preferences>, mediaId: String, favourite: Boolean) {
        store.edit { prefs ->
            val current = prefs[Key].orEmpty()
            prefs[Key] = if (favourite) current + mediaId else current - mediaId
        }
    }

    fun playlistId(id: String, online: Boolean) =
        if (online) "youtube_playlist/${id.removePrefix("VL")}" else "playlist/$id"

    fun channelId(id: String) = "podcasts/channel/$id"

    fun feedKey(feedUrl: String): String = MessageDigest.getInstance("SHA-1")
        .digest(feedUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)

    fun feedId(feedUrl: String) = "podcasts/feed/${feedKey(feedUrl)}"
}

/** Stable partition: favourite status never reverses the user's chosen sort order. */
fun <T> List<T>.podcastFavouritesFirst(favourites: Set<String>, id: (T) -> String): List<T> =
    sortedBy { id(it) !in favourites }
