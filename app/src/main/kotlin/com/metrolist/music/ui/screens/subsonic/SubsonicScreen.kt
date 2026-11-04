/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.subsonic

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.media3.exoplayer.offline.Download
import com.metrolist.music.LocalDownloadUtil
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.viewmodels.SubsonicPage
import com.metrolist.music.viewmodels.SubsonicTab
import com.metrolist.music.viewmodels.SubsonicViewModel

/** Browse and play a Navidrome or other Subsonic-compatible server. */
@Composable
fun SubsonicScreen(
    @Suppress("UNUSED_PARAMETER") navController: NavController,
    viewModel: SubsonicViewModel = hiltViewModel(),
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

    if (session == null) {
        SignInForm(viewModel)
    } else {
        Browser(viewModel)
    }
}

@Composable
private fun SignInForm(viewModel: SubsonicViewModel) {
    val signingIn by viewModel.signingIn.collectAsState()
    var server by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.subsonic_sign_in_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.subsonic_sign_in_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = server,
                    onValueChange = { server = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.subsonic_server)) },
                    placeholder = { Text("https://music.example.com") },
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
                    onClick = { viewModel.signIn(server, username, password) },
                    enabled = !signingIn && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (signingIn) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.subsonic_connect))
                    }
                }
            }
        }
    }
}

@Composable
private fun Browser(viewModel: SubsonicViewModel) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val session by viewModel.session.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val albumSort by viewModel.albumSort.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val starred by viewModel.starred.collectAsState()
    val rootFolders by viewModel.rootFolders.collectAsState()
    val search by viewModel.search.collectAsState()
    val pages by viewModel.pages.collectAsState()
    val current by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState(initial = false)
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler(enabled = pages.isNotEmpty()) { viewModel.back() }

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
                            playerConnection.playQueue(
                                ListQueue(title = queueTitle, items = songs.map { it.toMediaItem() }, startIndex = index),
                            )
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
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 16.dp)) {
                    IconButton(onClick = viewModel::back) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                    }
                    Text(page.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            when (page) {
                is SubsonicPage.Album -> page.songs?.let { list ->
                    playAllRow(list, page.title, playerConnection)
                    songs(list, page.title)
                } ?: loadingItem()
                is SubsonicPage.Playlist -> page.songs?.let { list ->
                    playAllRow(list, page.title, playerConnection)
                    songs(list, page.title)
                } ?: loadingItem()
                is SubsonicPage.Folder -> if (page.folders == null || page.songs == null) {
                    loadingItem()
                } else {
                    folderRows(page.folders, viewModel)
                    if (page.folders.isEmpty() && page.songs.isEmpty()) {
                        item(key = "empty_folder") { Hint(stringResource(R.string.subsonic_empty)) }
                    }
                    playAllRow(page.songs, page.title, playerConnection)
                    songs(page.songs, page.title)
                }
                is SubsonicPage.Artist -> page.albums?.let { list ->
                    items(list, key = { "album:${it.id}" }) { album ->
                        MediaRow(
                            title = album.name,
                            subtitle = listOfNotNull(album.year?.toString(), "${album.songCount} songs").joinToString(" · "),
                            artwork = viewModel.coverUrl(album.coverArt),
                            onClick = { viewModel.openAlbum(album) },
                        )
                    }
                } ?: loadingItem()
            }
            return@LazyColumn
        }

        item(key = "account") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
            ) {
                Text(
                    stringResource(R.string.subsonic_signed_in_as, session?.username.orEmpty(), session?.serverUrl.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::signOut) { Text(stringResource(R.string.subsonic_sign_out)) }
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.search(it)
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.subsonic_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        val results = search
        if (query.isNotBlank() && results != null) {
            if (results.artists.isEmpty() && results.albums.isEmpty() && results.songs.isEmpty()) {
                item(key = "no_results") { Hint(stringResource(R.string.subsonic_no_results)) }
            }
            items(results.artists, key = { "s_artist:${it.id}" }) { artist ->
                MediaRow(
                    title = artist.name,
                    subtitle = stringResource(R.string.subsonic_artist_albums, artist.albumCount),
                    artwork = viewModel.coverUrl(artist.coverArt),
                    round = true,
                    onClick = { viewModel.openArtist(artist) },
                )
            }
            items(results.albums, key = { "s_album:${it.id}" }) { album ->
                MediaRow(
                    title = album.name,
                    subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                    artwork = viewModel.coverUrl(album.coverArt),
                    onClick = { viewModel.openAlbum(album) },
                )
            }
            songs(results.songs, query)
            return@LazyColumn
        }

        item(key = "tabs") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(SubsonicTab.entries) { entry ->
                    FilterChip(
                        selected = tab == entry,
                        onClick = { viewModel.selectTab(entry) },
                        label = {
                            Text(
                                stringResource(
                                    when (entry) {
                                        SubsonicTab.ALBUMS -> R.string.albums
                                        SubsonicTab.ARTISTS -> R.string.artists
                                        SubsonicTab.PLAYLISTS -> R.string.playlists
                                        SubsonicTab.FOLDERS -> R.string.subsonic_folders
                                        SubsonicTab.STARRED -> R.string.subsonic_starred
                                    },
                                ),
                            )
                        },
                    )
                }
                item {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(painterResource(R.drawable.refresh), contentDescription = stringResource(R.string.news_refresh))
                    }
                }
            }
        }

        when (tab) {
            SubsonicTab.ALBUMS -> {
                item(key = "sorts") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        items(SubsonicViewModel.ALBUM_SORTS) { (type, label) ->
                            FilterChip(selected = albumSort == type, onClick = { viewModel.sortAlbums(type) }, label = { Text(label) })
                        }
                    }
                }
                albums?.let { list ->
                    items(list, key = { "album:${it.id}" }) { album ->
                        MediaRow(
                            title = album.name,
                            subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · "),
                            artwork = viewModel.coverUrl(album.coverArt),
                            onClick = { viewModel.openAlbum(album) },
                        )
                    }
                } ?: loadingItem()
            }
            SubsonicTab.ARTISTS -> artists?.let { list ->
                items(list, key = { "artist:${it.id}" }) { artist ->
                    MediaRow(
                        title = artist.name,
                        subtitle = stringResource(R.string.subsonic_artist_albums, artist.albumCount),
                        artwork = viewModel.coverUrl(artist.coverArt),
                        round = true,
                        onClick = { viewModel.openArtist(artist) },
                    )
                }
            } ?: loadingItem()
            SubsonicTab.PLAYLISTS -> playlists?.let { list ->
                if (list.isEmpty()) item(key = "no_playlists") { Hint(stringResource(R.string.subsonic_empty)) }
                items(list, key = { "playlist:${it.id}" }) { playlist ->
                    MediaRow(
                        title = playlist.name,
                        subtitle = stringResource(R.string.subsonic_song_count, playlist.songCount),
                        artwork = viewModel.coverUrl(playlist.coverArt),
                        onClick = { viewModel.openPlaylist(playlist) },
                    )
                }
            } ?: loadingItem()
            SubsonicTab.FOLDERS -> rootFolders?.let { list ->
                if (list.isEmpty()) item(key = "no_folders") { Hint(stringResource(R.string.subsonic_empty)) }
                folderRows(list.filter { it.isFolder }, viewModel)
                val looseSongs = list.mapNotNull { it.song }
                if (looseSongs.isNotEmpty()) {
                    item(key = "root_songs") {
                        // Songs sitting at the top level open as their own folder-style list
                        val title = stringResource(R.string.subsonic_loose_songs)
                        MediaRow(
                            title = title,
                            subtitle = stringResource(R.string.subsonic_song_count, looseSongs.size),
                            artwork = null,
                            placeholder = R.drawable.folder,
                            onClick = { viewModel.openLooseSongs(title, looseSongs) },
                        )
                    }
                }
            } ?: loadingItem()
            SubsonicTab.STARRED -> starred?.let { list ->
                if (list.isEmpty()) item(key = "no_starred") { Hint(stringResource(R.string.subsonic_empty)) }
                playAllRow(list, "Starred", playerConnection)
                songs(list, "Starred")
            } ?: loadingItem()
        }
    }
}

private fun LazyListScope.folderRows(folders: List<com.metrolist.music.subsonic.SubsonicFolderEntry>, viewModel: SubsonicViewModel) {
    items(folders, key = { "folder:${it.id}" }) { folder ->
        MediaRow(
            title = folder.name,
            subtitle = "",
            artwork = viewModel.coverUrl(folder.coverArt),
            placeholder = R.drawable.folder,
            onClick = { viewModel.openFolder(folder) },
        )
    }
}

private fun LazyListScope.loadingItem() {
    item(key = "loading") {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

private fun LazyListScope.playAllRow(
    songs: List<Song>,
    title: String,
    playerConnection: com.metrolist.music.playback.PlayerConnection,
) {
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
            Button(onClick = {
                playerConnection.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }))
            }) {
                Icon(painterResource(R.drawable.play), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.subsonic_play_all))
            }
            TextButton(onClick = {
                playerConnection.playQueue(ListQueue(title = title, items = songs.shuffled().map { it.toMediaItem() }))
            }) {
                Icon(painterResource(R.drawable.shuffle), contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.subsonic_shuffle))
            }
            // Downloads the whole album or playlist; files go to "Navidrome - <name>" folders
            TextButton(
                enabled = !allDownloaded,
                onClick = {
                    val missing = songs.filter { downloads[it.id]?.state != Download.STATE_COMPLETED }
                    missing.forEach { downloadUtil.download(it) }
                    Toast.makeText(
                        context,
                        context.getString(R.string.subsonic_downloading_count, missing.size),
                        Toast.LENGTH_SHORT,
                    ).show()
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
private fun MediaRow(
    title: String,
    subtitle: String,
    artwork: String?,
    round: Boolean = false,
    placeholder: Int = R.drawable.music_note,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(if (round) RoundedCornerShape(28.dp) else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Icon(painterResource(placeholder), contentDescription = null)
            if (artwork != null) {
                AsyncImage(model = artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}
