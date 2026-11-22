package com.metrolist.music.recognition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class RecognitionAttemptTest {
    @Test fun `cancel rejects a late result without cancelling caller and allows another attempt`() = runBlocking {
        val attempt = RecognitionAttempt()
        val late = CompletableDeferred<Unit>()
        var delivered = false
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            attempt.run { withContext(NonCancellable) { late.await(); "late match" } }
            delivered = true
        }
        attempt.cancel()
        assertEquals("new match", attempt.run { "new match" })
        late.complete(Unit)
        old.join()
        assertTrue(old.isCancelled)
        assertFalse(delivered)
        assertTrue(coroutineContext[kotlinx.coroutines.Job]!!.isActive)
    }

    @Test fun `old attempt cleanup cannot detach the new attempt from cancel`() = runBlocking {
        val attempt = RecognitionAttempt()
        val late = CompletableDeferred<Unit>()
        val waiting = CompletableDeferred<Unit>()
        val old = launch(start = CoroutineStart.UNDISPATCHED) {
            attempt.run { withContext(NonCancellable) { late.await() } }
        }
        val next = launch(start = CoroutineStart.UNDISPATCHED) { attempt.run { waiting.await() } }
        late.complete(Unit)
        old.join()
        attempt.cancel()
        next.join()
        assertTrue(old.isCancelled)
        assertTrue(next.isCancelled)
        assertEquals(42, attempt.run { 42 })
    }
}
