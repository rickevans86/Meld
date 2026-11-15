/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.metrolist.music.R
import com.metrolist.music.constants.PODCAST_EPISODE_ID_PREFIX
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.rememberPreference

/**
 * Wavelength: podcast skip buttons. When on, next and previous jump forward and back inside a
 * podcast episode (by the chosen number of seconds) instead of changing episode. On the phone,
 * holding the button changes episode instead.
 */
object PodcastSkip {
    val EnabledKey = booleanPreferencesKey("podcastSkipButtons")
    val ForwardKey = intPreferencesKey("podcastSkipForwardSeconds")
    val BackKey = intPreferencesKey("podcastSkipBackSeconds")

    // Dedicated fast-forward / rewind buttons in the player (always on for episodes, like audiobooks),
    // with their own amounts kept separate from the repurpose-next/previous skip above.
    val ButtonForwardKey = intPreferencesKey("podcastButtonForwardSeconds")
    val ButtonBackKey = intPreferencesKey("podcastButtonBackSeconds")

    const val DEFAULT_FORWARD = 30
    const val DEFAULT_BACK = 15

    /** The choices offered in settings, in seconds. */
    val CHOICES = listOf(5, 10, 15, 30, 45, 60, 90, 120)

    /** True for a podcast episode (including news bulletins), which is where the skip buttons apply. */
    fun isPodcast(mediaId: String?, isEpisode: Boolean): Boolean =
        isEpisode || mediaId?.startsWith(PODCAST_EPISODE_ID_PREFIX) == true

    /** "30 s", or "1 min 30 s" for longer jumps. */
    fun label(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }
}

/** Seconds to skip back and forward, when the skip buttons apply to what's playing; null otherwise. */
data class PodcastSkipAmounts(val back: Int, val forward: Int)

/** The dedicated podcast fast-forward / rewind amounts, for a podcast episode; null otherwise. */
@Composable
fun rememberPodcastSeekButtons(mediaMetadata: MediaMetadata?): PodcastSkipAmounts? {
    val (forward, _) = rememberPreference(PodcastSkip.ButtonForwardKey, PodcastSkip.DEFAULT_FORWARD)
    val (back, _) = rememberPreference(PodcastSkip.ButtonBackKey, PodcastSkip.DEFAULT_BACK)
    if (mediaMetadata == null) return null
    if (!PodcastSkip.isPodcast(mediaMetadata.id, mediaMetadata.isEpisode)) return null
    return PodcastSkipAmounts(back, forward)
}

@Composable
fun rememberPodcastSkip(mediaMetadata: MediaMetadata?): PodcastSkipAmounts? {
    val (enabled, _) = rememberPreference(PodcastSkip.EnabledKey, false)
    val (forward, _) = rememberPreference(PodcastSkip.ForwardKey, PodcastSkip.DEFAULT_FORWARD)
    val (back, _) = rememberPreference(PodcastSkip.BackKey, PodcastSkip.DEFAULT_BACK)
    if (!enabled || mediaMetadata == null) return null
    if (!PodcastSkip.isPodcast(mediaMetadata.id, mediaMetadata.isEpisode)) return null
    return PodcastSkipAmounts(back, forward)
}

/**
 * A skip icon: a circular arrow with the number of seconds in the middle, so the button shows it
 * jumps rather than changes track. Forward is the mirror image of back.
 */
@Composable
fun SkipIcon(forward: Boolean, seconds: Int, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Icon(
            painter = painterResource(R.drawable.replay),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size).scale(scaleX = if (forward) -1f else 1f, scaleY = 1f),
        )
        Text(
            text = seconds.toString(),
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (seconds >= 100) 0.24f else 0.3f).sp,
            modifier = Modifier.padding(top = (size.value * 0.08f).dp),
        )
    }
}

/**
 * A dedicated rewind / fast-forward row for the podcast player, mirroring the audiobook seek buttons.
 * [onSeek] receives the signed number of seconds to jump.
 */
@Composable
fun PodcastSeekRow(back: Int, forward: Int, color: Color, onSeek: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onSeek(-back) }) {
            Box(Modifier.size(40.dp)) { SkipIcon(forward = false, seconds = back, tint = color, size = 40.dp) }
        }
        Spacer(Modifier.width(56.dp))
        IconButton(onClick = { onSeek(forward) }) {
            Box(Modifier.size(40.dp)) { SkipIcon(forward = true, seconds = forward, tint = color, size = 40.dp) }
        }
    }
}
