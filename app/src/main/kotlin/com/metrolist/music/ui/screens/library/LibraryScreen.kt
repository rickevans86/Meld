/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalNavController
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumViewTypeKey
import com.metrolist.music.constants.ChipSortTypeKey
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.ui.component.ChipsRow
import com.metrolist.music.ui.screens.library.local.LocalFilesScreen
import com.metrolist.music.utils.rememberEnumPreference
import androidx.navigation.NavController

@Composable
fun LibraryScreen() {
    val navController = LocalNavController.current
    var filterType by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    var libraryViewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    var playlistViewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.GRID)

    // Local files has its own tab, so its Library chip is gone; go back to the main Library view
    androidx.compose.runtime.LaunchedEffect(filterType) {
        if (filterType == LibraryFilter.LOCAL_FILES) filterType = LibraryFilter.LIBRARY
    }

    val filterContent = @Composable {
        Row {
            ChipsRow(
                chips = listOf(
                    LibraryFilter.PLAYLISTS to stringResource(R.string.filter_playlists),
                    LibraryFilter.SONGS to stringResource(R.string.filter_songs),
                    LibraryFilter.ALBUMS to stringResource(R.string.filter_albums),
                    LibraryFilter.ARTISTS to stringResource(R.string.filter_artists),
                ),
                currentValue = filterType,
                onValueUpdate = {
                    filterType = if (filterType == it) LibraryFilter.LIBRARY else it
                },
                modifier = Modifier.weight(1f),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (filterType) {
            LibraryFilter.LIBRARY -> LibraryMixScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = libraryViewType,
                onViewTypeChange = { libraryViewType = it },
            )
            LibraryFilter.PLAYLISTS -> LibraryPlaylistsScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = playlistViewType,
                onViewTypeChange = { playlistViewType = it },
            )
            LibraryFilter.SONGS -> LibrarySongsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ALBUMS -> LibraryAlbumsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )
            LibraryFilter.ARTISTS -> LibraryArtistsScreen(
                navController,
                { filterType = LibraryFilter.LIBRARY },
            )

            LibraryFilter.LOCAL_FILES -> LocalFilesScreen(
                navController,
                filterContent,
            )

            // Podcasts and audiobooks have their own bottom tabs now
            LibraryFilter.PODCASTS, LibraryFilter.AUDIOBOOKS -> LibraryMixScreen(
                navController = navController,
                filterContent = filterContent,
                viewType = libraryViewType,
                onViewTypeChange = { libraryViewType = it },
            )
        }
    }
}
