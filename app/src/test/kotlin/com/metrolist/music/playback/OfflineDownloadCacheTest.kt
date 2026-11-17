package com.metrolist.music.playback

import android.app.Application
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.test.core.app.ApplicationProvider
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class OfflineDownloadCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var cache: SimpleCache
    @Before fun setup() {
        cache = SimpleCache(folder.newFolder("cache"), NoOpCacheEvictor(),
            StandaloneDatabaseProvider(ApplicationProvider.getApplicationContext<Application>()))
    }
    @After fun teardown() { cache.release() }

    private fun save(key: String, size: Int) {
        val hole = cache.startReadWrite(key, 0, size.toLong())
        val file = cache.startFile(key, 0, size.toLong())
        file.writeBytes(ByteArray(size) { 7 })
        cache.commitFile(file, size.toLong())
        cache.releaseHoleSpan(hole)
    }
    private fun spec(key: String, position: Long = 0, length: Long = -1) = DataSpec.Builder()
        .setUri("https://unavailable.invalid/audio").setKey(key).setPosition(position).setLength(length).build()
    private fun download(key: String, length: Long = 32, state: Int = Download.STATE_COMPLETED) =
        Download(DownloadRequest.Builder(key, Uri.parse("https://unavailable.invalid/audio")).build(), state, 0, 0, length, 0, 0)

    @Test fun `all completed media source files can bypass network resolution including very short files`() {
        for (id in listOf("youtube", "spotify:track", "podcast:episode", "subsonic:song", "mediaserver:song")) {
            save(id, 4)
            assertTrue(id, completedDownloadIsCached(cache, spec(id), download(id, 4)))
        }
    }
    @Test fun `a completed suffix seek uses cached bytes`() {
        save("song", 32)
        assertTrue(completedDownloadIsCached(cache, spec("song", 16, 8), download("song")))
    }
    @Test fun `partial missing and unfinished downloads do not claim offline availability`() {
        save("partial", 16)
        assertFalse(completedDownloadIsCached(cache, spec("partial"), download("partial")))
        assertFalse(completedDownloadIsCached(cache, spec("absent"), download("absent")))
        save("pending", 32)
        assertFalse(completedDownloadIsCached(cache, spec("pending"), download("pending", state = Download.STATE_DOWNLOADING)))
        assertFalse(completedDownloadIsCached(cache, spec("pending"), null))
    }
    @Test fun `persisted content length supports restored completed downloads with unknown request length`() {
        save("restored", 32)
        val mutations = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(mutations, 32)
        cache.applyContentMetadataMutations("restored", mutations)
        assertTrue(completedDownloadIsCached(cache, spec("restored"), download("restored", -1)))
    }
    @Test fun `offline source lists retain Spotify matches and exclude episodes and unfinished files`() {
        fun song(id: String, episode: Boolean = false) = Song(SongEntity(id, id, isEpisode = episode), emptyList())
        val songs = listOf(song("youtube"), song("matched"), song("spotify:track"), song("subsonic:track"),
            song("mediaserver:track"), song("podcast:episode", true), song("audiobook:abs:book:0"), song("pending"))
        val downloads = songs.associate { it.id to download(it.id, state = if (it.id == "pending") Download.STATE_DOWNLOADING else Download.STATE_COMPLETED) }
        assertEquals(listOf("youtube", "matched"), offlineMusicSongs(songs, downloads, OfflineMusicSource.YOUTUBE).map { it.id })
        assertEquals(listOf("matched", "spotify:track"), offlineMusicSongs(songs, downloads, OfflineMusicSource.SPOTIFY, setOf("matched")).map { it.id })
        assertEquals(listOf("subsonic:track"), offlineMusicSongs(songs, downloads, OfflineMusicSource.SUBSONIC).map { it.id })
        assertEquals(listOf("mediaserver:track"), offlineMusicSongs(songs, downloads, OfflineMusicSource.MEDIA_SERVER).map { it.id })
    }
}
