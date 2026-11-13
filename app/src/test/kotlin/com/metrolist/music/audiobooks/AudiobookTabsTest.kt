/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.audiobooks

import com.metrolist.music.audiobooks.abs.absSortParams
import org.junit.Assert.assertEquals
import org.junit.Test

class AudiobookTabsTest {
    @Test fun `default order is Library, Downloaded, Listening and a saved order round-trips`() {
        assertEquals(listOf(AudiobookTab.LIBRARY, AudiobookTab.DOWNLOADED, AudiobookTab.LISTENING), AudiobookTabs.decode(""))
        val custom = listOf(AudiobookTab.LISTENING, AudiobookTab.LIBRARY, AudiobookTab.DOWNLOADED)
        assertEquals(custom, AudiobookTabs.decode(AudiobookTabs.encode(custom)))
    }

    @Test fun `malformed saved orders keep every tab exactly once`() {
        assertEquals(
            listOf(AudiobookTab.DOWNLOADED, AudiobookTab.LIBRARY, AudiobookTab.LISTENING),
            AudiobookTabs.decode("downloaded, bogus,downloaded,,library"),
        )
    }

    @Test fun `open tab falls back to the first tab in the saved order`() {
        val order = AudiobookTabs.decode("listening,library,downloaded")
        assertEquals(AudiobookTab.LISTENING, AudiobookTabs.selected("", order))
        assertEquals(AudiobookTab.LISTENING, AudiobookTabs.selected("removed", order))
        assertEquals(AudiobookTab.DOWNLOADED, AudiobookTabs.selected("downloaded", order))
    }

    @Test fun `unknown sort falls back to title`() {
        assertEquals(AudiobookSort.TITLE, AudiobookTabs.sort(""))
        assertEquals(AudiobookSort.ADDED, AudiobookTabs.sort("added"))
        assertEquals(AudiobookSort.TITLE, AudiobookTabs.sort("newest"))
    }

    private data class Book(val title: String, val author: String?)
    private val books = listOf(Book("beta", "Zed"), Book("Alpha", null), Book("gamma", "amy"), Book("Delta", "Amy"))

    @Test fun `phone books sort by title ignoring case, and by author with unknown authors last`() {
        assertEquals(listOf("Alpha", "beta", "Delta", "gamma"),
            AudiobookTabs.sortLocal(books, AudiobookSort.TITLE, { it.title }, { it.author }).map { it.title })
        assertEquals(listOf("Delta", "gamma", "beta", "Alpha"),
            AudiobookTabs.sortLocal(books, AudiobookSort.AUTHOR, { it.title }, { it.author }).map { it.title })
        // No added date on the phone: Recently added keeps the title order.
        assertEquals(listOf("Alpha", "beta", "Delta", "gamma"),
            AudiobookTabs.sortLocal(books, AudiobookSort.ADDED, { it.title }, { it.author }).map { it.title })
    }

    @Test fun `Audiobookshelf sort parameters`() {
        assertEquals("media.metadata.title" to false, absSortParams(AudiobookSort.TITLE))
        assertEquals("media.metadata.authorName" to false, absSortParams(AudiobookSort.AUTHOR))
        assertEquals("addedAt" to true, absSortParams(AudiobookSort.ADDED))
    }
}
