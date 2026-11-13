package com.metrolist.music.audiobooks

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudiobookPlaybackTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `embedded chapters map to file offsets without mixing books`() {
        val chapters = listOf(
            com.metrolist.music.audiobooks.abs.AbsChapter("Two", 75.5, 120.0),
            com.metrolist.music.audiobooks.abs.AbsChapter("Invalid", -1.0, 0.0),
            com.metrolist.music.audiobooks.abs.AbsChapter("One", 0.0, 75.5),
            com.metrolist.music.audiobooks.abs.AbsChapter("Past end", 120.0, 130.0),
        )
        val tracks = mapOf(
            "first" to com.metrolist.music.audiobooks.abs.AbsTrackInfo("book", 0.0, 120.0, false, chapters),
            "second" to com.metrolist.music.audiobooks.abs.AbsTrackInfo("book", 60.0, 120.0, true),
            "other" to com.metrolist.music.audiobooks.abs.AbsTrackInfo("other", 70.0, 120.0, true),
        )
        assertEquals(
            listOf(AudiobookChapter("first", "One", 0), AudiobookChapter("second", "Two", 15_500)),
            com.metrolist.music.audiobooks.abs.absChapterNavigation("second", tracks),
        )
        assertEquals(emptyList<AudiobookChapter>(), com.metrolist.music.audiobooks.abs.absChapterNavigation(null, tracks))
    }

    @Test fun `rewind and forward clamp to track boundaries`() {
        assertEquals(0L, audiobookSeekPosition(5_000, -15, 60_000))
        assertEquals(60_000L, audiobookSeekPosition(50_000, 30, 60_000))
        assertEquals(45_000L, audiobookSeekPosition(15_000, 30, -1))
        assertEquals(15_000L, audiobookSeekPosition(30_000, -15, 60_000))
    }

    @Test fun `invalid saved skip times fall back independently`() {
        assertEquals(15, AudiobookPlaybackSettings.seconds(-1, AudiobookPlaybackSettings.DEFAULT_BACK))
        assertEquals(30, AudiobookPlaybackSettings.seconds(0, AudiobookPlaybackSettings.DEFAULT_FORWARD))
        assertEquals(120, AudiobookPlaybackSettings.seconds(120, AudiobookPlaybackSettings.DEFAULT_FORWARD))
    }

    @Test fun `bookmarks survive a new repository and removal preserves other chapters`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(temporaryFolder.root, "bookmarks.preferences_pb") }
            val first = AudiobookBookmark(id = "first", mediaId = "audiobook:chapter-one", chapter = "One", positionMs = 15_000, createdAt = 1)
            val second = AudiobookBookmark(id = "second", mediaId = "audiobook:chapter-two", chapter = "Two", positionMs = 30_000, createdAt = 2)
            val repository = AudiobookBookmarks(store)
            repository.add(first)
            repository.add(second)
            val reopened = AudiobookBookmarks(store)
            assertEquals(listOf(second, first), reopened.bookmarks.first())
            reopened.remove(first.id)
            assertEquals(listOf(second), repository.bookmarks.first())
        } finally {
            scope.cancel()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bookmark rejects non audiobook media`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { File(temporaryFolder.root, "invalid.preferences_pb") }
            AudiobookBookmarks(store).add(AudiobookBookmark(mediaId = "music-id", chapter = "", positionMs = 0))
        } finally {
            scope.cancel()
        }
    }
}
