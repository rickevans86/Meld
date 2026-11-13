/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.metrolist.music.R
import com.metrolist.music.audiobooks.AudiobookBookmark
import com.metrolist.music.audiobooks.AudiobookBookmarks
import com.metrolist.music.audiobooks.AudiobookPlaybackSettings
import com.metrolist.music.audiobooks.audiobookSeekPosition
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.podcasts.SkipIcon
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun AudiobookPlayerControls(
    connection: PlayerConnection,
    isPlaying: Boolean,
    enabled: Boolean,
    positionMs: Long,
    color: Color,
    onPlayPause: () -> Unit,
    onSleepTimer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = connection.player
    val windows by connection.queueWindows.collectAsStateWithLifecycle()
    val currentId = player.currentMediaItem?.mediaId
    val storedBack by rememberPreference(AudiobookPlaybackSettings.BackKey, AudiobookPlaybackSettings.DEFAULT_BACK)
    val storedForward by rememberPreference(AudiobookPlaybackSettings.ForwardKey, AudiobookPlaybackSettings.DEFAULT_FORWARD)
    val back = AudiobookPlaybackSettings.seconds(storedBack, AudiobookPlaybackSettings.DEFAULT_BACK)
    val forward = AudiobookPlaybackSettings.seconds(storedForward, AudiobookPlaybackSettings.DEFAULT_FORWARD)
    val localChapterIds = currentId?.let { connection.service.audiobookRepository.bookForChapter(it)?.chapterIds?.toSet() }
    val chapters = connection.service.absRepository.chaptersForTrack(currentId).ifEmpty {
        windows.filter {
            val id = it.mediaItem.mediaId
            when {
                localChapterIds != null -> id in localChapterIds
                com.metrolist.music.constants.isAbsTrackId(currentId) -> id.substringBeforeLast(':') == currentId?.substringBeforeLast(':')
                else -> id == currentId
            }
        }.map {
            com.metrolist.music.audiobooks.AudiobookChapter(it.mediaItem.mediaId, it.mediaItem.mediaMetadata.title?.toString().orEmpty(), 0L)
        }
    }
    val chapterIds = chapters.map { it.mediaId }.toSet() + listOfNotNull(currentId)
    val currentChapter = chapters.indexOfLast { it.mediaId == currentId && it.positionMs <= positionMs }.coerceAtLeast(0)
    val bookmarkStore = remember(context) { AudiobookBookmarks(context.dataStore) }
    val allBookmarks by bookmarkStore.bookmarks.collectAsStateWithLifecycle(initialValue = emptyList())
    val bookmarks = allBookmarks.filter { it.mediaId in chapterIds }
    var dialog by rememberSaveable(currentId) { mutableStateOf<String?>(null) }
    var speed by remember(player) { mutableFloatStateOf(player.playbackParameters.speed) }
    val failureMessage = stringResource(R.string.audiobook_bookmark_failed)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    fun seekChapter(mediaId: String, position: Long) {
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == mediaId } ?: return
        player.seekTo(index, position)
    }
    fun changeBookmark(bookmark: AudiobookBookmark?, removeId: String? = null) {
        scope.launch {
            try {
                if (bookmark != null) bookmarkStore.add(bookmark)
                if (removeId != null) bookmarkStore.remove(removeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                timber.log.Timber.w(e, "Audiobook bookmark change failed")
                Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
    CompositionLocalProvider(LocalContentColor provides color) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            chapters.getOrNull(currentChapter)?.let { chapter ->
                Text(chapter.title, modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = enabled && currentChapter > 0,
                    onClick = { chapters.getOrNull(currentChapter - 1)?.let { seekChapter(it.mediaId, it.positionMs) } },
                ) {
                    Icon(painterResource(R.drawable.skip_previous), stringResource(R.string.audiobook_previous_chapter))
                }
                IconButton(enabled = enabled, onClick = { player.seekTo(audiobookSeekPosition(player.currentPosition, -back, player.duration)) }) {
                    androidx.compose.foundation.layout.Box(Modifier.size(40.dp).audiobookSkipDescription(stringResource(R.string.audiobook_rewind_by, back))) {
                        SkipIcon(false, back, color, 40.dp)
                    }
                }
                FilledIconButton(onClick = onPlayPause, enabled = enabled, modifier = Modifier.size(72.dp)) {
                    Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play), stringResource(if (isPlaying) R.string.pause else R.string.play), Modifier.size(36.dp))
                }
                IconButton(enabled = enabled, onClick = { player.seekTo(audiobookSeekPosition(player.currentPosition, forward, player.duration)) }) {
                    androidx.compose.foundation.layout.Box(Modifier.size(40.dp).audiobookSkipDescription(stringResource(R.string.audiobook_forward_by, forward))) {
                        SkipIcon(true, forward, color, 40.dp)
                    }
                }
                IconButton(
                    enabled = enabled && currentChapter < chapters.lastIndex,
                    onClick = { chapters.getOrNull(currentChapter + 1)?.let { seekChapter(it.mediaId, it.positionMs) } },
                ) {
                    Icon(painterResource(R.drawable.skip_next), stringResource(R.string.audiobook_next_chapter))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = enabled, onClick = { dialog = "chapters" }) {
                    Icon(painterResource(R.drawable.list), stringResource(R.string.audiobook_chapters))
                }
                IconButton(enabled = enabled, onClick = onSleepTimer) {
                    Icon(painterResource(R.drawable.timer), stringResource(R.string.sleep_timer))
                }
                IconButton(enabled = enabled, onClick = { dialog = "bookmarks" }) {
                    Icon(painterResource(R.drawable.audiobook_bookmark), stringResource(R.string.audiobook_bookmarks))
                }
                TextButton(enabled = enabled, onClick = { dialog = "speed" }) {
                    Text(stringResource(R.string.audiobook_speed_label, String.format(Locale.getDefault(), "%.2f", speed)), color = color)
                }
            }
        }
    }
    when (dialog) {
        "chapters" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.audiobook_chapters)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(chapters) { chapter ->
                        Text(
                            chapter.title,
                            color = if (chapter == chapters.getOrNull(currentChapter)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { seekChapter(chapter.mediaId, chapter.positionMs); dialog = null }.padding(vertical = 16.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) } },
        )
        "bookmarks" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.audiobook_bookmarks)) },
            text = {
                Column {
                    TextButton(onClick = {
                        currentId?.let { id ->
                            changeBookmark(AudiobookBookmark(mediaId = id, chapter = chapters.getOrNull(currentChapter)?.title.orEmpty(), positionMs = player.currentPosition.coerceAtLeast(0)))
                        }
                    }) { Text(stringResource(R.string.audiobook_add_bookmark)) }
                    if (bookmarks.isEmpty()) Text(stringResource(R.string.audiobook_no_bookmarks))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${bookmark.chapter} · ${makeTimeString(bookmark.positionMs)}",
                                    modifier = Modifier.weight(1f).clickable { seekChapter(bookmark.mediaId, bookmark.positionMs); dialog = null }.padding(vertical = 16.dp),
                                )
                                IconButton(onClick = { changeBookmark(null, bookmark.id) }) {
                                    Icon(painterResource(R.drawable.delete), stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) } },
        )
        "speed" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.speed)) },
            text = {
                Column {
                    Text(stringResource(R.string.audiobook_speed_label, String.format(Locale.getDefault(), "%.2f", speed)))
                    Slider(value = speed.coerceIn(0.5f, 3f), onValueChange = { player.setPlaybackSpeed(it) }, valueRange = 0.5f..3f, steps = 9)
                }
            },
            dismissButton = { TextButton(onClick = { player.setPlaybackSpeed(1f) }) { Text(stringResource(R.string.reset)) } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

private fun Modifier.audiobookSkipDescription(label: String): Modifier =
    this.semantics { contentDescription = label }
