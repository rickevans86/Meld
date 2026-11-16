/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.podcasts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SpeedDialItem
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

internal fun podcastPinsToMigrate(pins: List<SpeedDialItem>, showIds: Set<String>): List<SpeedDialItem> =
    pins.filter { it.type == "PLAYLIST" && it.id in showIds }

/** Persist first, delete second: interruption can retry without losing the user's favourite. */
suspend fun migratePodcastPins(store: DataStore<Preferences>, database: MusicDatabase) {
    combine(database.speedDialDao.getAll(), database.subscribedPodcasts()) { pins, shows ->
        podcastPinsToMigrate(pins, shows.mapTo(mutableSetOf()) { it.id }).map { it.id }
    }.distinctUntilChanged().collect { ids ->
        for (id in ids) {
            PodcastFavourites.setFavourite(store, PodcastFavourites.channelId(id), true)
            database.speedDialDao.delete(id)
        }
    }
}
