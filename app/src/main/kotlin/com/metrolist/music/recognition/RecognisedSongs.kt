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

    /** Id of the playlist the car's add button puts the radio's current song into; empty means not set. */
    val RadioPlaylistKey = stringPreferencesKey("wavelengthRadioSongsPlaylist")

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
            val message = runCatching {
                addToPlaylist(app, PlaylistKey, R.string.recognised_playlist_missing, result.title, result.artist, result.youtubeVideoId)
            }
                .onFailure { Timber.tag(TAG).w(it, "Couldn't add recognised song") }
                .getOrElse { app.getString(R.string.recognised_add_failed, result.title) }
                ?: return@launch
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    /**
     * The car's add button on a radio station: [streamTitle] is what the station says is playing,
     * usually "Artist - Title". The song is found on YouTube Music and added to the radio songs
     * playlist (Settings → Features → Radio songs), like a recognised song.
     */
    fun addRadioSong(context: Context, streamTitle: String?) {
        val app = context.applicationContext
        scope.launch {
            val text = streamTitle?.trim().orEmpty()
            val message = if (text.isBlank()) {
                app.getString(R.string.radio_song_unknown)
            } else {
                val (artist, title) = splitStreamTitle(text)
                runCatching {
                    addToPlaylist(app, RadioPlaylistKey, R.string.radio_song_playlist_missing, title, artist, null)
                        ?: app.getString(R.string.radio_song_no_playlist)
                }
                    .onFailure { Timber.tag(TAG).w(it, "Couldn't add radio song") }
                    .getOrElse { app.getString(R.string.recognised_add_failed, title) }
            }
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    /**
     * The player menu's "Add to playlist" on a radio station: finds the song the station says is
     * playing on YouTube Music, saves it to the database and returns its id (null if not found).
     */
    suspend fun resolveRadioSong(context: Context, streamTitle: String?): String? {
        val text = streamTitle?.trim().orEmpty().ifBlank { return null }
        val (artist, title) = splitStreamTitle(text)
        val song = runCatching { findOnYouTube(SongQuery(title, artist, null)) }.getOrNull() ?: return null
        val database = EntryPointAccessors.fromApplication(context.applicationContext, Access::class.java).database()
        database.insert(song.toMediaMetadata())
        return song.id
    }

    /** "Artist - Title" → (artist, title). Without a dash the whole text is the title. */
    internal fun splitStreamTitle(text: String): Pair<String, String> {
        val dash = Regex("\\s+[-–—]\\s+").find(text) ?: return "" to text
        val artist = text.substring(0, dash.range.first).trim()
        val title = text.substring(dash.range.last + 1).trim()
        return if (artist.isBlank() || title.isBlank()) "" to text else artist to title
    }

    /** Returns a message to show, or null when no playlist is chosen for [key]. */
    private suspend fun addToPlaylist(
        context: Context,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        missingMessage: Int,
        title: String,
        artist: String,
        youtubeVideoId: String?,
    ): String? {
        val result = SongQuery(title, artist, youtubeVideoId)
        val playlistId = context.dataStore.data.first()[key]?.takeIf { it.isNotBlank() } ?: return null
        val access = EntryPointAccessors.fromApplication(context, Access::class.java)
        val database = access.database()
        val playlist = database.playlist(playlistId).firstOrNull()
            ?: return context.getString(missingMessage)
        // Already in the playlist under the same name and artist (maybe a different upload)? Then skip it.
        val existing = database.playlistSongs(playlist.id).first()
        if (existing.any { sameSong(it.song.title, it.song.artists.map { a -> a.name }, result.title, result.artist) }) {
            return context.getString(R.string.recognised_already_in, result.title, playlist.playlist.name)
        }
        val song = findOnYouTube(result)
            ?: return context.getString(R.string.recognised_not_found, result.title)
        database.insert(song.toMediaMetadata())
        if (database.playlistDuplicates(playlist.id, listOf(song.id)).isNotEmpty() ||
            existing.any { sameSong(it.song.title, it.song.artists.map { a -> a.name }, song.title, song.artists.joinToString(" ") { it.name }) }
        ) {
            return context.getString(R.string.recognised_already_in, result.title, playlist.playlist.name)
        }
        database.addSongsToPlaylist(playlist, listOf(song.id to null))
        playlist.playlist.browseId?.let { browseId ->
            access.syncUtils().scheduleAddToPlaylist(browseId, playlist.id, listOf(song.id))
        }
        return context.getString(R.string.recognised_added, result.title, playlist.playlist.name)
    }

    /**
     * Same song by name and artist, ignoring case, punctuation and extras like "(Official Video)" or
     * "- Remastered": the core titles must match and at least one artist must overlap.
     */
    private fun sameSong(titleA: String, artistsA: List<String>, titleB: String, artistB: String): Boolean {
        fun core(title: String) = title.lowercase(Locale.ROOT)
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
            .substringBefore(" - ")
            .filter { it.isLetterOrDigit() }
        fun norm(text: String) = text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (core(titleA).isEmpty() || core(titleA) != core(titleB)) return false
        val b = norm(artistB)
        return artistsA.isEmpty() || b.isEmpty() || artistsA.any { a -> norm(a).isNotEmpty() && (b.contains(norm(a)) || norm(a).contains(b)) }
    }

    private class SongQuery(val title: String, val artist: String, val youtubeVideoId: String?)

    /** The song on YouTube Music: Shazam's own video if it gave one, else the best song match. */
    private suspend fun findOnYouTube(result: SongQuery): SongItem? {
        val songs = YouTube.search("${result.title} ${result.artist}".trim(), YouTube.SearchFilter.FILTER_SONG)
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
