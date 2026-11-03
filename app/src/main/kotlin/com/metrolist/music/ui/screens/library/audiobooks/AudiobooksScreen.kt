/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library.audiobooks

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AudiobooksSourceKey
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AudiobookDetail
import com.metrolist.music.viewmodels.AudiobookSummary
import com.metrolist.music.viewmodels.AudiobooksViewModel
import kotlin.math.roundToInt

@Composable
fun AudiobooksScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    filterContent: @Composable () -> Unit,
) {
    var source by rememberPreference(AudiobooksSourceKey, SOURCE_DEVICE)
    val header: @Composable () -> Unit = {
        Column {
            filterContent()
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                FilterChip(
                    selected = source != SOURCE_ABS,
                    onClick = { source = SOURCE_DEVICE },
                    label = { Text(stringResource(R.string.audiobooks_source_device)) },
                )
                FilterChip(
                    selected = source == SOURCE_ABS,
                    onClick = { source = SOURCE_ABS },
                    label = { Text(stringResource(R.string.audiobooks_source_abs)) },
                )
            }
        }
    }
    if (source == SOURCE_ABS) {
        AudiobookshelfContent(header = header)
    } else {
        DeviceAudiobooks(header = header)
    }
}

private const val SOURCE_DEVICE = "device"
private const val SOURCE_ABS = "audiobookshelf"

@Composable
private fun DeviceAudiobooks(
    header: @Composable () -> Unit,
    viewModel: AudiobooksViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()

    val hasFolder by viewModel.hasFolder.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val summaries by viewModel.summaries.collectAsState()
    val openBookId by viewModel.openBookId.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val lazyListState = rememberLazyListState()

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val granted = runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!granted) return@rememberLauncherForActivityResult
        viewModel.currentFolder()?.takeIf { it != uri }?.let { old ->
            runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
        }
        viewModel.setFolder(uri)
    }

    // Positions are written by the player, so re-read them whenever the track changes or the screen returns
    LaunchedEffect(mediaMetadata?.id, isPlaying) { viewModel.refresh() }
    LaunchedEffect(openBookId) { lazyListState.scrollToItem(0) }
    BackHandler(enabled = openBookId != null) { viewModel.openBookId.value = null }

    fun play(detail: AudiobookDetail, index: Int) {
        if (detail.chapters.isEmpty()) return
        val chapterId = detail.chapters.getOrNull(index)?.id
        if (chapterId != null && chapterId == mediaMetadata?.id) {
            playerConnection.togglePlayPause()
            return
        }
        playerConnection.playQueue(
            ListQueue(
                title = detail.summary.book.title,
                items = detail.chapters.map { it.toMediaItem() },
                startIndex = index.coerceIn(0, detail.chapters.lastIndex),
            ),
        )
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "filter", contentType = CONTENT_TYPE_HEADER) { header() }

        if (!hasFolder) {
            item(key = "setup") {
                SetupPrompt(onChooseFolder = { folderPicker.launch(null) })
            }
            return@LazyColumn
        }

        val book = detail
        if (openBookId != null && book != null) {
            item(key = "book_header") {
                BookHeader(
                    detail = book,
                    isCurrentBookPlaying = isPlaying && book.chapters.any { it.id == mediaMetadata?.id },
                    onBack = { viewModel.openBookId.value = null },
                    onContinue = { play(book, book.summary.resumeIndex) },
                    onToggleFinished = { viewModel.setFinished(book.summary.book, !book.summary.finished) },
                )
            }
            itemsIndexed(book.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                ChapterRow(
                    number = index + 1,
                    chapter = chapter,
                    isCurrent = chapter.id == mediaMetadata?.id,
                    isResumePoint = book.summary.started && !book.summary.finished && index == book.summary.resumeIndex,
                    onClick = { play(book, index) },
                )
            }
            return@LazyColumn
        }

        item(key = "header", contentType = CONTENT_TYPE_HEADER) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.n_audiobook, summaries.size, summaries.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (isScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                } else {
                    IconButton(onClick = viewModel::rescan) {
                        Icon(
                            painter = painterResource(R.drawable.refresh),
                            contentDescription = stringResource(R.string.audiobooks_rescan),
                        )
                    }
                }
                IconButton(onClick = { folderPicker.launch(null) }) {
                    Icon(
                        painter = painterResource(R.drawable.folder),
                        contentDescription = stringResource(R.string.audiobooks_change_folder),
                    )
                }
            }
        }

        if (summaries.isEmpty() && !isScanning) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.audiobooks_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        }

        items(summaries, key = { it.book.id }) { summary ->
            BookRow(
                summary = summary,
                onClick = { viewModel.openBookId.value = summary.book.id },
            )
        }
    }
}

@Composable
private fun SetupPrompt(onChooseFolder: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.audiobook),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(R.string.audiobooks_setup_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.audiobooks_setup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onChooseFolder) {
            Text(stringResource(R.string.audiobooks_choose_folder))
        }
    }
}

@Composable
private fun BookCover(url: String?, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.audiobook),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusText(summary: AudiobookSummary): String {
    val count = summary.book.chapterIds.size
    return when {
        summary.finished -> stringResource(R.string.audiobook_finished)
        summary.started -> stringResource(
            R.string.audiobook_progress,
            summary.resumeIndex + 1,
            count,
            (summary.fractionListened * 100).roundToInt(),
        )
        else -> pluralStringResource(R.plurals.n_audiobook_chapter, count, count)
    }
}

@Composable
private fun BookRow(summary: AudiobookSummary, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        BookCover(summary.book.coverUri, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.book.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            summary.book.author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = statusText(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (summary.started && !summary.finished) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { summary.fractionListened },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
    }
}

@Composable
private fun BookHeader(
    detail: AudiobookDetail,
    isCurrentBookPlaying: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onToggleFinished: () -> Unit,
) {
    val summary = detail.summary
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.audiobooks_back),
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            BookCover(summary.book.coverUri, 112.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = summary.book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                summary.book.author?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    text = statusText(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (summary.started && !summary.finished) {
            LinearProgressIndicator(
                progress = { summary.fractionListened },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(4.dp),
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = onContinue,
            enabled = detail.chapters.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Icon(painter = painterResource(R.drawable.play), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    isCurrentBookPlaying -> stringResource(R.string.audiobook_pause)
                    summary.finished -> stringResource(R.string.audiobook_listen_again)
                    summary.started -> stringResource(R.string.audiobook_continue, summary.resumeIndex + 1)
                    else -> stringResource(R.string.audiobook_start)
                }
            )
        }
        OutlinedButton(
            onClick = onToggleFinished,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(if (summary.finished) R.drawable.radio_button_unchecked else R.drawable.check_circle),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (summary.finished) R.string.mark_unfinished else R.string.mark_finished))
        }
        Text(
            text = stringResource(R.string.audiobook_chapters),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ChapterRow(
    number: Int,
    chapter: Song,
    isCurrent: Boolean,
    isResumePoint: Boolean,
    onClick: () -> Unit,
) {
    val position = chapter.song.playbackPosition ?: 0L
    val durationMs = chapter.song.duration.coerceAtLeast(0) * 1000L
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (isCurrent) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent || isResumePoint) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrent || isResumePoint) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (position > 0 && durationMs > 0) {
                Text(
                    text = stringResource(R.string.audiobook_time_left, formatDuration(durationMs - position)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (durationMs > 0) {
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
