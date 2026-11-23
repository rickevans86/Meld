package com.metrolist.music.podcasts

import com.metrolist.innertube.models.SongItem
import java.io.IOException

/** Local state changes only after a successful remote removal. */
internal suspend fun removeSavedEpisodes(
    episodes: List<SongItem>,
    refreshTokens: suspend () -> List<SongItem>,
    removeRemote: suspend (String, String) -> Unit,
    onRemoved: suspend (SongItem) -> Unit,
) {
    val selected = episodes.distinctBy { it.id }
    val needsTokens = selected.any { it.setVideoId == null && !it.id.startsWith("podcast:") }
    val remote = if (needsTokens) refreshTokens().associateBy { it.id } else emptyMap()
    for (song in selected) {
        if (!song.id.startsWith("podcast:")) {
            val token = song.setVideoId ?: remote[song.id]?.setVideoId
            if (token != null) removeRemote(song.id, token)
            else throw IOException("Missing episode removal token")
        }
        onRemoved(song)
    }
}
