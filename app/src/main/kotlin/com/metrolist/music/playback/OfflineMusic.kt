/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.constants.isAudiobookId
import com.metrolist.music.constants.isYouTubeMediaId
import com.metrolist.music.db.entities.Song

enum class OfflineMusicSource { YOUTUBE, SPOTIFY, SUBSONIC, MEDIA_SERVER }

internal fun offlineMusicSongs(songs: List<Song>, downloads: Map<String, Download>, source: OfflineMusicSource,
    spotifyYouTubeIds: Set<String> = emptySet()): List<Song> = songs.filter {
    downloads[it.id]?.state == Download.STATE_COMPLETED && !it.song.isEpisode && !isAudiobookId(it.id) &&
        when (source) {
            OfflineMusicSource.YOUTUBE -> isYouTubeMediaId(it.id)
            OfflineMusicSource.SPOTIFY -> it.id.startsWith("spotify:") || it.id in spotifyYouTubeIds
            OfflineMusicSource.SUBSONIC -> it.id.startsWith("subsonic:")
            OfflineMusicSource.MEDIA_SERVER -> it.id.startsWith("mediaserver:")
        }
}
