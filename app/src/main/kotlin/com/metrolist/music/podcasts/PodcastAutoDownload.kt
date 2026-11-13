/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import android.content.Context
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
import com.metrolist.music.playback.DownloadUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Downloads new episodes of shows with auto-download on, over unmetered Wi-Fi only, and removes
 * the auto-downloaded ones that have dropped out of the newest few. Played episodes are skipped.
 */
class PodcastAutoDownload(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun podcastDirectory(): PodcastDirectory
        fun downloadUtil(): DownloadUtil
    }

    override suspend fun doWork(): Result {
        val access = EntryPointAccessors.fromApplication(applicationContext, Access::class.java)
        val directory = access.podcastDirectory()
        val downloads = access.downloadUtil()
        PlayedEpisodes.init(applicationContext)
        val shows = directory.followed.value.filter { it.autoDownload }
        if (shows.isEmpty()) return Result.success()

        val started = mutableListOf<String>()
        for (show in shows) {
            runCatching {
                val feed = directory.feed(show)
                val newest = feed.episodes.take(show.autoDownloadKeep)
                val songs = directory.prepare(feed.copy(episodes = newest))
                val keepIds = songs.map { it.id }.toSet()
                songs.filterNot { PlayedEpisodes.isPlayed(it.id) }.forEach { song ->
                    if (downloads.downloads.value[song.id]?.state != Download.STATE_COMPLETED) {
                        downloads.downloadInBackground(song)
                        started += song.id
                    }
                }
                // Remove only what auto-download itself fetched and is no longer among the newest
                show.autoDownloadedIds.filterNot { it in keepIds }.forEach { downloads.removeInBackground(it) }
                directory.setAutoDownloaded(show.feedUrl, keepIds.toList())
            }.onFailure { Timber.tag(TAG).w(it, "Auto-download failed for ${show.title}") }
        }

        // The download manager runs in this process: stay alive (up to ~9 minutes) while files download
        withTimeoutOrNull(9 * 60 * 1000L) {
            while (started.any { id ->
                    downloads.downloads.value[id]?.state.let {
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
        private const val TAG = "PodcastAutoDownload"
        private const val PERIODIC = "podcast_auto_download"
        private const val NOW = "podcast_auto_download_now"

        private val wifiOnly = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()

        /** Checks every 6 hours on Wi-Fi; [runNow] also checks as soon as Wi-Fi is available. */
        fun schedule(context: Context, runNow: Boolean = false) {
            val work = WorkManager.getInstance(context.applicationContext)
            work.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PodcastAutoDownload>(6, TimeUnit.HOURS).setConstraints(wifiOnly).build(),
            )
            if (runNow) {
                work.enqueueUniqueWork(
                    NOW,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<PodcastAutoDownload>().setConstraints(wifiOnly).build(),
                )
            }
        }
    }
}
