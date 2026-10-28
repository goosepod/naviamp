package app.naviamp.domain.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synchronous native reads must not consume the shared CPU coroutine pool. */
class BassPlaybackBlockingWorkTest {
    @Test fun blockingNativeWorkLeavesCpuCoroutinesResponsive() = runBlocking {
        val runtime = object : BassPlaybackEngineRuntime {
            override fun localFilePath(url: String): String? = null
            override fun nowEpochMillis() = 0L
            override fun <T> withPreparedPlaybackLock(block: () -> T) = block()
        }
        val release = CompletableDeferred<Unit>()
        val ready = Channel<Unit>(Channel.UNLIMITED)
        val scope = CoroutineScope(SupervisorJob())
        val jobs = List(16) {
            scope.launch(runtime.workContext) {
                ready.send(Unit)
                // BASS FILEPROCS and native stream creation are synchronous blocking calls.
                runBlocking { release.await() }
            }
        }
        try {
            withTimeout(5_000) {
                repeat(jobs.size) { ready.receive() }
                assertEquals("responsive", withContext(Dispatchers.Default) { "responsive" })
            }
        } finally {
            release.complete(Unit)
            scope.cancel()
            jobs.joinAll()
            ready.close()
        }
    }
}
