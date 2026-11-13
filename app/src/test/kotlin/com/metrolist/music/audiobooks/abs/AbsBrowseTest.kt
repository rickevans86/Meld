/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.metrolist.music.audiobooks.abs

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbsBrowseTest {
    private val book = """{"id":"book-1","mediaType":"book","media":{"metadata":{"title":"The Coast","authors":[{"name":"Maya"}]},"duration":123.0}}"""
    @Test fun `personalized shelves keep server order and ignore non book rows`() {
        val shelves = parseAbsShelves(Json.parseToJsonElement("""[
            {"id":"continue-series","type":"book","entities":[$book,$book]},
            {"id":"recently-added","type":"book","entities":[$book]},
            {"id":"authors","type":"author","entities":[{"id":"author"}]}
        ]"""))
        assertEquals(setOf("continue-series", "recently-added"), shelves.keys)
        assertEquals(listOf(AbsBookSummary("book-1", "The Coast", "Maya", 123.0)), shelves["continue-series"])
    }
    @Test fun `series and collections parse expanded book lists`() {
        for (kind in listOf(AbsBrowseKind.SERIES, AbsBrowseKind.COLLECTIONS)) {
            val page = parseAbsGroups(Json.parseToJsonElement("""{"results":[{"id":"group-1","name":"Favorites","books":[$book]}],"total":51}"""), kind)
            assertEquals(51, page.total)
            assertEquals("book-1", page.groups.single().books.single().id)
        }
    }
    @Test fun `playlist items use embedded library books and exclude podcasts`() {
        val podcast = book.replace("book-1", "podcast-1").replace("\"mediaType\":\"book\"", "\"mediaType\":\"podcast\"")
        val page = parseAbsGroups(Json.parseToJsonElement("""{"results":[{"id":"playlist","name":"Queue","items":[{"libraryItem":$book},{"libraryItem":$podcast},{}]}]}"""), AbsBrowseKind.PLAYLISTS)
        assertEquals(listOf("book-1"), page.groups.single().books.map { it.id })
        assertTrue(parseAbsShelves(Json.parseToJsonElement("{} ")).isEmpty())
    }
}
