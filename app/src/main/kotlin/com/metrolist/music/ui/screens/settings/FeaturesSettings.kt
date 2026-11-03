/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Spacer
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

    fun setEnabled(feature: WavelengthFeature, enabled: Boolean) {
        scope.launch {
            context.dataStore.edit { prefs ->
                val current = prefs[DisabledFeaturesKey].orEmpty()
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
        Material3SettingsGroup(
            title = stringResource(R.string.features),
            items = WavelengthFeature.entries.map { feature ->
                val enabled = feature !in disabled
                val (title, icon) = when (feature) {
                    WavelengthFeature.SECOND_SOURCE ->
                        if (secondIsYouTube) R.string.home_youtube to R.drawable.home_outlined else R.string.home_spotify to R.drawable.spotify
                    WavelengthFeature.NEWS -> R.string.news_tab to R.drawable.newspaper
                    WavelengthFeature.NAVIDROME -> R.string.subsonic_tab to R.drawable.dns
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
