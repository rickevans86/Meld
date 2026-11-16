package com.metrolist.music.podcasts

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PodcastFavouritesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `favourites keep selected sort order in both groups`() {
        val shows = listOf("Zulu", "Echo", "Delta", "Alpha")
        val favourites = setOf("Alpha", "Echo", "not followed")
        assertEquals(listOf("Echo", "Alpha", "Zulu", "Delta"), shows.podcastFavouritesFirst(favourites) { it })
        assertEquals(listOf("Alpha", "Echo", "Delta", "Zulu"), shows.reversed().podcastFavouritesFirst(favourites) { it })
        assertEquals(shows, shows.podcastFavouritesFirst(emptySet()) { it })
    }

    @Test fun `feed identity keeps existing Auto routes and cannot collide with channel IDs`() {
        val url = "https://example.com/podcast.xml?feed=1"
        assertEquals(16, PodcastFavourites.feedKey(url).length)
        assertEquals("podcasts/feed/7f3ba3f9d248cc62", PodcastFavourites.feedId(url))
        assertNotEquals(PodcastFavourites.feedId(url), PodcastFavourites.feedId("$url&other=2"))
        assertNotEquals(PodcastFavourites.feedId(url), PodcastFavourites.channelId(PodcastFavourites.feedKey(url)))
        assertEquals(3, PodcastFavourites.feedId(url).split('/').size)
    }

    @Test fun `favourites persist across reopening and add remove is idempotent`() = runBlocking {
        val file = File(folder.root, "favourites.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = PreferenceDataStoreFactory.create(scope = scope) { file }
        val channel = PodcastFavourites.channelId("VINwiki")
        val feed = PodcastFavourites.feedId("https://example.com/feed")
        try {
            PodcastFavourites.setFavourite(first, channel, true)
            PodcastFavourites.setFavourite(first, channel, true)
            PodcastFavourites.setFavourite(first, feed, true)
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val reopened = PreferenceDataStoreFactory.create(scope = scope) { file }
        try {
            assertEquals(setOf(channel, feed), reopened.data.first()[PodcastFavourites.Key])
            PodcastFavourites.setFavourite(reopened, channel, false)
            PodcastFavourites.setFavourite(reopened, channel, false)
            assertEquals(setOf(feed), reopened.data.first()[PodcastFavourites.Key])
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
        }
    }
}
