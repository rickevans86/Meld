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
    AUDIOBOOKS("audiobooks", setOf("audiobooks"), setOf("audiobooks")),
    PODCASTS("podcasts", setOf("podcasts"), setOf("podcasts")),
    LOCAL_FILES("local_files", setOf("local_files"), setOf("local_files")),
    RADIO("radio", setOf("radio"), setOf("radio")),
}

fun Preferences.disabledFeatures(): Set<WavelengthFeature> {
    val ids = this[DisabledFeaturesKey].orEmpty()
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
        hidden += setOf("spotify_liked", "spotify_playlists")
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
