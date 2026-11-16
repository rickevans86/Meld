/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import com.metrolist.music.R

internal object AutoPodcastFavourites {
    // Public Android Auto browse-action result key; Media3 does not expose this legacy constant.
    const val REFRESH_ITEM = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_REFRESH_ITEM"
    const val ADD = "FAVOURITE_PODCAST_CHANNEL"
    const val REMOVE = "UNFAVOURITE_PODCAST_CHANNEL"
    val AddCommand = SessionCommand(ADD, Bundle.EMPTY)
    val RemoveCommand = SessionCommand(REMOVE, Bundle.EMPTY)

    fun buttons(context: Context) = listOf(
        CommandButton.Builder(CommandButton.ICON_STAR_UNFILLED)
            .setDisplayName(context.getString(R.string.podcast_favourite_channel))
            .setSessionCommand(AddCommand)
            .setIconUri(icon(context, R.drawable.star_outline)).build(),
        CommandButton.Builder(CommandButton.ICON_STAR_FILLED)
            .setDisplayName(context.getString(R.string.podcast_unfavourite_channel))
            .setSessionCommand(RemoveCommand)
            .setIconUri(icon(context, R.drawable.star)).build(),
    )

    fun decorate(item: MediaItem, favourite: Boolean): MediaItem = item.buildUpon()
        .setMediaMetadata(item.mediaMetadata.buildUpon()
            .setTitle(if (favourite) "★ ${item.mediaMetadata.title}" else item.mediaMetadata.title)
            .setSupportedCommands(listOf(if (favourite) REMOVE else ADD)).build())
        .build()

    private fun icon(context: Context, resource: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).authority(context.packageName)
        .appendPath(resource.toString()).build()
}
