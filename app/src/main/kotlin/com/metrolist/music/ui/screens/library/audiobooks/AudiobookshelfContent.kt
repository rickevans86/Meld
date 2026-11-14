/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library.audiobooks

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.audiobooks.AudiobookSort
import com.metrolist.music.audiobooks.AudiobookTab
import com.metrolist.music.audiobooks.abs.AbsBook
import com.metrolist.music.audiobooks.abs.AbsBookSummary
import com.metrolist.music.audiobooks.abs.AbsDownloadState
import com.metrolist.music.audiobooks.abs.AbsProgress
import com.metrolist.music.constants.isAbsTrackId
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.viewmodels.AudiobookshelfViewModel
import kotlin.math.roundToInt

@Composable
fun AudiobookshelfContent(
    header: @Composable () -> Unit,
    viewModel: AudiobookshelfViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()

    val session by viewModel.session.collectAsState()
    val signInExpired by viewModel.signInExpired.collectAsState()
    val libraries by viewModel.libraries.collectAsState()
    val libraryId by viewModel.libraryId.collectAsState()
    val inProgress by viewModel.inProgress.collectAsState()
    val books by viewModel.books.collectAsState()
    val totalBooks by viewModel.totalBooks.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val error by viewModel.error.collectAsState()
    val openBookId by viewModel.openBookId.collectAsState()
    val openBook by viewModel.openBook.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val downloadedBooks by viewModel.downloadedBooks.collectAsState()
    var inlineQuery by rememberSaveable { mutableStateOf("") }
    // On the Audiobooks tab the search is the top-bar search button; elsewhere (Library) it stays in the page
    val navRoute = com.metrolist.music.LocalNavController.current.currentBackStackEntry?.destination?.route
    val onAudiobooksTab = navRoute == "audiobooks"
    val pageQuery = com.metrolist.music.ui.component.rememberPageSearchQuery("audiobooks")
    com.metrolist.music.ui.component.PageSearchEffect(pageQuery) { if (onAudiobooksTab) viewModel.search(it) }
    val query = if (onAudiobooksTab) pageQuery else inlineQuery
    val listState = rememberLazyListState()
    val tabs = rememberAudiobookTabState()
    LaunchedEffect(tabs.sort) { viewModel.setSort(tabs.sort) }

    val playingItemId = mediaMetadata?.id?.takeIf(::isAbsTrackId)?.removePrefix("audiobook:abs:")?.substringBeforeLast(':')

    LaunchedEffect(openBookId) { listState.scrollToItem(0) }
    // The server's progress changes while a book plays, so re-read it when playback pauses or the book changes
    LaunchedEffect(playingItemId, isPlaying) { if (session != null && !isPlaying) viewModel.refreshProgress() }
    BackHandler(enabled = openBookId != null) { viewModel.close() }

    fun play(itemId: String, startAtSec: Double?) {
        if (startAtSec == null && itemId == playingItemId) {
            playerConnection.togglePlayPause()
            return
        }
        viewModel.play(itemId, startAtSec) { prepared ->
            playerConnection.playQueue(
                ListQueue(
                    title = prepared.book.title,
                    items = prepared.songs.map { it.toMediaItem() },
                    startIndex = prepared.startIndex,
                ),
            )
        }
    }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        if (session != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.dismissError()
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "header") { header() }

        val current = session
        if (current == null) {
            item(key = "sign_in") {
                SignInForm(
                    busy = busy,
                    error = error,
                    onSignIn = viewModel::signIn,
                    onSignInWithKey = viewModel::signInWithApiKey,
                )
            }
            return@LazyColumn
        }

        if (signInExpired) {
            item(key = "expired") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.abs_sign_in_expired),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Button(onClick = viewModel::signOut) { Text(stringResource(R.string.abs_sign_in_again)) }
                }
            }
        }

        val book = openBook
        if (openBookId != null) {
            if (book == null) {
                item(key = "book_loading") { LoadingRow() }
            } else {
                bookDetail(
                    book = book,
                    progress = progress[book.id],
                    coverUrl = viewModel.coverUrl(book.id),
                    isThisBookPlaying = isPlaying && playingItemId == book.id,
                    busy = busy,
                    download = downloads[book.id],
                    onBack = viewModel::close,
                    onPlay = { startAt -> play(book.id, startAt) },
                    onDownload = { viewModel.download(book.id) },
                    onRemoveDownload = { viewModel.removeDownload(book.id) },
                    onToggleFinished = { viewModel.setFinished(book, progress[book.id]?.isComplete(book.durationSec) != true) },
                )
            }
            return@LazyColumn
        }

        item(key = "account") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.serverUrl.substringAfter("://"),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (current.username.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.abs_signed_in_as, current.username),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                } else {
                    IconButton(onClick = viewModel::refreshAll) {
                        Icon(painterResource(R.drawable.refresh), contentDescription = stringResource(R.string.abs_refresh))
                    }
                }
                TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.abs_sign_out)) }
            }
        }

        if (libraries.size > 1) {
            item(key = "libraries") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    libraries.forEach { library ->
                        FilterChip(
                            selected = library.id == libraryId,
                            onClick = { viewModel.selectLibrary(library.id) },
                            label = { Text(library.name, maxLines = 1) },
                        )
                    }
                }
            }
        }

        if (!onAudiobooksTab) item(key = "search") {
            OutlinedTextField(
                value = inlineQuery,
                onValueChange = {
                    inlineQuery = it
                    viewModel.search(it)
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.abs_search)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        val results = searchResults
        if (query.isNotBlank() && results != null) {
            if (results.isEmpty()) {
                item(key = "no_results") { EmptyText(stringResource(R.string.abs_no_results)) }
            }
            items(results, key = { "search:${it.id}" }) { summary ->
                AbsBookRow(summary, progress[summary.id], viewModel.coverUrl(summary.id), downloads[summary.id]?.done == true) { viewModel.open(summary.id) }
            }
            return@LazyColumn
        }

        item(key = "tabs") { AudiobookTabBar(tabs.order, tabs.selected, tabs.select) }

        fun LazyListScope.grid(list: List<AbsBookSummary>, prefix: String) = bookGrid(list, key = { "$prefix:${it.id}" }) { summary ->
            val bookProgress = progress[summary.id]
            GridBook(
                coverUrl = viewModel.coverUrl(summary.id),
                title = summary.title,
                author = summary.author,
                badge = when {
                    bookProgress?.isComplete(summary.durationSec) == true -> GridBadge.Finished
                    bookProgress != null && bookProgress.fractionComplete(summary.durationSec) > 0 ->
                        GridBadge.Progress((bookProgress.fractionComplete(summary.durationSec) * 100).roundToInt())
                    downloads[summary.id]?.done == true -> GridBadge.Downloaded
                    else -> null
                },
                onClick = { viewModel.open(summary.id) },
            )
        }

        when (tabs.selected) {
            AudiobookTab.LIBRARY -> {
                inProgress.firstOrNull()?.let { current ->
                    item(key = "continue") {
                        val currentProgress = progress[current.id]
                        ContinueCard(
                            coverUrl = viewModel.coverUrl(current.id),
                            title = current.title,
                            author = current.author,
                            fraction = currentProgress?.fractionComplete(current.durationSec)?.toFloat(),
                            detail = progressText(currentProgress, current.durationSec),
                            isPlaying = isPlaying && playingItemId == current.id,
                            onOpen = { viewModel.open(current.id) },
                            onPlay = { play(current.id, null) },
                        )
                    }
                }
                item(key = "library_header") {
                    LibraryHeader(
                        count = if (totalBooks > 0) pluralStringResource(R.plurals.n_audiobook, totalBooks, totalBooks) else null,
                        sort = tabs.sort,
                        sortOptions = AudiobookSort.entries,
                        onSort = tabs.setSort,
                    )
                }
                if (books.isEmpty() && !loading) {
                    item(key = "no_books") {
                        EmptyText(stringResource(if (libraries.isEmpty()) R.string.abs_no_libraries else R.string.abs_no_books))
                    }
                }
                grid(books, "book")
                if (books.size < totalBooks) {
                    item(key = "more") {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            OutlinedButton(onClick = viewModel::loadMore, enabled = !loading) {
                                Text(stringResource(R.string.abs_load_more))
                            }
                        }
                    }
                }
            }
            AudiobookTab.DOWNLOADED -> {
                if (downloadedBooks.isEmpty()) item(key = "none_downloaded") { TabMessage(stringResource(R.string.audiobook_none_downloaded)) }
                grid(downloadedBooks, "downloaded")
            }
            AudiobookTab.LISTENING -> {
                if (inProgress.isEmpty() && !loading) item(key = "none_listening") { TabMessage(stringResource(R.string.audiobook_none_listening)) }
                grid(inProgress, "listening")
            }
        }
    }
}

@Composable
private fun SignInForm(
    busy: Boolean,
    error: String?,
    onSignIn: (String, String, String) -> Unit,
    onSignInWithKey: (String, String) -> Unit,
) {
    var server by rememberSaveable { mutableStateOf("") }
    var useApiKey by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var apiKey by rememberSaveable { mutableStateOf("") }
    val canSubmit = server.isNotBlank() && !busy &&
        (if (useApiKey) apiKey.isNotBlank() else username.isNotBlank() && password.isNotEmpty())

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(stringResource(R.string.abs_connect_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.abs_connect_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = server,
            onValueChange = { server = it },
            label = { Text(stringResource(R.string.abs_server_address)) },
            placeholder = { Text("https://audiobooks.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !useApiKey,
                onClick = { useApiKey = false },
                label = { Text(stringResource(R.string.abs_use_password)) },
            )
            FilterChip(
                selected = useApiKey,
                onClick = { useApiKey = true },
                label = { Text(stringResource(R.string.abs_use_api_key)) },
            )
        }
        if (useApiKey) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text(stringResource(R.string.abs_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.abs_api_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.abs_username)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.abs_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.abs_sso_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = {
                if (useApiKey) onSignInWithKey(server, apiKey) else onSignIn(server, username, password)
            },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.abs_sign_in))
            }
        }
    }
}

private fun LazyListScope.bookDetail(
    book: AbsBook,
    progress: AbsProgress?,
    coverUrl: String?,
    isThisBookPlaying: Boolean,
    busy: Boolean,
    download: AbsDownloadState?,
    onBack: () -> Unit,
    onPlay: (Double?) -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onToggleFinished: () -> Unit,
) {
    val started = progress != null && progress.currentTimeSec > 0 && !progress.isComplete(book.durationSec)
    val finished = progress?.isComplete(book.durationSec) == true

    item(key = "abs_book_back") {
        IconButton(onClick = onBack, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.audiobooks_back))
        }
    }

    item(key = "abs_book_hero") {
        AudiobookHero(
            coverUrl = coverUrl,
            title = book.title,
            authors = book.author,
            metaText = progressText(progress, book.durationSec),
            finished = finished,
            fraction = progress?.fractionComplete(book.durationSec)?.toFloat(),
            primaryText = when {
                isThisBookPlaying -> stringResource(R.string.audiobook_pause)
                finished -> stringResource(R.string.audiobook_listen_again)
                started -> stringResource(R.string.abs_continue_from, formatClock(progress!!.currentTimeSec))
                else -> stringResource(R.string.audiobook_start)
            },
            primaryIcon = R.drawable.play,
            busy = busy,
            primaryEnabled = book.tracks.isNotEmpty(),
            onPrimary = { onPlay(null) },
        ) {
            when {
                download == null -> DetailActionButton(R.drawable.download, stringResource(R.string.abs_download)) { onDownload() }
                download.done -> DetailActionButton(R.drawable.offline, stringResource(R.string.abs_remove_download)) { onRemoveDownload() }
                download.failed -> DetailActionButton(R.drawable.download, stringResource(R.string.abs_retry)) { onDownload() }
                else -> DetailActionButton(R.drawable.download, "${(download.progress * 100).roundToInt()}%", enabled = false) {}
            }
            DetailActionButton(
                icon = if (finished) R.drawable.radio_button_unchecked else R.drawable.check_circle,
                text = stringResource(if (finished) R.string.mark_unfinished else R.string.mark_finished),
            ) { onToggleFinished() }
        }
    }

    item(key = "abs_book_meta") {
        Column(modifier = Modifier.fillMaxWidth()) {
            NarratedBy(book.narrators)
            book.description?.let { DetailOverview(it) }
            GenreChips(book.genres)
        }
    }

    val useChapters = book.chapters.isNotEmpty()
    val chapterCount = if (useChapters) book.chapters.size else book.tracks.size
    if (useChapters || book.tracks.size > 1) {
        item(key = "abs_chapters") {
            var expanded by rememberSaveable(book.id) { mutableStateOf(false) }
            val position = progress?.takeIf { !it.isComplete(book.durationSec) }?.currentTimeSec ?: -1.0
            Column(modifier = Modifier.fillMaxWidth()) {
                CollapsibleSectionTitle(stringResource(R.string.audiobook_chapters), chapterCount, expanded) { expanded = !expanded }
                if (expanded && useChapters) {
                    book.chapters.forEachIndexed { index, chapter ->
                        ChapterLine(
                            number = index + 1,
                            title = chapter.title,
                            time = formatClock(chapter.startSec),
                            isCurrent = position >= chapter.startSec && position < chapter.endSec,
                            onClick = { onPlay(chapter.startSec) },
                        )
                    }
                } else if (expanded) {
                    book.tracks.forEachIndexed { index, track ->
                        ChapterLine(
                            number = index + 1,
                            title = track.title.substringBeforeLast('.'),
                            time = formatClock(track.startOffsetSec),
                            isCurrent = false,
                            onClick = { onPlay(track.startOffsetSec) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterLine(number: Int, title: String, time: String, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )
        Text(
            title.ifBlank { stringResource(R.string.abs_chapter_n, number) },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Cover(url: String?, size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Icon(
            painterResource(R.drawable.audiobook),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
    )
}

@Composable
private fun LoadingRow() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) {
        CircularProgressIndicator()
    }
}

@Composable
private fun progressText(progress: AbsProgress?, durationSec: Double): String = when {
    progress?.isComplete(durationSec) == true -> stringResource(R.string.audiobook_finished)
    progress != null && progress.fractionComplete(durationSec) > 0 -> stringResource(
        R.string.abs_progress,
        (progress.fractionComplete(durationSec) * 100).roundToInt(),
        formatLength((durationSec - progress.currentTimeSec).coerceAtLeast(0.0)),
    )
    durationSec > 0 -> formatLength(durationSec)
    else -> ""
}

private fun formatLength(seconds: Double): String {
    val total = seconds.toLong()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "$hours h $minutes min" else "$minutes min"
}

private fun formatClock(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val secs = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%d:%02d".format(minutes, secs)
}
