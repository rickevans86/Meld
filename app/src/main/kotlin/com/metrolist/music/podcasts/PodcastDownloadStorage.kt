package com.metrolist.music.podcasts

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.constants.podcastPlaylistIds
import com.metrolist.music.constants.youTubePodcastPlaylists
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first

internal fun markedPodcastPlaylist(selected: List<String>, localId: String?, browseId: String?): Boolean =
    (localId != null && localId in selected) ||
        (browseId != null && selected.filter { it.startsWith("yt:") }.any {
            it.removePrefix("yt:").substringBefore('\t').removePrefix("VL") == browseId.removePrefix("VL")
        })

/** Private cache is already used for all downloads; podcasts must never be exported to the music tree. */
internal object PodcastDownloadStorage {
    val PrivateIdsKey = stringSetPreferencesKey("privatePodcastDownloadIds")

    suspend fun isPrivate(context: Context, database: MusicDatabase, songId: String,
        episodeHint: Boolean = false, sourcePlaylistId: String? = null): Boolean {
        val prefs = context.dataStore.data.first()
        if (episodeHint || songId.startsWith("podcast:") || songId in prefs[PrivateIdsKey].orEmpty() ||
            database.getSongByIdBlocking(songId)?.song?.isEpisode == true) return true
        val selected = prefs.podcastPlaylistIds()
        if (selected.isEmpty()) return false
        val localIds = selected.filterNot { it.startsWith("yt:") }
        val remoteIds = prefs.youTubePodcastPlaylists().map { it.id.removePrefix("VL") }.toMutableSet()
        for (id in localIds) database.playlist(id).first()?.playlist?.browseId?.let { remoteIds += it.removePrefix("VL") }
        if (sourcePlaylistId != null) {
            if (sourcePlaylistId in localIds) return true
            val remote = when {
                sourcePlaylistId.startsWith("youtube:") -> sourcePlaylistId.removePrefix("youtube:")
                sourcePlaylistId.startsWith("yt:") -> sourcePlaylistId.removePrefix("yt:")
                else -> database.playlist(sourcePlaylistId).first()?.playlist?.browseId
            }?.removePrefix("VL")
            if (remote in remoteIds) return true
        }
        if (localIds.isNotEmpty() && songId in database.podcastHomeSongIds(localIds).first()) return true
        return remoteIds.any { songId in prefs[stringSetPreferencesKey("podcastHomePlaylist:$it")].orEmpty() }
    }

    suspend fun remember(context: Context, songId: String) {
        context.dataStore.edit { it[PrivateIdsKey] = it[PrivateIdsKey].orEmpty() + songId }
    }
}
