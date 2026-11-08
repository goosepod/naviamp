package app.naviamp.app

import app.naviamp.domain.StreamRequest
import app.naviamp.domain.playback.PlaybackRandomAccessSource
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Fetches finite authenticated ranges, with at most 2 MiB retained per active/prepared source. */
internal class NaviampProviderRandomAccessSource(
    private val provider: MediaProvider,
    private val request: StreamRequest,
    override val lengthBytes: Long,
    private val lifetime: Job,
    private val isCurrentProvider: (MediaProvider) -> Boolean,
) : PlaybackRandomAccessSource {
    private val reads = Mutex()
    private val blocks = linkedMapOf<Long, ByteArray>()
    private val lastFailure = MutableStateFlow<Throwable?>(null)
    override val failure: Throwable? get() = lastFailure.value

    override suspend fun read(offset: Long, count: Int): ByteArray = try { reads.withLock {
        lifetime.ensureActive()
        check(isCurrentProvider(provider))
        require(offset in 0..lengthBytes && count in 0..1_048_576)
        val result = ByteArray(minOf(count.toLong(), lengthBytes - offset).toInt())
        var copied = 0
        while (copied < result.size) {
            val position = offset + copied
            val start = position / BLOCK_SIZE * BLOCK_SIZE
            val block = blocks.remove(start) ?: fetch(start)
            blocks[start] = block
            while (blocks.size > 16) blocks.remove(blocks.keys.first())
            val within = (position - start).toInt()
            val length = minOf(block.size - within, result.size - copied)
            check(length > 0)
            block.copyInto(result, copied, within, within + length)
            copied += length
        }
        result
    } } catch (failure: Throwable) {
        if (failure !is CancellationException) lastFailure.value = failure
        throw failure
    }

    private suspend fun fetch(start: Long): ByteArray {
        val end = start + minOf(BLOCK_SIZE, lengthBytes - start) - 1
        val bytes = ByteArray((end - start + 1).toInt())
        var received = 0
        var accepted = false
        val job = currentCoroutineContext()[Job]!!
        val revoked = lifetime.invokeOnCompletion { job.cancel() }
        try {
            val completed = provider.streamTrackBytes(request, "bytes=$start-$end", false,
                onResponse = { response ->
                    check(response.statusCode == 206 && response.contentRange == "bytes $start-$end/$lengthBytes")
                    check(response.contentLength == null || response.contentLength == bytes.size.toLong())
                    accepted = true
                },
                writeChunk = { data, count ->
                    lifetime.ensureActive()
                    check(isCurrentProvider(provider) && accepted && count in 0..data.size && count <= bytes.size - received)
                    data.copyInto(bytes, received, 0, count)
                    received += count
                },
            )
            check(completed && accepted && received == bytes.size)
            return bytes
        } finally { revoked.dispose() }
    }

    companion object {
        private const val BLOCK_SIZE = 131_072L
        suspend fun open(provider: MediaProvider, request: StreamRequest, lifetime: Job,
            isCurrentProvider: (MediaProvider) -> Boolean): NaviampProviderRandomAccessSource? {
            var total: Long? = null
            val job = currentCoroutineContext()[Job]!!
            val revoked = lifetime.invokeOnCompletion { job.cancel() }
            try {
                val completed = provider.streamTrackBytes(request, "bytes=0-0", true, onResponse = { response ->
                    if (response.statusCode == 206 && response.contentRange?.startsWith("bytes 0-0/") == true) {
                        total = response.contentRange?.substringAfter('/')?.toLongOrNull()?.takeIf { it > 0 }
                    }
                }, writeChunk = { _, _ -> })
                return if (completed) total?.let { NaviampProviderRandomAccessSource(provider, request, it, lifetime, isCurrentProvider) } else null
            } finally { revoked.dispose() }
        }
    }
}
