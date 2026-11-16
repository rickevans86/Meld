package com.metrolist.music.podcasts

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.constants.PodcastPlaylistsKey
import com.metrolist.music.db.InternalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class PodcastPhoneFavouritesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `migration converts known show playlist pins without touching songs artists or music playlists`() {
        val show = SpeedDialItem("show", title = "Show", type = "PLAYLIST")
        val music = SpeedDialItem("music", title = "Music", type = "PLAYLIST")
        val artist = SpeedDialItem("show", title = "Artist", type = "ARTIST")
        val song = SpeedDialItem("show", title = "Song", type = "SONG")
        assertEquals(listOf(show), podcastPinsToMigrate(listOf(show, music, artist, song), setOf("show")))
        assertTrue(podcastPinsToMigrate(listOf(music), setOf("show")).isEmpty())
    }

    @Test fun `RSS and YouTube favourites share a stable top group`() {
        val youtube = PodcastFavourites.channelId("yt")
        val rss = PodcastFavourites.feedId("https://example.com/rss")
        assertEquals(listOf(rss, youtube), listOf(youtube, rss).podcastFavouritesFirst(setOf(rss)) { it })
        assertEquals(listOf(youtube, rss), listOf(youtube, rss).podcastFavouritesFirst(setOf(youtube, rss)) { it })
    }

    @Test fun `migration saves favourite before removing pin and preserves existing favourites`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val internal = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        val db = MusicDatabase(internal)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "migration.preferences_pb") }
        try {
            internal.dao.insert(PodcastEntity(id = "show", title = "Show", bookmarkedAt = LocalDateTime.now()))
            internal.speedDialDao.insert(SpeedDialItem("show", title = "Show", type = "PLAYLIST"))
            internal.speedDialDao.insert(SpeedDialItem("music", title = "Music", type = "PLAYLIST"))
            PodcastFavourites.setFavourite(store, "existing", true)
            val migration = scope.launch { migratePodcastPins(store, db) }
            withTimeout(10_000) { internal.speedDialDao.getAll().first { pins -> pins.none { it.id == "show" } } }
            assertEquals(setOf("existing", PodcastFavourites.channelId("show")), store.data.first()[PodcastFavourites.Key])
            assertEquals(listOf("music"), internal.speedDialDao.getAll().first().map { it.id })
            migration.cancelAndJoin()
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin(); db.close() }
    }

    @Test fun `home exclusions observe native episodes and marked local playlist membership without retagging music`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build()
        try {
            db.dao.insert(SongEntity("native", "Episode", isEpisode = true))
            db.dao.insert(SongEntity("video", "Ordinary YouTube video"))
            db.dao.insert(SongEntity("music", "Music"))
            db.dao.insert(PlaylistEntity(id = "podcasts", name = "Podcast videos"))
            db.dao.insert(PlaylistSongMap(playlistId = "podcasts", songId = "video", position = 0))
            assertEquals(setOf("native", "video"), db.dao.podcastHomeSongIds(listOf("podcasts")).first().toSet())
            assertEquals(listOf("native"), db.dao.podcastHomeSongIds(emptyList()).first())
            assertFalse(db.dao.song("video").first()!!.song.isEpisode)
        } finally { db.close() }
    }
}
