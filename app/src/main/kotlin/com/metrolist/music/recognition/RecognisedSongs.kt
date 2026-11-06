/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.recognition

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.datastore.preferences.core.stringPreferencesKey
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import com.metrolist.shazamkit.models.RecognitionResult
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * Songs recognised by listening (the "Shazam" feature) are added automatically to the playlist chosen
 * in Settings → Features → Song recognition. The song is found on YouTube Music (the exact video when
 * Shazam gives one), saved, and added unless it's already in that playlist. Synced YouTube playlists
 * get it on YouTube too.
 */
object RecognisedSongs {
    /** Id of the Wavelength playlist recognised songs go into; missing or empty means off. */
    val PlaylistKey = stringPreferencesKey("wavelengthRecognisedPlaylist")

    private const val TAG = "RecognisedSongs"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun database(): MusicDatabase
        fun syncUtils(): SyncUtils
    }

    /** Called when a song is recognised; does nothing if no playlist is set. */
    fun onRecognised(context: Context, result: RecognitionResult) {
        val app = context.applicationContext
        scope.launch {
            val message = runCatching { addToPlaylist(app, result) }
                .onFailure { Timber.tag(TAG).w(it, "Couldn't add recognised song") }
                .getOrElse { app.getString(R.string.recognised_add_failed, result.title) }
                ?: return@launch
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    /** Returns a message to show, or null when the feature is off. */
    private suspend fun addToPlaylist(context: Context, result: RecognitionResult): String? {
        val playlistId = context.dataStore.data.first()[PlaylistKey]?.takeIf { it.isNotBlank() } ?: return null
        val access = EntryPointAccessors.fromApplication(context, Access::class.java)
        val database = access.database()
        val playlist = database.playlist(playlistId).firstOrNull()
            ?: return context.getString(R.string.recognised_playlist_missing)
        val song = findOnYouTube(result)
            ?: return context.getString(R.string.recognised_not_found, result.title)
        database.insert(song.toMediaMetadata())
        if (database.playlistDuplicates(playlist.id, listOf(song.id)).isNotEmpty()) {
            return context.getString(R.string.recognised_already_in, result.title, playlist.playlist.name)
        }
        database.addSongsToPlaylist(playlist, listOf(song.id to null))
        playlist.playlist.browseId?.let { browseId ->
            access.syncUtils().scheduleAddToPlaylist(browseId, playlist.id, listOf(song.id))
        }
        return context.getString(R.string.recognised_added, result.title, playlist.playlist.name)
    }

    /** The recognised song on YouTube Music: Shazam's own video if it gave one, else the best song match. */
    private suspend fun findOnYouTube(result: RecognitionResult): SongItem? {
        val songs = YouTube.search("${result.title} ${result.artist}", YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items.orEmpty().filterIsInstance<SongItem>()
        result.youtubeVideoId?.let { id -> songs.firstOrNull { it.id == id }?.let { return it } }
        fun norm(text: String) = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val title = norm(result.title)
        val artist = norm(result.artist)
        return songs.firstOrNull { song ->
            norm(song.title).contains(title) && song.artists.any { a -> artist.contains(norm(a.name)) || norm(a.name).contains(artist) }
        } ?: songs.firstOrNull { norm(it.title).contains(title) } ?: songs.firstOrNull()
    }
}
