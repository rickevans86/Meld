/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.constants.isYouTubeMediaId
import com.metrolist.music.db.entities.Song

/** One source of truth for the Auto downloads count, browse pages and playback/shuffle queue. */
internal fun autoDownloadedYouTubeSongs(songs: List<Song>, downloads: Map<String, Download>): List<Song> =
    songs.filter {
        isYouTubeMediaId(it.id) && !it.song.isLocal && !it.song.isEpisode &&
            downloads[it.id]?.state == Download.STATE_COMPLETED
    }.sortedBy { downloads[it.id]?.updateTimeMs ?: 0L }
