/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.mediaserver

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.mediaserver.MediaServerKind
import com.metrolist.music.mediaserver.MsAlbum
import com.metrolist.music.mediaserver.MsArtist
import com.metrolist.music.mediaserver.MsPlaylist
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.viewmodels.MediaServerPage
import com.metrolist.music.viewmodels.MediaServerTab
import com.metrolist.music.viewmodels.MediaServerViewModel
import com.metrolist.music.viewmodels.PlexSignIn

/** Jellyfin (or Emby) and Plex music in one tab: pick which one when signing in. */
@Composable
fun MediaServerScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: MediaServerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.dismissMessage()
        }
    }

    if (session == null) SignIn(viewModel) else Browser(viewModel)
}

@Composable
private fun SignIn(viewModel: MediaServerViewModel) {
    val context = LocalContext.current
    val busy by viewModel.signingIn.collectAsState()
    val plex by viewModel.plex.collectAsState()
    var kind by rememberSaveable { mutableStateOf(MediaServerKind.JELLYFIN) }
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.media_server_sign_in_title), style = MaterialTheme.typography.titleLarge)
                // Pick which kind of server
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MediaServerKind.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = kind == option,
                            onClick = { kind = option },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = MediaServerKind.entries.size),
                            label = { Text(if (option == MediaServerKind.JELLYFIN) "Jellyfin" else "Plex") },
                        )
                    }
                }
                when (kind) {
                    MediaServerKind.JELLYFIN -> {
                        Text(
                            stringResource(R.string.media_server_jellyfin_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = server,
                            onValueChange = { server = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.subsonic_server)) },
                            placeholder = { Text("https://jellyfin.example.com") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.subsonic_username)) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            singleLine = true,
                            label = { Text(stringResource(R.string.subsonic_password)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.signInJellyfin(server, username, password) },
                            enabled = !busy && server.isNotBlank() && username.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.subsonic_connect))
                        }
                    }
                    MediaServerKind.PLEX -> when (val state = plex) {
                        null -> {
                            Text(
                                stringResource(R.string.media_server_plex_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = viewModel::startPlex, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.media_server_plex_start))
                            }
                        }
                        is PlexSignIn.WaitingForCode -> {
                            Text(stringResource(R.string.media_server_plex_enter_code), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                state.pin.code,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 8.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    copy(context, state.pin.code)
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://plex.tv/link"))) }
                                }) { Text(stringResource(R.string.media_server_plex_open)) }
                                TextButton(onClick = viewModel::cancelPlex) { Text(stringResource(android.R.string.cancel)) }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    stringResource(R.string.media_server_plex_waiting),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        is PlexSignIn.PickServer -> {
                            Text(stringResource(R.string.media_server_plex_pick), style = MaterialTheme.typography.bodyMedium)
                            state.servers.forEach { option ->
                                OutlinedButton(onClick = { viewModel.usePlexServer(option) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text(option.name)
                                }
                            }
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("Plex code", text))
}

@Composable
private fun Browser(viewModel: MediaServerViewModel) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val session by viewModel.session.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val search by viewModel.search.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val busy by viewModel.signingIn.collectAsState()
    val current by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState(initial = false)
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = pages.isNotEmpty()) { viewModel.back() }
    BackHandler(enabled = pages.isEmpty() && search != null) {
        query = ""
        viewModel.clearSearch()
    }

    fun LazyListScope.songs(songs: List<Song>, queueTitle: String) {
        itemsIndexed(songs, key = { _, song -> "song:${song.id}" }) { index, song ->
            SongListItem(
                song = song,
                isActive = current?.id == song.id,
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(onClick = { menuState.show { SongMenu(originalSong = song, onDismiss = menuState::dismiss) } }) {
                        Icon(painterResource(R.drawable.more_vert), contentDescription = null)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (current?.id == song.id) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(ListQueue(title = queueTitle, items = songs.map { it.toMediaItem() }, startIndex = index))
                        }
                    },
            )
        }
    }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        val page = pages.lastOrNull()
        if (page != null) {
            item(key = "page_header") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                    IconButton(onClick = viewModel::back) { Icon(painterResource(R.drawable.arrow_back), contentDescription = null) }
                    Text(page.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            when (page) {
                is MediaServerPage.Album -> page.songs?.let {
                    playAllRow(it, page.album.title, playerConnection)
                    songs(it, page.album.title)
                } ?: loading()
                is MediaServerPage.Playlist -> page.songs?.let {
                    playAllRow(it, page.playlist.name, playerConnection)
                    songs(it, page.playlist.name)
                } ?: loading()
                is MediaServerPage.Artist -> page.albums?.let { list ->
                    items(list, key = { "album:${it.id}" }) { album -> AlbumRow(album) { viewModel.openAlbum(album) } }
                } ?: loading()
            }
            return@LazyColumn
        }

        item(key = "header") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.dns), contentDescription = null)
                    Text(
                        listOfNotNull(
                            if (session?.kind == MediaServerKind.PLEX) "Plex" else "Jellyfin",
                            session?.serverName?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    IconButton(onClick = viewModel::refresh) { Icon(painterResource(R.drawable.sync), contentDescription = stringResource(R.string.media_server_refresh)) }
                    TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.subsonic_sign_out)) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isBlank()) viewModel.clearSearch()
                    },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.media_server_search)) },
                    leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MediaServerTab.entries
                        .filter { it != MediaServerTab.FAVOURITES || viewModel.hasFavourites }
                        .forEach { option ->
                            FilterChip(
                                selected = tab == option && search == null,
                                onClick = {
                                    query = ""
                                    viewModel.clearSearch()
                                    viewModel.selectTab(option)
                                },
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                MediaServerTab.ALBUMS -> R.string.albums
                                                MediaServerTab.ARTISTS -> R.string.artists
                                                MediaServerTab.PLAYLISTS -> R.string.playlists
                                                MediaServerTab.FAVOURITES -> R.string.media_server_favourites
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                }
            }
        }

        val results = search
        if (results != null) {
            if (results.artists.isNotEmpty()) {
                items(results.artists, key = { "sa:${it.id}" }) { artist -> ArtistRow(artist) { viewModel.openArtist(artist) } }
            }
            if (results.albums.isNotEmpty()) {
                items(results.albums, key = { "sb:${it.id}" }) { album -> AlbumRow(album) { viewModel.openAlbum(album) } }
            }
            songs(results.songs, results.query)
            if (results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty()) empty()
            return@LazyColumn
        }

        when (tab) {
            MediaServerTab.ALBUMS -> albums?.let { list ->
                if (list.isEmpty()) empty()
                items(list, key = { "album:${it.id}" }) { album -> AlbumRow(album) { viewModel.openAlbum(album) } }
            } ?: loading()
            MediaServerTab.ARTISTS -> artists?.let { list ->
                if (list.isEmpty()) empty()
                items(list, key = { "artist:${it.id}" }) { artist -> ArtistRow(artist) { viewModel.openArtist(artist) } }
            } ?: loading()
            MediaServerTab.PLAYLISTS -> playlists?.let { list ->
                if (list.isEmpty()) empty()
                items(list, key = { "playlist:${it.id}" }) { playlist -> PlaylistRow(playlist) { viewModel.openPlaylist(playlist) } }
            } ?: loading()
            MediaServerTab.FAVOURITES -> favourites?.let { list ->
                if (list.isEmpty()) empty()
                val title = "Favourites"
                playAllRow(list, title, playerConnection)
                songs(list, title)
            } ?: loading()
        }
    }
}

private fun LazyListScope.loading() {
    item(key = "loading") {
        Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
    }
}

private fun LazyListScope.empty() {
    item(key = "empty") {
        Text(
            stringResource(R.string.subsonic_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}

/** Play / Shuffle / Download all, for an album, playlist or favourites. */
private fun LazyListScope.playAllRow(songs: List<Song>, title: String, playerConnection: com.metrolist.music.playback.PlayerConnection) {
    if (songs.isEmpty()) return
    item(key = "play_all") {
        val context = LocalContext.current
        val downloadUtil = LocalDownloadUtil.current
        val downloads by downloadUtil.downloads.collectAsState()
        val states = songs.map { downloads[it.id]?.state }
        val allDownloaded = states.all { it == Download.STATE_COMPLETED }
        val downloading = states.any { it == Download.STATE_QUEUED || it == Download.STATE_DOWNLOADING }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { playerConnection.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() })) }) {
                Icon(painterResource(R.drawable.play), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.subsonic_play_all))
            }
            TextButton(onClick = { playerConnection.playQueue(ListQueue(title = title, items = songs.shuffled().map { it.toMediaItem() })) }) {
                Icon(painterResource(R.drawable.shuffle), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.subsonic_shuffle))
            }
            TextButton(
                enabled = !allDownloaded,
                onClick = {
                    val missing = songs.filter { downloads[it.id]?.state != Download.STATE_COMPLETED }
                    missing.forEach { downloadUtil.download(it) }
                    Toast.makeText(context, context.getString(R.string.subsonic_downloading_count, missing.size), Toast.LENGTH_SHORT).show()
                },
            ) {
                when {
                    allDownloaded -> Icon(painterResource(R.drawable.offline), contentDescription = null)
                    downloading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else -> Icon(painterResource(R.drawable.download), contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        when {
                            allDownloaded -> R.string.subsonic_downloaded
                            downloading -> R.string.downloading
                            else -> R.string.subsonic_download_all
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun MediaRow(title: String, subtitle: String?, imageUrl: String?, round: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlbumRow(album: MsAlbum, onClick: () -> Unit) =
    MediaRow(album.title, listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "), album.coverUrl, onClick = onClick)

@Composable
private fun ArtistRow(artist: MsArtist, onClick: () -> Unit) =
    MediaRow(artist.name, null, artist.coverUrl, round = true, onClick = onClick)

@Composable
private fun PlaylistRow(playlist: MsPlaylist, onClick: () -> Unit) =
    MediaRow(playlist.name, stringResource(R.string.media_server_songs, playlist.songCount), playlist.coverUrl, onClick = onClick)
