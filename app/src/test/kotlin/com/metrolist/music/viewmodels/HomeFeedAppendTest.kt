package com.metrolist.music.viewmodels

import com.metrolist.innertube.pages.HomePage
import com.metrolist.music.utils.StableHomeOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedAppendTest {
    private fun section(title: String) = HomePage.Section(title, null, null, null, emptyList())
    @Test fun `pagination preserves existing rows and appends only unseen sections`() {
        val old = listOf(section("New releases"), section("Similar to DJ Monster"))
        val next = listOf(old[0], section("Next"), section("Next"))
        assertEquals(old + section("Next"), appendHomeSections(old, next))
        assertEquals(old, appendHomeSections(old, old))
    }
    @Test fun `new random rows cannot move existing rows until order reset`() {
        val order = StableHomeOrder()
        assertEquals(listOf("A", "B"), order.arrange(listOf("A", "B")) { it })
        assertEquals(listOf("A", "B", "C"), order.arrange(listOf("C", "B", "A")) { it })
        assertEquals(listOf("C", "B", "A"), StableHomeOrder().arrange(listOf("C", "B", "A")) { it })
    }
}
