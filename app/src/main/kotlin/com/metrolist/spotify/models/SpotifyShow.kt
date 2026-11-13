package com.metrolist.spotify.models

/** A podcast (show) saved in the user's Spotify library. */
data class SpotifyShow(
    val id: String,
    val name: String,
    val publisher: String? = null,
    val imageUrl: String? = null,
    val uri: String,
)
