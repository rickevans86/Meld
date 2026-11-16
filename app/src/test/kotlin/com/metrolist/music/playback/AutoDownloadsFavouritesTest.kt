package com.metrolist.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.ui.screens.settings.deserializeSections
import com.metrolist.music.ui.screens.settings.serializeSections
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class AutoDownloadsFavouritesTest {
    private fun song(id: String, episode: Boolean = false, local: Boolean = false) =
        Song(SongEntity(id, id, isEpisode = episode, isLocal = local), emptyList())

    private fun download(id: String, state: Int = Download.STATE_COMPLETED, updated: Long = 0) =
        Download(DownloadRequest.Builder(id, Uri.parse("https://example.com/$id")).build(),
            state, 0, updated, 100, 0, if (state == Download.STATE_FAILED) Download.FAILURE_REASON_UNKNOWN else 0)

    @Test fun `downloads exclude all non YouTube sources and native YouTube episodes`() {
        val songs = listOf(song("youtubeSong"), song("ytEpisode", episode = true), song("podcast:rss"),
            song("audiobook:abs:book:0"), song("local:file"), song("spotify:song"), song("subsonic:song"),
            song("mediaserver:song"), song("radio:station"), song("localFlag", local = true))
        val downloads = songs.associate { it.id to download(it.id) }
        assertEquals(listOf("youtubeSong"), autoDownloadedYouTubeSongs(songs, downloads).map { it.id })
    }

    @Test fun `only completed existing songs are counted in download order`() {
        val songs = listOf(song("new"), song("old"), song("queued"), song("failed"), song("missing"))
        val downloads = mapOf("new" to download("new", updated = 20), "old" to download("old", updated = 10),
            "queued" to download("queued", Download.STATE_DOWNLOADING), "failed" to download("failed", Download.STATE_FAILED),
            "orphan" to download("orphan"))
        val queue = autoDownloadedYouTubeSongs(songs, downloads)
        assertEquals(listOf("old", "new"), queue.map { it.id })
        assertEquals(queue.map { it.id }.toSet(), queue.shuffled().map { it.id }.toSet())
    }

    @Test fun `old root layouts drop downloaded and retain section order and disabled flags`() {
        val sections = deserializeSections("podcasts:true,downloaded:true,youtube:false,audiobooks:true")
        assertFalse(sections.any { it.first.id == "downloaded" })
        assertEquals(listOf("podcasts", "youtube", "audiobooks"), sections.map { it.first.id }.filter { it in setOf("podcasts", "youtube", "audiobooks") })
        assertFalse(sections.first { it.first.id == "youtube" }.second)
        assertFalse(serializeSections(sections).contains("downloaded"))
        assertFalse(deserializeSections("").any { it.first.id == "downloaded" })
    }

    @Test fun `browse buttons advertise the same add remove commands as channel metadata`() {
        val base = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        // This project's JVM tests do not package app string resources.
        val context = object : android.content.ContextWrapper(base) {
            @Suppress("DEPRECATION")
            override fun getResources(): android.content.res.Resources = object : android.content.res.Resources(
                base.resources.assets, base.resources.displayMetrics, base.resources.configuration,
            ) {
                override fun getString(id: Int) = "label-$id"
            }
        }
        val buttons = AutoPodcastFavourites.buttons(context)
        assertEquals(listOf(AutoPodcastFavourites.ADD, AutoPodcastFavourites.REMOVE), buttons.map { it.sessionCommand?.customAction })
        assertTrue(buttons.all { it.iconUri?.scheme == "android.resource" })
        assertTrue(buttons.all { it.displayName.isNotBlank() })
    }

    @Test fun `favourite browse metadata switches action and preserves node identity artwork and playback flags`() {
        val node = MediaItem.Builder().setMediaId("podcasts/channel/show")
            .setMediaMetadata(MediaMetadata.Builder().setTitle("Show").setSubtitle("Publisher")
                .setIsPlayable(false).setIsBrowsable(true).setArtworkUri(Uri.parse("content://cover/show")).build()).build()
        val favourite = AutoPodcastFavourites.decorate(node, true)
        assertEquals("★ Show", favourite.mediaMetadata.title)
        assertEquals(listOf(AutoPodcastFavourites.REMOVE), favourite.mediaMetadata.supportedCommands)
        assertEquals(listOf(AutoPodcastFavourites.ADD), AutoPodcastFavourites.decorate(node, false).mediaMetadata.supportedCommands)
        assertEquals(node.mediaId, favourite.mediaId)
        assertEquals(node.mediaMetadata.artworkUri, favourite.mediaMetadata.artworkUri)
        assertEquals(false, favourite.mediaMetadata.isPlayable)
        assertEquals(true, favourite.mediaMetadata.isBrowsable)
        assertEquals("Show", node.mediaMetadata.title)
    }
}
