package com.metrolist.music.podcasts

import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoveSavedEpisodesTest {
    private fun episode(id: String, token: String? = null) = SongItem(id, id, emptyList(), thumbnail = "", setVideoId = token)
    @Test fun `single and selected episodes use remote token and remove each item only once`() = runBlocking {
        val removed = mutableListOf<String>()
        val requests = mutableListOf<Pair<String, String>>()
        removeSavedEpisodes(listOf(episode("a", "token-a"), episode("b"), episode("a", "token-a")),
            refreshTokens = { listOf(episode("b", "token-b")) },
            removeRemote = { id, token -> requests += id to token },
            onRemoved = { removed += it.id })
        assertEquals(listOf("a" to "token-a", "b" to "token-b"), requests)
        assertEquals(listOf("a", "b"), removed)
    }
    @Test fun `failed remote removal keeps local entry for retry`() = runBlocking {
        val removed = mutableListOf<String>()
        val result = runCatching { removeSavedEpisodes(listOf(episode("a", "token")),
            refreshTokens = { error("Unnecessary refresh") },
            removeRemote = { _, _ -> error("Network failed") }, onRemoved = { removed += it.id }) }
        assertTrue(result.isFailure)
        assertTrue(removed.isEmpty())
    }
    @Test fun `local feed episodes can be removed without a network request`() = runBlocking {
        val removed = mutableListOf<String>()
        removeSavedEpisodes(listOf(episode("podcast:local")),
            refreshTokens = { emptyList() }, removeRemote = { _, _ -> error("No remote removal needed") },
            onRemoved = { removed += it.id })
        assertEquals(listOf("podcast:local"), removed)
    }
    @Test fun `missing remote token keeps the entry rather than treating incomplete data as removal`() = runBlocking {
        val removed = mutableListOf<String>()
        assertTrue(runCatching { removeSavedEpisodes(listOf(episode("a")),
            refreshTokens = { emptyList() }, removeRemote = { _, _ -> error("No token") },
            onRemoved = { removed += it.id }) }.isFailure)
        assertTrue(removed.isEmpty())
    }

}
