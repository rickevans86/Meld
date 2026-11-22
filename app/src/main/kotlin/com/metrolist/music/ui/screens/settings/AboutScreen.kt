/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.graphics.shapes.RoundedPolygon
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.playback.PlayerConnection
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.Button

private data class Contributor(
    val name: String,
    val roleRes: Int,
    val githubHandle: String,
    val avatarUrl: String = "https://github.com/$githubHandle.png",
    val githubUrl: String = "https://github.com/$githubHandle",
    val sponsorUrl: String? = null,
    val polygon: RoundedPolygon? = null,
    val favoriteSongVideoId: String? = null
)

private data class CommunityLink(
    val labelRes: Int,
    val iconRes: Int,
    val url: String
)

private val meldDeveloper = Contributor(
    name = "Francesco Grazioso",
    roleRes = R.string.credits_meld_developer,
    githubHandle = "FrancescoGrazioso",
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val upstreamLeadDeveloper = Contributor(
    name = "Mo Agamy",
    roleRes = R.string.credits_lead_developer,
    githubHandle = "mostafaalagamy",
    polygon = MaterialShapes.Cookie9Sided,
    favoriteSongVideoId = "Mh2JWGWvy_Y"
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val upstreamCollaborators = listOf(
    Contributor(name = "Adriel O'Connel", roleRes = R.string.credits_collaborator, githubHandle = "adrielGGmotion", polygon = MaterialShapes.Cookie4Sided, favoriteSongVideoId = "m2zUrruKjDQ"),
    Contributor(name = "Nyx", roleRes = R.string.credits_collaborator, githubHandle = "nyxiereal", polygon = MaterialShapes.Cookie12Sided, favoriteSongVideoId = "zselaN6zPXw"),
)

private val communityLinks = listOf(
    CommunityLink(R.string.credits_view_repo, R.drawable.github, "https://github.com/rickevans86/Meld"),
    CommunityLink(R.string.credits_license_name, R.drawable.info, "https://github.com/rickevans86/Meld/blob/main/LICENSE")
)

private fun handleEasterEggClick(
    clickCount: Int,
    favoriteSongVideoId: String?,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    playerConnection: PlayerConnection?,
    wannaPlayStr: String,
    yeahStr: String,
    onCountUpdate: (Int) -> Unit
) {
    if (favoriteSongVideoId != null) {
        val newCount = clickCount + 1
        onCountUpdate(newCount)
        if (newCount >= 3) {
            onCountUpdate(0)
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = wannaPlayStr,
                    actionLabel = yeahStr,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    playerConnection?.playQueue(YouTubeQueue(WatchEndpoint(videoId = favoriteSongVideoId)))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 8.dp, top = 8.dp),
        textAlign = TextAlign.Start
    )
}

@Composable
private fun ContributorAvatar(
    avatarUrl: String,
    sizeDp: Int,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    val fallback = painterResource(R.drawable.small_icon)
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier.size(sizeDp.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
    ) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            placeholder = fallback,
            fallback = fallback,
            error = fallback,
        )
    }
}

/** Action button for the 3-segment row under the lead developer */
@Composable
private fun RowScope.SegmentedActionButton(
    label: String,
    iconRes: Int,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.weight(1f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.height(72.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A generic clickable card for secondary actions like Buy Me a Coffee */
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(20.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(20.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var showWavelengthFeatures by rememberSaveable { mutableStateOf(false) }
    if (showWavelengthFeatures) {
        AlertDialog(
            onDismissRequest = { showWavelengthFeatures = false },
            title = { Text(stringResource(R.string.wavelength_additions_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.wavelength_additions_intro))
                    listOf(
                        R.string.wavelength_additions_layout_title to R.string.wavelength_additions_layout_desc,
                        R.string.wavelength_additions_homes_title to R.string.wavelength_additions_homes_desc,
                        R.string.wavelength_additions_offline_title to R.string.wavelength_additions_offline_desc,
                        R.string.wavelength_additions_downloads_title to R.string.wavelength_additions_downloads_desc,
                        R.string.wavelength_additions_local_title to R.string.wavelength_additions_local_desc,
                        R.string.wavelength_additions_books_title to R.string.wavelength_additions_books_desc,
                        R.string.wavelength_additions_book_player_title to R.string.wavelength_additions_book_player_desc,
                        R.string.wavelength_additions_abs_title to R.string.wavelength_additions_abs_desc,
                        R.string.wavelength_additions_podcasts_title to R.string.wavelength_additions_podcasts_desc,
                        R.string.wavelength_additions_episodes_title to R.string.wavelength_additions_episodes_desc,
                        R.string.wavelength_additions_podcast_controls_title to R.string.wavelength_additions_podcast_controls_desc,
                        R.string.wavelength_additions_radio_title to R.string.wavelength_additions_radio_desc,
                        R.string.wavelength_additions_recognition_title to R.string.wavelength_additions_recognition_desc,
                        R.string.wavelength_additions_news_title to R.string.wavelength_additions_news_desc,
                        R.string.wavelength_additions_servers_title to R.string.wavelength_additions_servers_desc,
                        R.string.wavelength_additions_auto_title to R.string.wavelength_additions_auto_desc,
                        R.string.wavelength_additions_output_title to R.string.wavelength_additions_output_desc,
                        R.string.wavelength_additions_updates_title to R.string.wavelength_additions_updates_desc,
                        R.string.wavelength_additions_integrations_title to R.string.wavelength_additions_integrations_desc,
                    ).forEach { (title, description) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(description))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWavelengthFeatures = false }) {
                    Text(stringResource(R.string.close))
                }
            },
        )
    }
    val uriHandler = LocalUriHandler.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val localSnackbarHostState = remember { SnackbarHostState() }
    val wannaPlayStr = stringResource(R.string.wanna_play_favorite_song)
    val yeahStr = stringResource(R.string.yeah)
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .verticalScroll(rememberScrollState())
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
                )
            )
    
            Spacer(Modifier.height(16.dp))
    
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialShapes.SoftBurst.toShape())
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(R.drawable.small_icon),
                            contentDescription = stringResource(R.string.metrolist),
                            colorFilter = ColorFilter.tint(
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                blendMode = BlendMode.SrcIn,
                            ),
                            modifier = Modifier.size(40.dp)
                        )
                    }
            
                    Spacer(Modifier.height(16.dp))
            
                    Text(
                        text = stringResource(R.string.metrolist),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = MaterialTheme.typography.headlineMedium.letterSpacing
                    )
            
                    Spacer(Modifier.height(8.dp))
            
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        val archText = BuildConfig.ARCHITECTURE.uppercase()
                        val versionText = if (BuildConfig.DEBUG) {
                            stringResource(R.string.app_version_info, BuildConfig.VERSION_NAME, "$archText • DEBUG")
                        } else {
                            stringResource(R.string.app_version_info, BuildConfig.VERSION_NAME, archText)
                        }
                        Text(
                            text = versionText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
    
            Spacer(Modifier.height(32.dp))
    
            LinearWavyProgressIndicator(
                progress = { 1f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                trackColor = Color.Transparent,
                amplitude = { 1f }
            )
    
            Spacer(Modifier.height(32.dp))
    
            Button(
                onClick = { showWavelengthFeatures = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringResource(R.string.wavelength_additions_title))
            }

            Spacer(Modifier.height(24.dp))

            SectionHeader(stringResource(R.string.credits_original_project))

            Text(
                text = stringResource(R.string.credits_based_on_metrolist),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 12.dp),
                textAlign = TextAlign.Start
            )

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                val allUpstreamContributors = listOf(meldDeveloper, upstreamLeadDeveloper) + upstreamCollaborators
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    allUpstreamContributors.forEachIndexed { index, contributor ->
                        var clickCount by remember(contributor.name) { mutableIntStateOf(0) }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = contributor.name,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = { Text(stringResource(contributor.roleRes)) },
                            leadingContent = {
                                ContributorAvatar(
                                    avatarUrl = contributor.avatarUrl,
                                    sizeDp = 56,
                                    shape = contributor.polygon?.toShape() ?: CircleShape,
                                    contentDescription = contributor.name,
                                    onClick = {
                                        handleEasterEggClick(
                                            clickCount = clickCount,
                                            favoriteSongVideoId = contributor.favoriteSongVideoId,
                                            coroutineScope = coroutineScope,
                                            snackbarHostState = localSnackbarHostState,
                                            playerConnection = playerConnection,
                                            wannaPlayStr = wannaPlayStr,
                                            yeahStr = yeahStr,
                                            onCountUpdate = { clickCount = it }
                                        )
                                    }
                                )
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.github),
                                    contentDescription = stringResource(R.string.credits_github),
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { uriHandler.openUri(contributor.githubUrl) }
                        )
                        
                        if (index < allUpstreamContributors.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
    
            Spacer(Modifier.height(32.dp))
    
            SectionHeader(stringResource(R.string.community_and_info))
    
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    communityLinks.forEachIndexed { index, link ->
                        ListItem(
                            headlineContent = { Text(stringResource(link.labelRes), fontWeight = FontWeight.SemiBold) },
                            supportingContent = if (link.labelRes == R.string.credits_license_name) {
                                { Text(stringResource(R.string.credits_license_desc)) }
                            } else null,
                            leadingContent = { Icon(painterResource(link.iconRes), null, modifier = Modifier.size(24.dp)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { uriHandler.openUri(link.url) }
                        )
                        
                        if (index < communityLinks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
    
            Spacer(Modifier.height(40.dp))
        }

        TopAppBar(
            title = { Text(stringResource(R.string.about)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )

        androidx.compose.material3.SnackbarHost(
            hostState = localSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                )
        )
    }
}
