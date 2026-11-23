package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.viewmodels.SpotifyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistsScreen(navController: NavController, viewModel: SpotifyViewModel = hiltViewModel()) {
    val playlists by viewModel.spotifyPlaylists.collectAsState()
    val loading by viewModel.playlistsLoading.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPlaylists() }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.spotify_your_playlists)) },
            navigationIcon = { IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
            } },
            actions = { IconButton(onClick = viewModel::loadPlaylists) {
                Icon(painterResource(R.drawable.refresh), stringResource(R.string.browse_refresh))
            } },
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom).asPaddingValues()) {
            if (!loading && playlists.isEmpty()) item { Text(stringResource(R.string.spotify_no_playlists), Modifier.padding(24.dp)) }
            items(playlists.distinctBy { it.id }, key = { it.id }) { playlist ->
                ListItem(
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { playlist.description?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it) } },
                    leadingContent = { AsyncImage(playlist.images.firstOrNull()?.url, null, Modifier.size(64.dp)) },
                    modifier = Modifier.clickable { navController.navigate("spotify_playlist/${playlist.id}") },
                )
            }
        }
    }
}
