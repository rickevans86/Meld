/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.audiobooks

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.constants.isAudiobookId
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class AudiobookBookmark(
    val id: String = UUID.randomUUID().toString(),
    val mediaId: String,
    val chapter: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

class AudiobookBookmarks(private val store: DataStore<Preferences>) {
    private val key = stringSetPreferencesKey("audiobookBookmarks")
    private val json = Json { ignoreUnknownKeys = true }

    val bookmarks = store.data.map { prefs ->
        prefs[key].orEmpty().mapNotNull(::decode).sortedByDescending { it.createdAt }
    }

    suspend fun add(bookmark: AudiobookBookmark) {
        require(isAudiobookId(bookmark.mediaId) && bookmark.positionMs >= 0)
        store.edit { prefs -> prefs[key] = prefs[key].orEmpty() + json.encodeToString(bookmark) }
    }

    suspend fun remove(id: String) {
        store.edit { prefs -> prefs[key] = prefs[key].orEmpty().filterNot { decode(it)?.id == id }.toSet() }
    }

    private fun decode(value: String): AudiobookBookmark? =
        runCatching { json.decodeFromString<AudiobookBookmark>(value) }
            .getOrNull()?.takeIf { isAudiobookId(it.mediaId) && it.positionMs >= 0 }
}
