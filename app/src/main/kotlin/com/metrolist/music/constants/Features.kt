/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.constants

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Wavelength features that can be switched off in Settings → Features. Stored as the set of ids turned off. */
val DisabledFeaturesKey = stringSetPreferencesKey("disabledFeatures")

/**
 * A switchable feature: the app screens it hides from the bottom bar and side menu, and the
 * Android Auto section ids (see AndroidAutoSection) it hides.
 */
enum class WavelengthFeature(val id: String, val routes: Set<String>, val autoSections: Set<String>) {
    /** The music source that isn't the default home (YouTube or Spotify). */
    SECOND_SOURCE("second_source", setOf("home_alt"), emptySet()),
    NEWS("news", setOf("news", "news_sources"), setOf("news")),
    NAVIDROME("navidrome", setOf("subsonic"), setOf("subsonic")),
    /** Jellyfin / Plex music (one tab; pick the server type when signing in). */
    MEDIA_SERVER("media_server", setOf("media_server"), setOf("media_server")),
    AUDIOBOOKS("audiobooks", setOf("audiobooks"), setOf("audiobooks")),
    PODCASTS("podcasts", setOf("podcasts"), setOf("podcasts")),
    LOCAL_FILES("local_files", setOf("local_files"), setOf("local_files")),
    RADIO("radio", setOf("radio"), setOf("radio")),
}

/**
 * A new install starts with only the YouTube home and the Library: every feature is off until switched on
 * in Settings → Features. (Installs from before this change keep everything on; see [settleFeatureDefaults].)
 */
val DEFAULT_DISABLED_FEATURE_IDS: Set<String> = WavelengthFeature.entries.mapTo(HashSet()) { it.id }

/** The saved switched-off features, or the new-install default when nothing is saved yet. */
fun Preferences.disabledFeatureIds(): Set<String> = this[DisabledFeaturesKey] ?: DEFAULT_DISABLED_FEATURE_IDS

/**
 * Saves the feature switches on first start, so the default never changes under someone: an update
 * from a version without this default keeps every feature on; a new install saves the all-off default.
 */
suspend fun settleFeatureDefaults(context: android.content.Context) {
    if (context.dataStore.data.first()[DisabledFeaturesKey] != null) return
    val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    val upgraded = info != null && info.firstInstallTime != info.lastUpdateTime
    context.dataStore.edit { prefs ->
        if (prefs[DisabledFeaturesKey] == null) {
            prefs[DisabledFeaturesKey] = if (upgraded) emptySet() else DEFAULT_DISABLED_FEATURE_IDS
        }
    }
}

fun Preferences.disabledFeatures(): Set<WavelengthFeature> {
    val ids = disabledFeatureIds()
    return WavelengthFeature.entries.filterTo(HashSet()) { it.id in ids }
}

fun Set<WavelengthFeature>.hidesRoute(route: String?): Boolean = route != null && any { route in it.routes }

/**
 * Android Auto section ids to hide. Turning off the second source hides the Spotify sections when
 * YouTube is the default home (the library sections are the user's own library, so they stay).
 */
fun Preferences.hiddenAutoSections(): Set<String> {
    val disabled = disabledFeatures()
    val hidden = disabled.flatMapTo(HashSet()) { it.autoSections }
    if (WavelengthFeature.SECOND_SOURCE in disabled && defaultHomeSource() != HOME_SOURCE_SPOTIFY) {
        hidden += "spotify"
    }
    return hidden
}

@Composable
fun rememberDisabledFeatures(): Set<WavelengthFeature> {
    val context = LocalContext.current
    val flow = remember(context) { context.dataStore.data.map { it.disabledFeatures() } }
    val disabled by flow.collectAsState(initial = emptySet())
    return disabled
}

/**
 * Integrations Wavelength switches off and hides (their code stays so Meld updates merge cleanly).
 * Set one to false to bring that integration back.
 */
object RemovedIntegrations {
    const val DISCORD = true
    const val LASTFM = true
    const val LISTEN_TOGETHER = true

    /** Signs out of the removed integrations and stops them reconnecting. Safe to call on every start. */
    suspend fun clearSavedState(context: android.content.Context) {
        context.dataStore.edit { prefs ->
            if (DISCORD) {
                prefs[EnableDiscordRPCKey] = false
                prefs.remove(DiscordUsernameKey)
                prefs.remove(DiscordNameKey)
                prefs.remove(DiscordAvatarKey)
            }
            if (LASTFM) {
                prefs[EnableLastFMScrobblingKey] = false
                prefs.remove(LastFMSessionKey)
                prefs.remove(LastFMUsernameKey)
            }
            if (LISTEN_TOGETHER) {
                prefs[ListenTogetherInTopBarKey] = false
                prefs.remove(ListenTogetherSessionTokenKey)
                prefs.remove(ListenTogetherRoomCodeKey)
                prefs.remove(ListenTogetherUserIdKey)
                prefs.remove(ListenTogetherIsHostKey)
                prefs.remove(ListenTogetherSessionTimestampKey)
            }
        }
        if (DISCORD) {
            runCatching {
                com.metrolist.music.discord.DiscordTokenStore.init(context.applicationContext)
                com.metrolist.music.discord.DiscordTokenStore.clear()
            }
        }
    }
}
