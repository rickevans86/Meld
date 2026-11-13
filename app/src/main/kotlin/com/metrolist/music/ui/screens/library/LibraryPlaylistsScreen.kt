/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.CONTENT_TYPE_HEADER
import com.metrolist.music.constants.CONTENT_TYPE_PLAYLIST
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistSortDescendingKey
import com.metrolist.music.constants.PlaylistSortType
import com.metrolist.music.constants.PlaylistSortTypeKey
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.constants.ShowCachedPlaylistKey
import com.metrolist.music.constants.ShowDownloadedPlaylistKey
import com.metrolist.music.constants.ShowLikedPlaylistKey
import com.metrolist.music.constants.HideYtmLikedSongsKey
import com.metrolist.music.constants.SpotifySyncLikesKey
import com.metrolist.music.constants.ShowTopPlaylistKey
import com.metrolist.music.constants.ShowUploadedPlaylistKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.ui.component.CreatePlaylistDialog
import com.metrolist.music.ui.component.HideOnScrollFAB
import com.metrolist.music.ui.component.LibrarySearchEmptyPlaceholder
import com.metrolist.music.ui.component.LibrarySearchHeader
import com.metrolist.music.ui.component.LibraryPlaylistGridItem
import com.metrolist.music.ui.component.LibraryPlaylistListItem
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.component.PlaylistListItem
import com.metrolist.music.ui.component.SortHeader
import com.metrolist.music.ui.component.SpotifyFolderGridItem
import com.metrolist.music.ui.component.SpotifyFolderListItem
import com.metrolist.music.extensions.matchesNormalizedQuery
import com.metrolist.music.extensions.normalizeForSearch
import com.metrolist.music.utils.rememberEnumPreference
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LibraryPlaylistsViewModel
import com.metrolist.music.ui.menu.SpotifyPlaylistMenu
import com.metrolist.music.viewmodels.SpotifyViewModel
import com.metrolist.spotify.SpotifyMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.only

private data class VisiblePlaylistItem(
    val key: String,
    val playlist: Playlist,
    val autoPlaylist: Boolean,
    val route: String? = null,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewType: LibraryViewType,
    onViewTypeChange: (LibraryViewType) -> Unit,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
    spotifyViewModel: SpotifyViewModel = hiltViewModel(),
    initialTextFieldValue: String? = null,
    allowSyncing: Boolean = true,
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val coroutineScope = rememberCoroutineScope()

    val (sortType, onSortTypeChange) = rememberEnumPreference(
        PlaylistSortTypeKey,
        PlaylistSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(
        PlaylistSortDescendingKey,
        true
    )
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val playlists by viewModel.allPlaylists.collectAsState()

    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val normalizedQuery = remember(searchQuery) { searchQuery.normalizeForSearch() }
    // Playlists pinned to the top of the Library show in the Pins row instead of the list below
    val pinnedKeys = com.metrolist.music.constants.rememberLibraryPins()
    val filteredPlaylists = remember(playlists, normalizedQuery, pinnedKeys) {
        if (normalizedQuery.isBlank()) {
            playlists.filterNot { "playlist:${it.id}" in pinnedKeys }
        } else {
            playlists.filter { playlist ->
                matchesNormalizedQuery(normalizedQuery, playlist.playlist.name)
            }
        }
    }

    val topSize by viewModel.topValue.collectAsState(initial = 50)

    // Spotify integration - when active, Spotify is the PRIMARY source
    val isSpotifyActive by spotifyViewModel.isSpotifyActive.collectAsState()
    // Faithful hierarchical view: folders + only the playlists at the library
    // root. Playlists nested in folders are reachable via SpotifyFolderScreen.
    val spotifyFolders by spotifyViewModel.spotifyRootFolders.collectAsState()
    val spotifyPlaylists by spotifyViewModel.spotifyRootPlaylists.collectAsState()
    val spotifyLikedSongsTotal by spotifyViewModel.likedSongsTotal.collectAsState()
    val isUsingFallback by spotifyViewModel.isUsingFallback.collectAsState()
    val fallbackReason by spotifyViewModel.fallbackReason.collectAsState()
    val needsSpotifyReLogin by spotifyViewModel.needsReLogin.collectAsState()

    LaunchedEffect(isSpotifyActive) {
        if (isSpotifyActive) {
            spotifyViewModel.loadAll()
        }
    }

    // ── Pins row (same pins as the main Library view) ──
    val allSpotifyPlaylists by spotifyViewModel.spotifyPlaylists.collectAsState()
    val pinContext = androidx.compose.ui.platform.LocalContext.current
    val pinUnpinned = stringResource(R.string.library_unpinned)
    val pinPlaylistLabel = stringResource(R.string.library_pin_playlist)
    val spotifyLikedName = stringResource(R.string.spotify_liked_songs)
    val likedPinName = stringResource(R.string.liked)
    // Long-pressing a pin opens the same ⋮ menu as the item has in the list
    val pinMenuScope = rememberCoroutineScope()
    fun pinMenu(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        menuState.show(content)
    }
    val pinCards = pinnedKeys.mapNotNull { key ->
        val id = key.substringAfter(':')
        when (key.substringBefore(':')) {
            "playlist" -> playlists.firstOrNull { it.id == id }?.let { p ->
                com.metrolist.music.ui.component.LibraryPinCard(key, p.playlist.name, pinPlaylistLabel, p.thumbnails.firstOrNull(), onLongClick = {
                    pinMenu { com.metrolist.music.ui.menu.PlaylistMenu(playlist = p, coroutineScope = pinMenuScope, onDismiss = menuState::dismiss) }
                }) {
                    if (!p.playlist.isEditable && p.songCount == 0 && p.playlist.browseId != null) {
                        navController.navigate("online_playlist/${p.playlist.browseId}")
                    } else {
                        navController.navigate("local_playlist/${p.id}")
                    }
                }
            }
            // YouTube Liked songs (pinned from its ⋮ menu)
            "auto" -> if (id == "liked") {
                com.metrolist.music.ui.component.LibraryPinCard(key, likedPinName, "YouTube", null, placeholder = R.drawable.favorite) {
                    navController.navigate("auto_playlist/liked")
                }
            } else {
                null
            }
            "spotify" -> if (id == "liked_songs") {
                com.metrolist.music.ui.component.LibraryPinCard(key, spotifyLikedName, "Spotify", null, placeholder = R.drawable.favorite, onLongClick = {
                    pinMenu {
                        com.metrolist.music.ui.menu.SpotifyPlaylistMenu(
                            spotifyId = "spotify:liked_songs",
                            title = spotifyLikedName,
                            onNavigate = { navController.navigate("spotify_liked_songs") },
                            onDismiss = menuState::dismiss,
                        )
                    }
                }) {
                    navController.navigate("spotify_liked_songs")
                }
            } else {
                (allSpotifyPlaylists + spotifyPlaylists).firstOrNull { it.id == id }?.let { sp ->
                    com.metrolist.music.ui.component.LibraryPinCard(key, sp.name, "Spotify", SpotifyMapper.getPlaylistThumbnail(sp), onLongClick = {
                        pinMenu {
                            com.metrolist.music.ui.menu.SpotifyPlaylistMenu(
                                playlist = sp,
                                onNavigate = { navController.navigate("spotify_playlist/${sp.id}") },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }) {
                        navController.navigate("spotify_playlist/${sp.id}")
                    }
                }
            }
            else -> null
        }
    }
    val unpin: (com.metrolist.music.ui.component.LibraryPinCard) -> Unit = { pin ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val app = pinContext.applicationContext
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { com.metrolist.music.constants.setLibraryPinned(app, pin.key, false) }
        android.widget.Toast.makeText(pinContext, pinUnpinned, android.widget.Toast.LENGTH_SHORT).show()
    }
    val visibleSpotifyPlaylists = spotifyPlaylists.filterNot { "spotify:${it.id}" in pinnedKeys }

    val likedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.liked)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val downloadPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.offline)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val topPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.my_top) + " $topSize"
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )


    val uploadedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.uploaded_playlist)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val cachedPlaylist =
        Playlist(
            playlist = PlaylistEntity(
                id = UUID.randomUUID().toString(),
                name = stringResource(R.string.cached_playlist)
            ),
            songCount = 0,
            songThumbnails = emptyList(),
        )

    val (showLiked) = rememberPreference(ShowLikedPlaylistKey, true)
    val (spotifySyncLikes) = rememberPreference(SpotifySyncLikesKey, false)
    val (hideYtmLikedSongs) = rememberPreference(HideYtmLikedSongsKey, true)
    val hideYtmLiked = spotifySyncLikes && hideYtmLikedSongs
    val (showDownloaded) = rememberPreference(ShowDownloadedPlaylistKey, true)
    val (showTop) = rememberPreference(ShowTopPlaylistKey, true)
    val (showUploaded) = rememberPreference(ShowUploadedPlaylistKey, true)
    val (showCached) = rememberPreference(ShowCachedPlaylistKey, true)
    // A pinned Liked songs shows in the Pins row instead
    val likedPinned = "auto:liked" in com.metrolist.music.constants.rememberLibraryPins()
    val showLikedPlaylist = showLiked && !hideYtmLiked && !likedPinned && matchesNormalizedQuery(normalizedQuery, likedPlaylist.playlist.name)
    val showDownloadedPlaylist =
        showDownloaded && matchesNormalizedQuery(normalizedQuery, downloadPlaylist.playlist.name)
    val showCachedPlaylists = showCached && matchesNormalizedQuery(normalizedQuery, cachedPlaylist.playlist.name)
    val showTopPlaylists = showTop && matchesNormalizedQuery(normalizedQuery, topPlaylist.playlist.name)
    val showUploadedPlaylists =
        showUploaded && matchesNormalizedQuery(normalizedQuery, uploadedPlaylist.playlist.name)

    val visibleResults = remember(
        filteredPlaylists,
        showLikedPlaylist,
        showDownloadedPlaylist,
        showCachedPlaylists,
        showTopPlaylists,
        showUploadedPlaylists,
        topSize,
    ) {
        buildList {
            if (showLikedPlaylist) {
                add(
                    VisiblePlaylistItem(
                        key = "likedPlaylist",
                        playlist = likedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/liked",
                    ),
                )
            }
            if (showDownloadedPlaylist) {
                add(
                    VisiblePlaylistItem(
                        key = "downloadedPlaylist",
                        playlist = downloadPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/downloaded",
                    ),
                )
            }
            if (showCachedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "cachedPlaylist",
                        playlist = cachedPlaylist,
                        autoPlaylist = true,
                        route = "cache_playlist/cached",
                    ),
                )
            }
            if (showTopPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "TopPlaylist",
                        playlist = topPlaylist,
                        autoPlaylist = true,
                        route = "top_playlist/$topSize",
                    ),
                )
            }
            if (showUploadedPlaylists) {
                add(
                    VisiblePlaylistItem(
                        key = "uploadedPlaylist",
                        playlist = uploadedPlaylist,
                        autoPlaylist = true,
                        route = "auto_playlist/uploaded",
                    ),
                )
            }

            filteredPlaylists
                .distinctBy { it.id }
                .forEach { playlist ->
                    add(
                        VisiblePlaylistItem(
                            key = playlist.id,
                            playlist = playlist,
                            autoPlaylist = false,
                        ),
                    )
                }
        }
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry?.savedStateHandle?.getStateFlow("scrollToTop", false)?.collectAsState()

    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            when (viewType) {
                LibraryViewType.LIST -> lazyListState.animateScrollToItem(0)
                LibraryViewType.GRID -> lazyGridState.animateScrollToItem(0)
            }
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            initialTextFieldValue = initialTextFieldValue,
            allowSyncing = allowSyncing,
            onPlaylistCreated = { playlistId ->
                showCreatePlaylistDialog = false
                navController.navigate("local_playlist/$playlistId")
            }
        )
    }

    val headerContent = @Composable {
        LibrarySearchHeader(
            isSearchActive = isSearchActive,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onBack = {
                isSearchActive = false
                viewModel.updateSearchQuery("")
            },
            keyboardController = keyboardController,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> R.string.sort_by_create_date
                        PlaylistSortType.NAME -> R.string.sort_by_name
                        PlaylistSortType.SONG_COUNT -> R.string.sort_by_song_count
                        PlaylistSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                    }
                },
            )

            Spacer(Modifier.weight(1f))

            Text(
                text = pluralStringResource(
                    R.plurals.n_playlist,
                    visibleResults.size,
                    visibleResults.size,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )

            IconButton(
                onClick = { isSearchActive = true },
                modifier = Modifier.padding(start = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = stringResource(R.string.search),
                )
            }

            IconButton(
                onClick = {
                    onViewTypeChange(viewType.toggle())
                },
                modifier = Modifier.padding(end = 8.dp).size(40.dp),
            ) {
                Icon(
                    painter =
                    painterResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.drawable.list
                            LibraryViewType.GRID -> R.drawable.grid_view
                        },
                    ),
                    contentDescription = stringResource(
                        when (viewType) {
                            LibraryViewType.LIST -> R.string.switch_to_grid_view
                            LibraryViewType.GRID -> R.string.switch_to_list_view
                        },
                    ),
                )
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (pinCards.isNotEmpty() && normalizedQuery.isBlank()) {
                        item(key = "library_pins", contentType = CONTENT_TYPE_HEADER) {
                            com.metrolist.music.ui.component.LibraryPinsRow(pins = pinCards, onUnpin = unpin)
                        }
                    }

                    if (visibleResults.isEmpty()) {
                        item(key = "empty_placeholder") {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                LibrarySearchEmptyPlaceholder(
                                    modifier = Modifier.animateItem(),
                                    icon = R.drawable.playlist_play,
                                    text = stringResource(R.string.library_playlist_empty),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleResults,
                        key = { it.key },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        if (item.autoPlaylist) {
                            PlaylistListItem(
                                playlist = item.playlist,
                                autoPlaylist = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            item.route?.let(navController::navigate)
                                        }
                                        .animateItem(),
                            )
                        } else {
                            LibraryPlaylistListItem(
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = item.playlist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Spotify session expired banner
                    if (needsSpotifyReLogin) {
                        item(key = "spotify_relogin_banner") {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.spotify),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                    Text(
                                        text = stringResource(R.string.spotify_session_expired),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            navController.navigate("settings/spotify/login")
                                        },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.spotify_relogin),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Spotify playlists failed to load banner
                    if (isSpotifyActive && !needsSpotifyReLogin && isUsingFallback && spotifyPlaylists.isEmpty()) {
                        item(key = "spotify_load_error_banner") {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.spotify),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                    Text(
                                        text = stringResource(R.string.spotify_playlists_load_error),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = { spotifyViewModel.loadPlaylists() },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.retry),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Spotify Liked Songs as a special playlist entry
                    if (isSpotifyActive && spotifyLikedSongsTotal > 0 && "spotify:liked_songs" !in pinnedKeys) {
                        item(key = "spotify_liked_songs") {
                            val likedSongsName = stringResource(R.string.spotify_liked_songs)
                            PlaylistListItem(
                                playlist = Playlist(
                                    playlist = PlaylistEntity(
                                        id = "spotify_liked_songs",
                                        name = likedSongsName,
                                        remoteSongCount = spotifyLikedSongsTotal,
                                    ),
                                    songCount = spotifyLikedSongsTotal,
                                    songThumbnails = emptyList(),
                                ),
                                autoPlaylist = true,
                                trailingContent = {
                                    IconButton(onClick = {
                                        menuState.show {
                                            SpotifyPlaylistMenu(
                                                spotifyId = "spotify:liked_songs",
                                                title = likedSongsName,
                                                onNavigate = { navController.navigate("spotify_liked_songs") },
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    }) {
                                        Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("spotify_liked_songs")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SpotifyPlaylistMenu(
                                                    spotifyId = "spotify:liked_songs",
                                                    title = likedSongsName,
                                                    onNavigate = { navController.navigate("spotify_liked_songs") },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    )
                                    .animateItem(),
                            )
                        }
                    }

                    // Spotify folders rendered above playlists, mirroring the user's
                    // organization. Tapping a folder opens its contents in a dedicated
                    // screen instead of inlining them here, so the root view stays
                    // close to what the user sees on Spotify itself (issue #78).
                    if (isSpotifyActive && spotifyFolders.isNotEmpty()) {
                        items(
                            items = spotifyFolders,
                            key = { "spotify_folder_${it.uri}" },
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) { folder ->
                            val encodedUri = java.net.URLEncoder.encode(folder.uri, Charsets.UTF_8.name())
                            val encodedName = java.net.URLEncoder.encode(folder.name, Charsets.UTF_8.name())
                            SpotifyFolderListItem(
                                folder = folder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("spotify_folder/$encodedUri?name=$encodedName")
                                    }
                                    .animateItem(),
                            )
                        }
                    }

                    // Spotify playlists rendered as regular playlist items
                    if (isSpotifyActive && spotifyPlaylists.isNotEmpty()) {
                        items(
                            items = visibleSpotifyPlaylists,
                            key = { "spotify_${it.id}" },
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) { spotifyPlaylist ->
                            val thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(spotifyPlaylist)
                            PlaylistListItem(
                                playlist = Playlist(
                                    playlist = PlaylistEntity(
                                        id = "spotify_${spotifyPlaylist.id}",
                                        name = spotifyPlaylist.name,
                                        thumbnailUrl = thumbnailUrl,
                                    ),
                                    songCount = 0,
                                    songThumbnails = listOfNotNull(thumbnailUrl),
                                ),
                                autoPlaylist = true,
                                trailingContent = {
                                    IconButton(onClick = {
                                        menuState.show {
                                            SpotifyPlaylistMenu(
                                                playlist = spotifyPlaylist,
                                                onNavigate = { navController.navigate("spotify_playlist/${spotifyPlaylist.id}") },
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    }) {
                                        Icon(painter = painterResource(R.drawable.more_vert), contentDescription = null)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("spotify_playlist/${spotifyPlaylist.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SpotifyPlaylistMenu(
                                                    playlist = spotifyPlaylist,
                                                    onNavigate = { navController.navigate("spotify_playlist/${spotifyPlaylist.id}") },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyListState,
                    icon = R.drawable.add,
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns =
                    GridCells.Adaptive(
                        minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp,
                    ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                ) {
                    item(
                        key = "filter",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        filterContent()
                    }

                    item(
                        key = "header",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = CONTENT_TYPE_HEADER,
                    ) {
                        headerContent()
                    }

                    if (pinCards.isNotEmpty() && normalizedQuery.isBlank()) {
                        item(key = "library_pins", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) {
                            com.metrolist.music.ui.component.LibraryPinsRow(pins = pinCards, onUnpin = unpin)
                        }
                    }

                    if (visibleResults.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            if (searchQuery.isNotBlank()) {
                                LibrarySearchEmptyPlaceholder(modifier = Modifier.animateItem())
                            } else {
                                LibrarySearchEmptyPlaceholder(
                                    modifier = Modifier.animateItem(),
                                    icon = R.drawable.playlist_play,
                                    text = stringResource(R.string.library_playlist_empty),
                                )
                            }
                        }
                    }

                    items(
                        items = visibleResults,
                        key = { it.key },
                        contentType = { CONTENT_TYPE_PLAYLIST },
                    ) { item ->
                        if (item.autoPlaylist) {
                            PlaylistGridItem(
                                playlist = item.playlist,
                                fillMaxWidth = true,
                                autoPlaylist = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                item.route?.let(navController::navigate)
                                            },
                                        )
                                        .animateItem(),
                            )
                        } else {
                            LibraryPlaylistGridItem(
                                menuState = menuState,
                                coroutineScope = coroutineScope,
                                playlist = item.playlist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    // Spotify playlists failed to load banner (grid)
                    if (isSpotifyActive && !needsSpotifyReLogin && isUsingFallback && spotifyPlaylists.isEmpty()) {
                        item(
                            key = "spotify_load_error_banner",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            androidx.compose.material3.Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = androidx.compose.material3.CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.spotify),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                                    Text(
                                        text = stringResource(R.string.spotify_playlists_load_error),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    androidx.compose.material3.TextButton(
                                        onClick = { spotifyViewModel.loadPlaylists() },
                                    ) {
                                        Text(
                                            text = stringResource(R.string.retry),
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Spotify Liked Songs as a special playlist entry (grid)
                    if (isSpotifyActive && spotifyLikedSongsTotal > 0 && "spotify:liked_songs" !in pinnedKeys) {
                        item(key = "spotify_liked_songs") {
                            val likedSongsName = stringResource(R.string.spotify_liked_songs)
                            PlaylistGridItem(
                                playlist = Playlist(
                                    playlist = PlaylistEntity(
                                        id = "spotify_liked_songs",
                                        name = likedSongsName,
                                        remoteSongCount = spotifyLikedSongsTotal,
                                    ),
                                    songCount = spotifyLikedSongsTotal,
                                    songThumbnails = emptyList(),
                                ),
                                fillMaxWidth = true,
                                autoPlaylist = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("spotify_liked_songs")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SpotifyPlaylistMenu(
                                                    spotifyId = "spotify:liked_songs",
                                                    title = likedSongsName,
                                                    onNavigate = { navController.navigate("spotify_liked_songs") },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    )
                                    .animateItem(),
                            )
                        }
                    }

                    // Spotify folders rendered above playlists in grid view
                    if (isSpotifyActive && spotifyFolders.isNotEmpty()) {
                        items(
                            items = spotifyFolders,
                            key = { "spotify_folder_${it.uri}" },
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) { folder ->
                            val encodedUri = java.net.URLEncoder.encode(folder.uri, Charsets.UTF_8.name())
                            val encodedName = java.net.URLEncoder.encode(folder.name, Charsets.UTF_8.name())
                            SpotifyFolderGridItem(
                                folder = folder,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        navController.navigate("spotify_folder/$encodedUri?name=$encodedName")
                                    }
                                    .animateItem(),
                            )
                        }
                    }

                    // Spotify playlists in grid view
                    if (isSpotifyActive && spotifyPlaylists.isNotEmpty()) {
                        items(
                            items = visibleSpotifyPlaylists,
                            key = { "spotify_${it.id}" },
                            contentType = { CONTENT_TYPE_PLAYLIST },
                        ) { spotifyPlaylist ->
                            val thumbnailUrl = SpotifyMapper.getPlaylistThumbnail(spotifyPlaylist)
                            PlaylistGridItem(
                                playlist = Playlist(
                                    playlist = PlaylistEntity(
                                        id = "spotify_${spotifyPlaylist.id}",
                                        name = spotifyPlaylist.name,
                                        thumbnailUrl = thumbnailUrl,
                                    ),
                                    songCount = 0,
                                    songThumbnails = listOfNotNull(thumbnailUrl),
                                ),
                                fillMaxWidth = true,
                                autoPlaylist = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("spotify_playlist/${spotifyPlaylist.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SpotifyPlaylistMenu(
                                                    playlist = spotifyPlaylist,
                                                    onNavigate = { navController.navigate("spotify_playlist/${spotifyPlaylist.id}") },
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    )
                                    .animateItem(),
                            )
                        }
                    }
                }

                HideOnScrollFAB(
                    lazyListState = lazyGridState,
                    icon = R.drawable.add,
                    onClick = {
                        showCreatePlaylistDialog = true
                    },
                )
            }
        }
    }
}
