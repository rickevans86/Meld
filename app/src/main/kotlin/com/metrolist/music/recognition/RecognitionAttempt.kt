package com.metrolist.music.recognition

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job

/** Owns only the current recognition attempt, so cancellation does not cancel its caller's scope. */
internal class RecognitionAttempt {
    private val active = AtomicReference<Job?>()

    suspend fun <T> run(block: suspend () -> T): T = coroutineScope {
        val job = coroutineContext.job
        active.getAndSet(job)?.cancel()
        try {
            block().also { coroutineContext.ensureActive() }
        } finally {
            active.compareAndSet(job, null)
        }
    }

    fun cancel() {
        active.getAndSet(null)?.cancel()
    }
}
