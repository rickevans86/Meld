/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

/** A playlist's name and, for playlists synced from YouTube Music, its browse id. */
data class PlaylistFolderInfo(
    val name: String,
    val browseId: String?,
)
