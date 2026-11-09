/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

/** Whether played podcast episodes are hidden on show pages and in New episodes (and on Android Auto). */
val HidePlayedEpisodesKey = booleanPreferencesKey("hidePlayedEpisodes")

/** Podcast episode lists show the oldest episode first when true (newest first by default). */
val PodcastOldestFirstKey = booleanPreferencesKey("podcastOldestFirst")

/** The sort chip on podcast pages and New episodes: "Newest first" / "Oldest first". */
@androidx.compose.runtime.Composable
fun EpisodeSortChip(oldestFirst: Boolean, onChange: (Boolean) -> Unit, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.FilterChip(
        selected = oldestFirst,
        onClick = { onChange(!oldestFirst) },
        label = {
            androidx.compose.material3.Text(
                androidx.compose.ui.res.stringResource(
                    if (oldestFirst) com.metrolist.music.R.string.podcast_sort_oldest else com.metrolist.music.R.string.podcast_sort_newest,
                ),
            )
        },
        leadingIcon = {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(
                    if (oldestFirst) com.metrolist.music.R.drawable.arrow_upward else com.metrolist.music.R.drawable.arrow_downward,
                ),
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(18.dp),
            )
        },
        modifier = modifier,
    )
}

/**
 * Which podcast episodes have been played (by song id). Kept in a small file because the song
 * table has no "played" column and Wavelength doesn't change Meld's database.
 */
object PlayedEpisodes {
    private val json = Json { ignoreUnknownKeys = true }
    private val _played = MutableStateFlow<Set<String>>(emptySet())
    val played = _played.asStateFlow()

    @Volatile
    private var file: File? = null

    /** Loads the saved list once; safe to call from anywhere. */
    fun init(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, "played_episodes.json")
            _played.value = runCatching {
                json.decodeFromString(ListSerializer(kotlinx.serialization.serializer<String>()), f.readText()).toSet()
            }.getOrDefault(emptySet())
            file = f
        }
    }

    fun isPlayed(id: String) = id in _played.value

    fun set(context: Context, id: String, played: Boolean) {
        init(context)
        val current = _played.value
        if ((id in current) == played) return
        _played.value = if (played) current + id else current - id
        runCatching {
            file?.writeText(json.encodeToString(ListSerializer(kotlinx.serialization.serializer<String>()), _played.value.toList()))
        }.onFailure { Timber.w(it, "Could not save played episodes") }
    }
}

@Composable
fun rememberPlayedEpisodes(): Set<String> {
    PlayedEpisodes.init(LocalContext.current)
    val played by PlayedEpisodes.played.collectAsState()
    return played
}
