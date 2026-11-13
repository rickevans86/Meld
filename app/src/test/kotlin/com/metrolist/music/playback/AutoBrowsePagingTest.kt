/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.playback

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBrowsePagingTest {
    /** A fake server list paged like Audiobookshelf (page, limit), counting requests. */
    private class FakeServer(private val items: List<String>) {
        var requests = 0
        val pager: AutoRemotePage<String> = { page, limit ->
            requests++
            val from = (page.toLong() * limit).coerceAtMost(items.size.toLong()).toInt()
            items.subList(from, (from + limit).coerceAtMost(items.size)) to items.size
        }
    }

    private fun walk(next: (Int) -> AutoPage<String>?): List<AutoPage<String>> {
        val pages = mutableListOf<AutoPage<String>>()
        do { pages += next(pages.size)!! } while (pages.last().hasMore && pages.size < 100)
        return pages
    }

    private fun assertVisible(pages: List<AutoPage<String>>) = pages.forEach { page ->
        val children = page.items.size + if (page.hasMore) 1 else 0
        assertTrue("page shows $children children", children < 100)
    }

    @Test fun `in-memory lists page under the Android Auto cap and cover every item once`() {
        val items = (1..250).map { "item$it" }
        val pages = walk { autoPage(items, it) }
        assertEquals(listOf(98, 98, 54), pages.map { it.items.size })
        assertEquals(listOf(true, true, false), pages.map { it.hasMore })
        assertEquals(items, pages.flatMap { it.items })
        assertVisible(pages)
        assertEquals(AutoPage(emptyList<String>(), false), autoPage(items, 10))
    }

    @Test fun `device books come first then server books across misaligned server pages`() {
        val device = (1..30).map { "device$it" }
        val server = FakeServer((1..300).map { "abs$it" })
        val pages = walk { page -> runBlocking { autoCombinedPage(page, device, localFirst = true, remote = server.pager) } }
        assertEquals(device + (1..300).map { "abs$it" }, pages.flatMap { it.items })
        assertVisible(pages)
        assertTrue("at most two server requests per page", server.requests <= pages.size * 2)
    }

    @Test fun `server playlists come first then app playlists`() {
        val app = (1..120).map { "app$it" }
        val server = FakeServer((1..250).map { "abs$it" })
        val pages = walk { page -> runBlocking { autoCombinedPage(page, app, localFirst = false, remote = server.pager) } }
        assertEquals((1..250).map { "abs$it" } + app, pages.flatMap { it.items })
        assertVisible(pages)
        assertEquals(pages.size, server.requests)
    }

    @Test fun `a page that exactly fills with device books still learns whether server books follow`() = runBlocking {
        val device = (1..AUTO_PAGE_SIZE).map { "device$it" }
        assertFalse(autoCombinedPage(0, device, true, FakeServer(emptyList()).pager)!!.hasMore)
        val withServer = autoCombinedPage(0, device, true, FakeServer(listOf("abs1", "abs2")).pager)!!
        assertTrue(withServer.hasMore)
        assertEquals(device, withServer.items)
        assertEquals(listOf("abs1", "abs2"), autoCombinedPage(1, device, true, FakeServer(listOf("abs1", "abs2")).pager)!!.items)
    }

    @Test fun `server failure is reported so callers can show offline content`() = runBlocking {
        val failing: AutoRemotePage<String> = { _, _ -> null }
        assertNull(autoCombinedPage(0, listOf("device"), true, failing))
        assertNull(autoCombinedPage(0, listOf("app"), false, failing))
        // A page made only of device books needs no server request.
        assertEquals(AutoPage((1..AUTO_PAGE_SIZE).map { "d$it" }, true),
            autoCombinedPage(0, (1..200).map { "d$it" }, true, failing))
    }

    @Test fun `page numbers from browse IDs are validated`() {
        assertEquals(0, autoPageNumber("0"))
        assertEquals(3, autoPageNumber("3"))
        assertNull(autoPageNumber(null))
        assertNull(autoPageNumber("-1"))
        assertNull(autoPageNumber("next"))
        assertNull(autoPageNumber(Int.MAX_VALUE.toString()))
    }
}
