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
 * Auto-download for playlists (⋮ → Auto-download new songs): every few hours, YouTube-synced
 * playlists are refreshed from YouTube and any song in them that isn't downloaded yet is downloaded
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
        val ids = applicationContext.dataStore.data.first()[AutoDownloadPlaylistsKey].orEmpty()
        if (ids.isEmpty()) return Result.success()
        val access = EntryPointAccessors.fromApplication(applicationContext, Access::class.java)
        val database = access.database()
        val downloads = access.downloadUtil()

        val started = mutableListOf<String>()
        for (id in ids) {
            runCatching {
                val playlist = database.playlist(id).firstOrNull() ?: return@runCatching
                // Pull in new songs added on YouTube first
                playlist.playlist.browseId?.let { browseId ->
                    runCatching { access.syncUtils().syncPlaylistSuspend(browseId, playlist.id) }
                        .onFailure { Timber.tag(TAG).w(it, "Couldn't refresh ${playlist.playlist.name}") }
                }
                database.playlistSongs(playlist.id).first().map { it.song }.forEach { song ->
                    val state = downloads.downloads.value[song.id]?.state
                    if (state != Download.STATE_COMPLETED && state != Download.STATE_QUEUED && state != Download.STATE_DOWNLOADING) {
                        downloads.downloadInBackground(song)
                        started += song.id
                    }
                }
            }.onFailure { Timber.tag(TAG).w(it, "Auto-download failed for playlist $id") }
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

        /** Ids of playlists whose new songs download automatically. */
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

        suspend fun setEnabled(context: Context, playlistId: String, enabled: Boolean) {
            context.dataStore.edit { prefs ->
                val current = prefs[AutoDownloadPlaylistsKey].orEmpty()
                prefs[AutoDownloadPlaylistsKey] = if (enabled) current + playlistId else current - playlistId
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
