/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.metrolist.innertube.utils.completed
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.DisabledFeaturesKey
import com.metrolist.music.constants.HOME_SOURCE_SPOTIFY
import com.metrolist.music.constants.HOME_SOURCE_YOUTUBE
import com.metrolist.music.constants.WavelengthFeature
import com.metrolist.music.constants.hidesRoute
import com.metrolist.music.constants.rememberDefaultHomeSource
import com.metrolist.music.constants.rememberDisabledFeatures
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.launch

/** Turn Wavelength features off: they're hidden from the bottom bar, side menu and Android Auto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesSettings(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val disabled = rememberDisabledFeatures()
    val secondIsYouTube = rememberDefaultHomeSource() == HOME_SOURCE_SPOTIFY

    fun setDefaultHome(source: String) {
        scope.launch { context.dataStore.edit { it[com.metrolist.music.constants.DefaultHomeSourceKey] = source } }
    }

    // Bottom bar: which screens are on it (up to 5) and in what order
    val barTabs = com.metrolist.music.constants.rememberBottomTabs()
    val barChoices = com.metrolist.music.constants.bottomTabChoices(alternateIsSpotify = !secondIsYouTube)
        .filterNot { disabled.hidesRoute(it.route) }
    val shownTabs = barTabs.filter { route -> barChoices.any { it.route == route } }

    fun saveTabs(routes: List<String>) {
        scope.launch { context.dataStore.edit { it[com.metrolist.music.constants.BottomTabsKey] = routes.joinToString(",") } }
    }

    fun setOnBar(route: String, on: Boolean) {
        if (on) {
            if (route !in barTabs && shownTabs.size < com.metrolist.music.constants.MAX_BOTTOM_TABS) saveTabs(barTabs + route)
        } else if (shownTabs.size > 1) {
            saveTabs(barTabs - route)
        }
    }

    fun moveTab(route: String, offset: Int) {
        val from = shownTabs.indexOf(route)
        val to = from + offset
        if (from < 0 || to !in shownTabs.indices) return
        val other = shownTabs[to]
        // Swap within the full saved list so tabs of switched-off features keep their place
        saveTabs(barTabs.map { if (it == route) other else if (it == other) route else it })
    }

    fun setEnabled(feature: WavelengthFeature, enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val current = prefs[DisabledFeaturesKey] ?: com.metrolist.music.constants.DEFAULT_DISABLED_FEATURE_IDS
                prefs[DisabledFeaturesKey] = if (enabled) current - feature.id else current + feature.id
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)))
        Text(
            stringResource(R.string.features_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        // Which music source is the Home tab; the other one is the "second source" below
        Material3SettingsGroup(
            title = stringResource(R.string.features_default_home),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.home_outlined),
                    title = { Text(stringResource(R.string.features_default_home)) },
                    description = { Text(stringResource(R.string.features_default_home_desc)) },
                    trailingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = !secondIsYouTube,
                                onClick = { setDefaultHome(HOME_SOURCE_YOUTUBE) },
                                label = { Text(stringResource(R.string.home_youtube)) },
                            )
                            FilterChip(
                                selected = secondIsYouTube,
                                onClick = { setDefaultHome(HOME_SOURCE_SPOTIFY) },
                                label = { Text(stringResource(R.string.home_spotify)) },
                            )
                        }
                    },
                ),
            ),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.bottom_bar_intro, com.metrolist.music.constants.MAX_BOTTOM_TABS),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        // On-bar tabs first in bar order, then the rest in side-menu order
        val orderedChoices = shownTabs.mapNotNull { route -> barChoices.firstOrNull { it.route == route } } +
            barChoices.filter { it.route !in shownTabs }
        Material3SettingsGroup(
            title = stringResource(R.string.bottom_bar),
            items = orderedChoices.map { screen ->
                val position = shownTabs.indexOf(screen.route)
                val on = position >= 0
                val canChange = if (on) shownTabs.size > 1 else shownTabs.size < com.metrolist.music.constants.MAX_BOTTOM_TABS
                Material3SettingsItem(
                    icon = painterResource(screen.iconIdInactive),
                    title = { Text(stringResource(screen.titleId)) },
                    description = {
                        Text(
                            when {
                                on -> stringResource(R.string.bottom_bar_position, position + 1)
                                !canChange -> stringResource(R.string.bottom_bar_full, com.metrolist.music.constants.MAX_BOTTOM_TABS)
                                else -> stringResource(R.string.bottom_bar_menu_only)
                            },
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (on) {
                                androidx.compose.material3.IconButton(onClick = { moveTab(screen.route, -1) }, enabled = position > 0) {
                                    Icon(painterResource(R.drawable.arrow_upward), contentDescription = stringResource(R.string.news_move_up))
                                }
                                androidx.compose.material3.IconButton(
                                    onClick = { moveTab(screen.route, 1) },
                                    enabled = position < shownTabs.size - 1,
                                ) {
                                    Icon(painterResource(R.drawable.arrow_downward), contentDescription = stringResource(R.string.news_move_down))
                                }
                            }
                            Switch(
                                checked = on,
                                enabled = canChange,
                                onCheckedChange = { setOnBar(screen.route, it) },
                            )
                        }
                    },
                    onClick = { if (canChange) setOnBar(screen.route, !on) },
                )
            },
        )
        Spacer(Modifier.height(16.dp))
        Material3SettingsGroup(
            title = stringResource(R.string.features),
            items = WavelengthFeature.entries.map { feature ->
                val enabled = feature !in disabled
                val (title, icon) = when (feature) {
                    WavelengthFeature.SECOND_SOURCE ->
                        if (secondIsYouTube) R.string.home_youtube to R.drawable.home_outlined else R.string.home_spotify to R.drawable.spotify
                    WavelengthFeature.NEWS -> R.string.news_tab to R.drawable.newspaper
                    WavelengthFeature.NAVIDROME -> R.string.subsonic_tab to R.drawable.dns
                    WavelengthFeature.MEDIA_SERVER -> R.string.media_server_tab to R.drawable.dns
                    WavelengthFeature.AUDIOBOOKS -> R.string.filter_audiobooks to R.drawable.audiobook
                    WavelengthFeature.PODCASTS -> R.string.filter_podcasts to R.drawable.podcasts
                    WavelengthFeature.LOCAL_FILES -> R.string.filter_local_files to R.drawable.folder
                    WavelengthFeature.RADIO -> R.string.radio_tab to R.drawable.radio
                }
                val description: (@Composable () -> Unit)? =
                    if (feature == WavelengthFeature.SECOND_SOURCE) {
                        { Text(stringResource(R.string.features_second_source_desc)) }
                    } else {
                        null
                    }
                Material3SettingsItem(
                    icon = painterResource(icon),
                    title = { Text(stringResource(title)) },
                    description = description,
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = { setEnabled(feature, it) },
                            thumbContent = {
                                Icon(
                                    painterResource(if (enabled) R.drawable.check else R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(SwitchDefaults.IconSize),
                                )
                            },
                        )
                    },
                    onClick = { setEnabled(feature, !enabled) },
                )
            },
        )
        Spacer(Modifier.height(16.dp))
        RecognisedSongsSetting()
        Spacer(Modifier.height(16.dp))
        RadioSongsSetting()
        Spacer(Modifier.height(16.dp))
        PodcastPlaylistsSetting()
    }

    TopAppBar(
        title = { Text(stringResource(R.string.features)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        },
    )
}

/**
 * Song recognition: which playlist recognised songs are added to automatically (or off).
 * Lists Wavelength's own editable playlists, plus an option to create a "Recognised songs" playlist.
 */
@Composable
private fun RecognisedSongsSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = com.metrolist.music.LocalDatabase.current
    val playlists by database.playlistsByCreateDateAsc().collectAsState(initial = emptyList())
    val editable = playlists.filter { it.playlist.isEditable }
    val selectedId by remember(context) {
        context.dataStore.data.map { it[com.metrolist.music.recognition.RecognisedSongs.PlaylistKey].orEmpty() }
    }.collectAsState(initial = "")
    val selected = editable.firstOrNull { it.id == selectedId }
    var choosing by remember { mutableStateOf(false) }
    val newName = stringResource(R.string.recognised_default_playlist)

    fun choose(id: String) {
        scope.launch { context.dataStore.edit { it[com.metrolist.music.recognition.RecognisedSongs.PlaylistKey] = id } }
        choosing = false
    }

    var choosingAutoOff by remember { mutableStateOf(false) }
    val autoOffMinutes by remember(context) {
        context.dataStore.data.map {
            it[com.metrolist.music.recognition.AutoRecognitionService.AutoOffMinutesKey]
                ?: com.metrolist.music.recognition.AutoRecognitionService.DEFAULT_AUTO_OFF_MINUTES
        }
    }.collectAsState(initial = com.metrolist.music.recognition.AutoRecognitionService.DEFAULT_AUTO_OFF_MINUTES)

    // Keep listening needs the microphone; ask for it the first time
    val keepListening by com.metrolist.music.recognition.AutoRecognitionService.running.collectAsState()
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) com.metrolist.music.recognition.AutoRecognitionService.start(context) }
    fun setKeepListening(on: Boolean) {
        when {
            !on -> com.metrolist.music.recognition.AutoRecognitionService.stop(context)
            com.metrolist.music.recognition.MusicRecognitionService.hasRecordPermission(context) ->
                com.metrolist.music.recognition.AutoRecognitionService.start(context)
            else -> micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    Material3SettingsGroup(
        title = stringResource(R.string.recognised_section),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.mic),
                title = { Text(stringResource(R.string.recognised_playlist_setting)) },
                description = {
                    Text(selected?.playlist?.name ?: stringResource(R.string.recognised_off))
                },
                onClick = { choosing = true },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.mic),
                title = { Text(stringResource(R.string.auto_recognise_toggle)) },
                description = { Text(stringResource(R.string.auto_recognise_desc)) },
                trailingContent = { Switch(checked = keepListening, onCheckedChange = { setKeepListening(it) }) },
                onClick = { setKeepListening(!keepListening) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.timer),
                title = { Text(stringResource(R.string.auto_recognise_off_after)) },
                description = { Text(autoOffLabel(autoOffMinutes)) },
                onClick = { choosingAutoOff = true },
            ),
        ),
    )

    if (choosingAutoOff) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosingAutoOff = false },
            title = { Text(stringResource(R.string.auto_recognise_off_after)) },
            text = {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(360.dp)) {
                    // Never, then every 30 minutes up to 12 hours
                    val options = listOf(0) + (1..24).map { it * 30 }
                    items(options.size) { index ->
                        val minutes = options[index]
                        RecognisedChoice(autoOffLabel(minutes), minutes == autoOffMinutes) {
                            scope.launch {
                                context.dataStore.edit { it[com.metrolist.music.recognition.AutoRecognitionService.AutoOffMinutesKey] = minutes }
                            }
                            choosingAutoOff = false
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { choosingAutoOff = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (choosing) {
        PlaylistTargetDialog(
            title = stringResource(R.string.recognised_playlist_setting),
            offLabel = stringResource(R.string.recognised_off),
            newName = newName,
            editable = editable,
            selectedId = selectedId,
            onChoose = ::choose,
            onDismiss = { choosing = false },
        )
    }
}

/**
 * Radio songs: the playlist the add button in Android Auto puts the radio station's current song into.
 */
@Composable
private fun RadioSongsSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = com.metrolist.music.LocalDatabase.current
    val playlists by database.playlistsByCreateDateAsc().collectAsState(initial = emptyList())
    val editable = playlists.filter { it.playlist.isEditable }
    val selectedId by remember(context) {
        context.dataStore.data.map { it[com.metrolist.music.recognition.RecognisedSongs.RadioPlaylistKey].orEmpty() }
    }.collectAsState(initial = "")
    val selected = editable.firstOrNull { it.id == selectedId }
    var choosing by remember { mutableStateOf(false) }

    fun choose(id: String) {
        scope.launch { context.dataStore.edit { it[com.metrolist.music.recognition.RecognisedSongs.RadioPlaylistKey] = id } }
        choosing = false
    }

    Material3SettingsGroup(
        title = stringResource(R.string.radio_songs_section),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.radio),
                title = { Text(stringResource(R.string.radio_songs_setting)) },
                description = {
                    Text(
                        (selected?.playlist?.name ?: stringResource(R.string.recognised_off)) + "\n" +
                            stringResource(R.string.radio_songs_desc),
                    )
                },
                onClick = { choosing = true },
            ),
        ),
    )

    if (choosing) {
        PlaylistTargetDialog(
            title = stringResource(R.string.radio_songs_setting),
            offLabel = stringResource(R.string.recognised_off),
            newName = stringResource(R.string.radio_songs_default_playlist),
            editable = editable,
            selectedId = selectedId,
            onChoose = ::choose,
            onDismiss = { choosing = false },
        )
    }
}

/**
 * Picks the playlist songs are added to automatically: Off, a new playlist called [newName]
 * (created as a private YouTube playlist when signed in), or one of your editable playlists.
 */
@Composable
private fun PlaylistTargetDialog(
    title: String,
    offLabel: String,
    newName: String,
    editable: List<com.metrolist.music.db.entities.Playlist>,
    selectedId: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selected = editable.firstOrNull { it.id == selectedId }
    fun choose(id: String) = onChoose(id)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn {
                item {
                    RecognisedChoice(offLabel, selected == null) { choose("") }
                }
                if (editable.none { it.playlist.name.equals(newName, ignoreCase = true) }) {
                    item {
                        RecognisedChoice(stringResource(R.string.recognised_create, newName), false) {
                            // New playlist named [newName], created on your YouTube account as a private
                            // (hidden) playlist and linked here, then selected. Without a YouTube sign-in it's
                            // made in Wavelength only. bookmarkedAt makes it one of your saved playlists.
                            val syncUtils = dagger.hilt.android.EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                com.metrolist.music.recognition.RecognisedSongs.Access::class.java,
                            ).syncUtils()
                            syncUtils.createPlaylist(
                                playlist = com.metrolist.music.db.entities.PlaylistEntity(
                                    name = newName,
                                    bookmarkedAt = java.time.LocalDateTime.now(),
                                    isEditable = true,
                                ),
                                syncWithYouTube = true,
                            ) { playlistId, _ -> choose(playlistId) }
                            onDismiss()
                        }
                    }
                }
                items(editable.size) { index ->
                    val playlist = editable[index]
                    RecognisedChoice(playlist.playlist.name, playlist.id == selectedId) { choose(playlist.id) }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun RecognisedChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

/** One row in the "Playlists to show as podcasts" list. */
private sealed interface PodcastPlaylistChoice {
    val key: String
    val name: String

    data class Saved(val playlist: com.metrolist.music.db.entities.Playlist) : PodcastPlaylistChoice {
        override val key get() = playlist.id
        override val name get() = playlist.playlist.name
    }

    data class Online(val item: com.metrolist.music.constants.YouTubePodcastPlaylist) : PodcastPlaylistChoice {
        override val key get() = "yt:" + item.id
        override val name get() = item.title
    }
}

/**
 * Podcasts: pick any of your playlists (podcast or not) to show in the Podcasts tab and in Android Auto's
 * Podcasts section. Lists playlists saved in Wavelength plus your YouTube account's playlists (including
 * unlisted ones made in the YouTube app), and a YouTube playlist link can be pasted in.
 */
@Composable
private fun PodcastPlaylistsSetting() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = com.metrolist.music.LocalDatabase.current
    val playlists by database.playlistsByCreateDateAsc().collectAsState(initial = emptyList())
    val chosenSaved = com.metrolist.music.constants.rememberPodcastPlaylistIds()
    val chosenOnline = com.metrolist.music.constants.rememberYouTubePodcastPlaylists()
    var choosing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var online by remember { mutableStateOf<List<com.metrolist.music.constants.YouTubePodcastPlaylist>?>(null) }
    val chosenCount = chosenSaved.count { id -> playlists.any { it.id == id } } + chosenOnline.size
    // Episode downloads: on play, and delete once finished (both on unless switched off)
    val (downloadOnPlay, onDownloadOnPlay) = com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.podcasts.EpisodeDownloads.DownloadOnPlayKey,
        true,
    )
    val (deleteOnFinish, onDeleteOnFinish) = com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.podcasts.EpisodeDownloads.DeleteOnFinishKey,
        true,
    )
    val notFound = stringResource(R.string.podcast_playlists_link_bad)
    // Skip buttons: next/previous jump inside an episode (hold to change episode)
    val (skipEnabled, onSkipEnabled) = com.metrolist.music.utils.rememberPreference(com.metrolist.music.podcasts.PodcastSkip.EnabledKey, false)
    val (skipForward, onSkipForward) = com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.podcasts.PodcastSkip.ForwardKey,
        com.metrolist.music.podcasts.PodcastSkip.DEFAULT_FORWARD,
    )
    val (skipBack, onSkipBack) = com.metrolist.music.utils.rememberPreference(
        com.metrolist.music.podcasts.PodcastSkip.BackKey,
        com.metrolist.music.podcasts.PodcastSkip.DEFAULT_BACK,
    )
    var pickingSkip by remember { mutableStateOf<Boolean?>(null) } // true = forward, false = back

    // Your YouTube account's playlists, loaded when the list opens
    LaunchedEffect(choosing) {
        if (!choosing || online != null) return@LaunchedEffect
        online = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.metrolist.innertube.YouTube.library("FEmusic_liked_playlists").completed()
                .getOrNull()?.items.orEmpty()
                .filterIsInstance<com.metrolist.innertube.models.PlaylistItem>()
                .filterNot { it.id == "SE" || it.id == "LM" }
                .map { com.metrolist.music.constants.YouTubePodcastPlaylist(it.id, it.title, it.thumbnail) }
        }
    }

    Material3SettingsGroup(
        title = stringResource(R.string.filter_podcasts),
        items = listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.podcasts),
                title = { Text(stringResource(R.string.podcast_playlists_setting)) },
                description = {
                    Text(
                        if (chosenCount == 0) {
                            stringResource(R.string.podcast_playlists_none)
                        } else {
                            androidx.compose.ui.res.pluralStringResource(R.plurals.podcast_playlists_count, chosenCount, chosenCount)
                        },
                    )
                },
                onClick = { choosing = true },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.download),
                title = { Text(stringResource(R.string.episode_download_on_play)) },
                description = { Text(stringResource(R.string.episode_download_on_play_desc)) },
                trailingContent = { Switch(checked = downloadOnPlay, onCheckedChange = onDownloadOnPlay) },
                onClick = { onDownloadOnPlay(!downloadOnPlay) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.delete),
                title = { Text(stringResource(R.string.episode_delete_on_finish)) },
                description = { Text(stringResource(R.string.episode_delete_on_finish_desc)) },
                trailingContent = { Switch(checked = deleteOnFinish, onCheckedChange = onDeleteOnFinish) },
                onClick = { onDeleteOnFinish(!deleteOnFinish) },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.fast_forward),
                title = { Text(stringResource(R.string.podcast_skip_setting)) },
                description = { Text(stringResource(R.string.podcast_skip_setting_desc)) },
                trailingContent = { Switch(checked = skipEnabled, onCheckedChange = onSkipEnabled) },
                onClick = { onSkipEnabled(!skipEnabled) },
            ),
        ) + if (skipEnabled) listOf(
            Material3SettingsItem(
                icon = painterResource(R.drawable.skip_next),
                title = { Text(stringResource(R.string.podcast_skip_forward)) },
                description = { Text(com.metrolist.music.podcasts.PodcastSkip.label(skipForward)) },
                onClick = { pickingSkip = true },
            ),
            Material3SettingsItem(
                icon = painterResource(R.drawable.skip_previous),
                title = { Text(stringResource(R.string.podcast_skip_back)) },
                description = { Text(com.metrolist.music.podcasts.PodcastSkip.label(skipBack)) },
                onClick = { pickingSkip = false },
            ),
        ) else emptyList(),
    )

    pickingSkip?.let { forward ->
        val current = if (forward) skipForward else skipBack
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickingSkip = null },
            title = { Text(stringResource(if (forward) R.string.podcast_skip_forward else R.string.podcast_skip_back)) },
            text = {
                Column {
                    com.metrolist.music.podcasts.PodcastSkip.CHOICES.forEach { seconds ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (forward) onSkipForward(seconds) else onSkipBack(seconds)
                                    pickingSkip = null
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            androidx.compose.material3.RadioButton(selected = seconds == current, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(com.metrolist.music.podcasts.PodcastSkip.label(seconds))
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { pickingSkip = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (choosing) {
        val savedBrowseIds = playlists.mapNotNull { it.playlist.browseId }.toSet()
        val chosenOnlineIds = chosenOnline.map { it.id }.toSet()
        // Saved playlists, then account playlists Wavelength hasn't saved, then ticked links not in either
        val onlineItems = (online.orEmpty() + chosenOnline).distinctBy { it.id }.filter { it.id !in savedBrowseIds }
        val all: List<PodcastPlaylistChoice> =
            playlists.map { PodcastPlaylistChoice.Saved(it) } + onlineItems.map { PodcastPlaylistChoice.Online(it) }
        fun isTicked(choice: PodcastPlaylistChoice) = when (choice) {
            is PodcastPlaylistChoice.Saved -> choice.playlist.id in chosenSaved
            is PodcastPlaylistChoice.Online -> choice.item.id in chosenOnlineIds
        }
        fun toggle(choice: PodcastPlaylistChoice, on: Boolean) = scope.launch {
            when (choice) {
                is PodcastPlaylistChoice.Saved -> com.metrolist.music.constants.setPodcastPlaylist(context, choice.playlist.id, on)
                is PodcastPlaylistChoice.Online -> com.metrolist.music.constants.setYouTubePodcastPlaylist(context, choice.item, on)
            }
        }
        val shown = all.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            // Ticked playlists first, then the rest A–Z
            .sortedWith(compareBy({ !isTicked(it) }, { it.name.lowercase() }))
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(stringResource(R.string.podcast_playlists_setting)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.podcast_playlists_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.podcast_playlists_search)) },
                        leadingIcon = { Icon(painterResource(R.drawable.search), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    // Paste a YouTube playlist link for one that isn't listed
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.OutlinedTextField(
                            value = link,
                            onValueChange = { link = it },
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.podcast_playlists_link)) },
                            leadingIcon = { Icon(painterResource(R.drawable.link), contentDescription = null) },
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        androidx.compose.material3.TextButton(
                            enabled = link.isNotBlank(),
                            onClick = {
                                val id = com.metrolist.music.constants.youTubePlaylistIdFrom(link)
                                if (id == null) {
                                    android.widget.Toast.makeText(context, notFound, android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    scope.launch {
                                        val page = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.metrolist.innertube.YouTube.playlist(id).getOrNull()
                                        }
                                        com.metrolist.music.constants.setYouTubePodcastPlaylist(
                                            context,
                                            com.metrolist.music.constants.YouTubePodcastPlaylist(
                                                id,
                                                page?.playlist?.title ?: id,
                                                page?.playlist?.thumbnail,
                                            ),
                                            true,
                                        )
                                        link = ""
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.podcast_playlists_add)) }
                    }
                    if (online == null) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.podcast_playlists_loading),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.height(320.dp)) {
                        items(shown.size, key = { shown[it].key }) { index ->
                            val choice = shown[index]
                            val ticked = isTicked(choice)
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle(choice, !ticked) }
                                    .padding(vertical = 4.dp),
                            ) {
                                androidx.compose.material3.Checkbox(checked = ticked, onCheckedChange = { toggle(choice, it) })
                                val source = when (choice) {
                                    is PodcastPlaylistChoice.Saved -> com.metrolist.music.ui.component.playlistSourceOf(choice.playlist)
                                    is PodcastPlaylistChoice.Online -> com.metrolist.music.ui.component.PlaylistSource.YOUTUBE
                                }
                                source?.let {
                                    com.metrolist.music.ui.component.PlaylistSourceLogo(it, size = 16.dp, modifier = Modifier.padding(end = 6.dp))
                                }
                                Text(choice.name, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { choosing = false }) { Text(stringResource(R.string.podcast_playlists_done)) }
            },
        )
    }
}

/** "Never", "30 minutes", "1 hour", "1 hour 30 minutes"… */
@Composable
private fun autoOffLabel(minutes: Int): String {
    if (minutes <= 0) return stringResource(R.string.auto_recognise_off_never)
    val hours = minutes / 60
    val rest = minutes % 60
    val parts = buildList {
        if (hours > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.auto_recognise_hours, hours, hours))
        if (rest > 0) add(androidx.compose.ui.res.pluralStringResource(R.plurals.auto_recognise_minutes, rest, rest))
    }
    return parts.joinToString(" ")
}
