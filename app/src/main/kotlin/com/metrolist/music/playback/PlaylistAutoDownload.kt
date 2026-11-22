/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.media3.exoplayer.offline.Download
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.metrolist.music.db.MusicDatabase
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.utils.completed
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.constants.OfflineModeKey
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.SpotifyTokenManager
import com.metrolist.spotify.Spotify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.metrolist.music.utils.SyncUtils
import com.metrolist.music.utils.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Auto-download for playlists (⋮ → Auto-download new songs): every few hours, enabled local, YouTube and
 * Spotify playlists are checked and any song in them that isn't downloaded yet is downloaded
 * (so new songs arrive offline). Switching it on also downloads what's already there. Downloads follow
 * the Wi-Fi-only setting in Storage/Download.
 */
class PlaylistAutoDownload(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun database(): MusicDatabase
        fun syncUtils(): SyncUtils
        fun downloadUtil(): DownloadUtil
    }

    override suspend fun doWork(): Result {
        val prefs = applicationContext.dataStore.data.first()
        val ids = prefs[AutoDownloadPlaylistsKey].orEmpty()
        if (ids.isEmpty() || prefs[OfflineModeKey] == true) return Result.success()
        val access = EntryPointAccessors.fromApplication(applicationContext, Access::class.java)
        val database = access.database()
        val downloads = access.downloadUtil()

        val started = mutableListOf<String>()
        val seenYouTube = mutableSetOf<String>()
        suspend fun active(id: String): Boolean {
            currentCoroutineContext().ensureActive()
            val current = applicationContext.dataStore.data.first()
            return current[OfflineModeKey] != true && id in current[AutoDownloadPlaylistsKey].orEmpty()
        }
        suspend fun queue(metadata: MediaMetadata, folder: String, source: String, selection: String) {
            if (!active(selection)) return
            if (database.getSongByIdBlocking(metadata.id) == null) database.insert(metadata)
            val song = database.getSongByIdBlocking(metadata.id) ?: return
            val state = downloads.downloads.value[song.id]?.state
            downloads.downloadInBackground(song, exportFolder = folder, sourcePlaylistId = source)
            if (shouldPrepareDownload(state)) started += song.id
        }
        suspend fun localPlaylist(localId: String, selection: String) {
            val playlist = database.playlist(localId).firstOrNull() ?: return
            playlist.playlist.browseId?.let { browseId ->
                if (!seenYouTube.add(browseId.removePrefix("VL"))) return
                access.syncUtils().syncPlaylistSuspend(browseId, playlist.id)
            }
            val folder = if (playlist.playlist.browseId != null) "YouTube - ${playlist.playlist.name}" else playlist.playlist.name
            for (song in database.playlistSongs(playlist.id).first().map { it.song }) {
                if (!active(selection)) break
                queue(song.toMediaMetadata(), folder, playlist.id, selection)
            }
        }
        val mapper = SpotifyYouTubeMapper(database)
        for (id in ids) {
            if (!active(id)) continue
            try {
                when {
                    id == LIKED_ID -> {
                        access.syncUtils().syncLikedSongsSuspend()
                        for (song in database.likedSongs(com.metrolist.music.constants.SongSortType.CREATE_DATE, true).first()) {
                            if (!active(id)) break
                            queue(song.toMediaMetadata(), "YouTube - Liked songs", id, id)
                        }
                    }
                    id.startsWith(YOUTUBE_PREFIX) -> {
                        val remote = id.removePrefix(YOUTUBE_PREFIX).removePrefix("VL")
                        val local = database.playlistByBrowseId(remote).first()
                            ?: database.playlistByBrowseId("VL$remote").first()
                        if (local != null) localPlaylist(local.id, id) else if (seenYouTube.add(remote)) {
                            val page = YouTube.playlist(remote).completed().getOrThrow()
                            for (song in page.songs) {
                                if (!active(id)) break
                                queue(song.toMediaMetadata(), "YouTube - ${page.playlist.title}", id, id)
                            }
                        }
                    }
                    id.startsWith(SPOTIFY_PREFIX) -> {
                        if (!SpotifyTokenManager.ensureAuthenticated()) continue
                        val remote = id.removePrefix(SPOTIFY_PREFIX)
                        val playlist = Spotify.playlist(remote).getOrThrow()
                        var offset = 0
                        do {
                            if (!active(id)) break
                            val page = Spotify.playlistTracks(remote, limit = 100, offset = offset).getOrThrow()
                            for (item in page.items) {
                                if (!active(id)) break
                                val track = item.track?.takeUnless { item.isLocal } ?: continue
                                val metadata = mapper.mapToYouTube(track) ?: continue
                                queue(metadata, "Spotify - ${playlist.name}", id, id)
                            }
                            offset += 100
                        } while (offset < page.total)
                    }
                    else -> localPlaylist(id, id) // Keep existing saved local IDs compatible.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Auto-download failed for playlist $id")
            }
        }

        // The download manager runs in this process: stay alive (up to ~9 minutes) while files download
        withTimeoutOrNull(9 * 60 * 1000L) {
            while (started.any { songId ->
                    downloads.downloads.value[songId]?.state.let {
                        it == null || it == Download.STATE_QUEUED || it == Download.STATE_DOWNLOADING || it == Download.STATE_RESTARTING
                    }
                }
            ) {
                delay(5_000)
            }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "PlaylistAutoDownload"
        private const val PERIODIC = "playlist_auto_download"
        private const val NOW = "playlist_auto_download_now"

        /** Id used for Liked songs (not a playlist in the database). */
        const val LIKED_ID = "liked"
        const val YOUTUBE_PREFIX = "youtube:"
        const val SPOTIFY_PREFIX = "spotify:"

        fun sourceId(localId: String?, browseId: String?): String = when (val remote = browseId?.removePrefix("VL")) {
            "LM" -> LIKED_ID
            null -> requireNotNull(localId)
            else -> YOUTUBE_PREFIX + remote
        }

        internal fun enabledIds(current: Set<String>, id: String, enabled: Boolean, aliases: Set<String>): Set<String> =
            (current - aliases - id).let { if (enabled) it + id else it }

        /** Ids of playlists whose new songs download automatically ([LIKED_ID] for Liked songs). */
        val AutoDownloadPlaylistsKey = stringSetPreferencesKey("wavelengthAutoDownloadPlaylists")

        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Checks every 6 hours; [runNow] also checks straight away. */
        fun schedule(context: Context, runNow: Boolean = false) {
            val work = WorkManager.getInstance(context.applicationContext)
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PlaylistAutoDownload>(6, TimeUnit.HOURS).setConstraints(online).build(),
            )
            if (runNow) {
                work.enqueueUniqueWork(
                    NOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<PlaylistAutoDownload>().setConstraints(online).build(),
                )
            }
        }

        suspend fun setEnabled(context: Context, playlistId: String, enabled: Boolean, aliases: Set<String> = emptySet()) {
            context.dataStore.edit { prefs ->
                val current = prefs[AutoDownloadPlaylistsKey].orEmpty()
                prefs[AutoDownloadPlaylistsKey] = enabledIds(current, playlistId, enabled, aliases)
            }
            if (enabled) schedule(context, runNow = true)
        }

        @Composable
        fun rememberEnabledIds(): Set<String> {
            val context = LocalContext.current
            val flow = remember(context) { context.dataStore.data.map { it[AutoDownloadPlaylistsKey].orEmpty() } }
            val ids by flow.collectAsState(initial = emptySet())
            return ids
        }
    }
}
