package com.metrolist.music.podcasts

import androidx.datastore.preferences.core.intPreferencesKey

object PodcastBuffer {
    val MinutesKey = intPreferencesKey("podcastStartupBufferMinutes")
    const val DEFAULT_MINUTES = 2
    val choices = (2..20 step 2).toList()
    fun minutes(value: Int) = value.coerceIn(2, 20).let { it - it % 2 }
    fun isPodcast(id: String, episode: Boolean, playlistId: String?, selected: List<String>, knownIds: Set<String>, membershipLoaded: Boolean): Boolean {
        val origin = playlistId?.removePrefix("VL")
        return episode || id.startsWith("podcast:") || id in knownIds || origin in knownIds ||
            (origin != null && selected.any { it.removePrefix("yt:").substringBefore('\t').removePrefix("VL") == origin }) ||
            (!membershipLoaded && selected.isNotEmpty())
    }
    fun requiredUs(value: Int) = minutes(value) * 60_000_000L
}
