/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.db.entities.Playlist

/** Where a playlist comes from, shown as a small logo next to it in the Library. */
enum class PlaylistSource(val logo: Int) {
    YOUTUBE(R.drawable.logo_youtube),
    SPOTIFY(R.drawable.logo_spotify),
}

/**
 * Spotify rows use ids starting "spotify_" (and Spotify liked songs); YouTube playlists have a YouTube
 * browse id, and Liked songs is the YouTube likes. Playlists made only in the app have no logo.
 */
@Composable
fun playlistSourceOf(playlist: Playlist): PlaylistSource? {
    val name = playlist.playlist.name
    return when {
        playlist.id.startsWith("spotify") || name == stringResource(R.string.spotify_liked_songs) -> PlaylistSource.SPOTIFY
        playlist.playlist.browseId != null || name == stringResource(R.string.liked) -> PlaylistSource.YOUTUBE
        else -> null
    }
}

@Composable
fun PlaylistSourceLogo(source: PlaylistSource, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(source.logo),
        contentDescription = when (source) {
            PlaylistSource.YOUTUBE -> "YouTube"
            PlaylistSource.SPOTIFY -> "Spotify"
        },
        modifier = modifier.size(size),
    )
}
