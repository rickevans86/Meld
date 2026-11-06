/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.radio

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.radio.RadioStation
import com.metrolist.music.viewmodels.RadioList
import com.metrolist.music.viewmodels.RadioViewModel

/** Internet radio: favourites, popular stations near you, genres and search. */
@Composable
fun RadioScreen(
    navController: NavController,
    viewModel: RadioViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val list by viewModel.list.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val current by playerConnection.mediaMetadata.collectAsState()
    // Typed in the top-bar search (search button on this tab)
    val query = com.metrolist.music.ui.component.rememberPageSearchQuery("radio")
    com.metrolist.music.ui.component.PageSearchEffect(query) {
        // Clearing the search goes back to Popular, unless a chip was picked meanwhile
        if (it.isNotBlank() || viewModel.list.value is RadioList.Search) viewModel.search(it)
    }
    var addDialogLink by rememberSaveable { mutableStateOf<String?>(null) }
    val shown = if (list is RadioList.Favourites) favourites else stations
    val pastedLink = com.metrolist.music.podcasts.PodcastDirectory.linkIn(query)

    addDialogLink?.let { initial ->
        AddStationDialog(
            initialLink = initial,
            onDismiss = { addDialogLink = null },
            onAdd = { name, link, onError ->
                viewModel.addCustom(name, link) { error ->
                    if (error == null) {
                        addDialogLink = null
                        com.metrolist.music.ui.component.PageSearch.close()
                    } else {
                        onError(error)
                    }
                }
            },
        )
    }
    val queueTitle = stringResource(R.string.radio_tab)

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "chips") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
            ) {
                item {
                    androidx.compose.material3.AssistChip(
                        onClick = { addDialogLink = "" },
                        label = { Text(stringResource(R.string.radio_add_station)) },
                        leadingIcon = { Icon(painterResource(R.drawable.add), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
                item {
                    FilterChip(
                        selected = list is RadioList.Favourites,
                        onClick = { com.metrolist.music.ui.component.PageSearch.close(); viewModel.show(RadioList.Favourites) },
                        label = { Text(stringResource(R.string.radio_favourites)) },
                    )
                }
                item {
                    FilterChip(
                        selected = list is RadioList.Popular,
                        onClick = { com.metrolist.music.ui.component.PageSearch.close(); viewModel.show(RadioList.Popular) },
                        label = { Text(stringResource(R.string.radio_popular)) },
                    )
                }
                items(RadioViewModel.GENRES) { (tag, label) ->
                    FilterChip(
                        selected = (list as? RadioList.Genre)?.tag == tag,
                        onClick = { com.metrolist.music.ui.component.PageSearch.close(); viewModel.show(RadioList.Genre(tag, label)) },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (pastedLink != null) {
            item(key = "pasted_link") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { addDialogLink = pastedLink }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(painterResource(R.drawable.add), contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.radio_add_this_link), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (list is RadioList.Favourites || list is RadioList.Search) {
            item(key = "find_online") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.radio_find_online),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { navController.navigate("radio_web?url=" + android.net.Uri.encode("https://streamurl.link/")) }) {
                            Text("StreamURL.link")
                        }
                        TextButton(onClick = { navController.navigate("radio_web?url=" + android.net.Uri.encode("https://fmstream.org/")) }) {
                            Text("FMSTREAM")
                        }
                        TextButton(onClick = { addDialogLink = "" }) { Text(stringResource(R.string.radio_add_station)) }
                    }
                }
            }
        }
        val playingStation = current?.id?.takeIf { com.metrolist.music.constants.isRadioId(it) }
        val playingTitle = nowPlaying?.takeIf { it.first == playingStation }?.second
        if (playingStation != null && playingTitle != null) {
            item(key = "now_playing") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(12.dp),
                ) {
                    Text(
                        stringResource(R.string.radio_now_playing, current?.title.orEmpty()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        playingTitle,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        when {
            loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> item(key = "error") { Hint(error.orEmpty()) }
            shown.isEmpty() -> item(key = "empty") {
                Hint(
                    stringResource(
                        if (list is RadioList.Favourites) R.string.radio_no_favourites else R.string.radio_no_results,
                    ),
                )
            }
        }
        if (!loading) {
            itemsIndexed(shown, key = { _, station -> "station:${station.id}" }) { index, station ->
                StationRow(
                    station = station,
                    isCurrent = current?.id == station.songId,
                    isFavourite = favourites.any { it.id == station.id },
                    onToggleFavourite = { viewModel.toggleFavourite(station) },
                    onClick = {
                        if (current?.id == station.songId) {
                            playerConnection.togglePlayPause()
                        } else {
                            viewModel.play(shown, index) { songs, start ->
                                playerConnection.playQueue(
                                    ListQueue(title = queueTitle, items = songs.map { it.toMediaItem() }, startIndex = start),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun AddStationDialog(
    initialLink: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onAdd: (name: String, link: String, onError: (String) -> Unit) -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var link by rememberSaveable { mutableStateOf(initialLink) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.radio_add_station)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.radio_add_station_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it; error = null },
                    singleLine = true,
                    label = { Text(stringResource(R.string.radio_stream_link)) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                                ?.let { pasted -> link = com.metrolist.music.podcasts.PodcastDirectory.linkIn(pasted) ?: pasted }
                        }) {
                            Icon(painterResource(R.drawable.content_paste), contentDescription = stringResource(R.string.podcast_paste))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.radio_station_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !adding && link.isNotBlank(),
                onClick = {
                    adding = true
                    onAdd(name, link) { message ->
                        adding = false
                        error = message
                    }
                },
            ) { Text(stringResource(R.string.radio_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun StationRow(
    station: RadioStation,
    isCurrent: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Icon(painterResource(R.drawable.radio), contentDescription = null)
            if (station.favicon != null) {
                AsyncImage(
                    model = station.favicon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (station.details.isNotEmpty()) {
                Text(
                    station.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFavourite) {
            Icon(
                painterResource(if (isFavourite) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(R.string.radio_favourite),
                tint = if (isFavourite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
