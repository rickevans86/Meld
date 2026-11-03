/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.radio

import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * For a radio station that announces its song, shows the song as the title and the station as
 * the artist, as the notification and Android Auto do. Anything else passes through unchanged.
 */
fun Flow<MediaMetadata?>.withRadioNowPlaying(): Flow<MediaMetadata?> =
    combine(this, RadioDirectory.nowPlayingTitle) { metadata, nowPlaying ->
        if (metadata != null && nowPlaying != null && nowPlaying.first == metadata.id) {
            metadata.copy(title = nowPlaying.second, artists = listOf(MediaMetadata.Artist(id = null, name = metadata.title)))
        } else {
            metadata
        }
    }
