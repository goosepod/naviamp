package app.naviamp.app

import app.naviamp.domain.StreamRequest
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackRandomAccessSource
import app.naviamp.domain.provider.MediaProvider
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.network.sockets.port
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Local playback transport. The provider owns upstream authentication; this owner forwards bytes
 * with backpressure and never stores a complete track. Only current/prepared streams have leases.
 * Ktor's common socket API supplies the same loopback-only boundary on every host.
 */
class NaviampPlaybackStreamRelay(
    scope: CoroutineScope,
    private val isCurrentProvider: (MediaProvider) -> Boolean,
) {
    enum class Slot { Current, PreparedNext }

    private class Lease(val provider: MediaProvider, val request: StreamRequest, val token: String, val job: Job) {
        val preparation = Mutex()
        var source: PlaybackRandomAccessSource? = null
        var prepared = false
    }
    private data class State(val epoch: Long = 0, val preparedEpoch: Long = 0, val slots: Map<Slot, Lease> = emptyMap())
    private val state = MutableStateFlow(State())
    private val lifetime = SupervisorJob(scope.coroutineContext[Job])
    private val workers = CoroutineScope(scope.coroutineContext + lifetime + Dispatchers.Default)
    private val startup = Mutex()
    private val connections = Semaphore(8)
    private var listener: ServerSocket? = null
    private var selector: SelectorManager? = null

    @OptIn(ExperimentalUuidApi::class)
    suspend fun url(slot: Slot, provider: MediaProvider, request: StreamRequest): String {
        val before = state.value
        return startup.withLock {
            currentCoroutineContext().ensureActive()
            lifetime.ensureActive()
            val server = listener ?: withContext(Dispatchers.Default) {
                val manager = SelectorManager(Dispatchers.Default)
                try {
                    val bound = aSocket(manager).tcp().bind("127.0.0.1", 0)
                    selector = manager
                    listener = bound
                    workers.launch {
                        try {
                            while (true) {
                                val socket = bound.accept()
                                if (!connections.tryAcquire()) { socket.close(); continue }
                                launch {
                                    try { serve(socket, bound.port) }
                                    finally { socket.close(); connections.release() }
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            lifetime.cancel(CancellationException("Playback stream listener closed", failure))
                        } finally { bound.close(); manager.close() }
                    }.invokeOnCompletion { bound.close(); manager.close() }
                    bound
                } catch (failure: Throwable) { manager.close(); throw failure }
            }
            val snapshot = state.value
            if (snapshot.epoch != before.epoch ||
                (slot == Slot.PreparedNext && snapshot.preparedEpoch != before.preparedEpoch)) throw CancellationException()
            val lease = snapshot.slots.values.firstOrNull { it.provider === provider && it.request == request }
                ?: Lease(provider, request, Uuid.random().toString() + Uuid.random().toString(), SupervisorJob(lifetime))
            val next = snapshot.copy(slots = snapshot.slots + (slot to lease))
            if (!state.compareAndSet(snapshot, next)) {
                if (lease !in snapshot.slots.values) lease.job.cancel()
                throw CancellationException()
            }
            releaseUnused(snapshot, next)
            "http://127.0.0.1:${server.port}/playback/${lease.token}"
        }
    }

    fun clearPrepared() {
        val previous = state.getAndUpdate { it.copy(preparedEpoch = it.preparedEpoch + 1, slots = it.slots - Slot.PreparedNext) }
        releaseUnused(previous, state.value)
    }

    suspend fun randomAccessSource(url: String): PlaybackRandomAccessSource? {
        val lease = state.value.slots.values.firstOrNull { url.endsWith("/playback/${it.token}") } ?: return null
        if (lease.request.quality != StreamQuality.Original) return null
        return lease.preparation.withLock {
            lease.job.ensureActive()
            if (!lease.prepared) {
                lease.source = NaviampProviderRandomAccessSource.open(lease.provider, lease.request, lease.job, isCurrentProvider)
                lease.prepared = true
            }
            lease.source
        }
    }

    fun clearCurrent() {
        val previous = state.getAndUpdate { it.copy(slots = it.slots - Slot.Current) }
        releaseUnused(previous, state.value)
    }

    fun close() {
        val previous = state.getAndUpdate { State(epoch = it.epoch + 1) }
        previous.slots.values.forEach { it.job.cancel() }
        lifetime.cancel()
        listener?.close()
        selector?.close()
    }

    private fun releaseUnused(previous: State, next: State) {
        previous.slots.values.filter { it !in next.slots.values }.forEach { it.job.cancel() }
    }

    private suspend fun serve(socket: Socket, port: Int) {
        val output = socket.openWriteChannel(autoFlush = true)
        var started = false
        suspend fun begin(code: Int, headers: Map<String, String> = emptyMap()) {
            check(!started)
            started = true
            output.writeStringUtf8("HTTP/1.1 $code Response\r\nConnection: close\r\n")
            headers.forEach { (name, value) -> output.writeStringUtf8("$name: $value\r\n") }
            output.writeStringUtf8("\r\n")
            output.flush()
        }
        try {
            val parsed = withTimeout(5_000) {
                val input = socket.openReadChannel()
                val first = input.readUTF8Line(2_048)?.split(' ') ?: return@withTimeout null
                if (first.size != 3 || first[2] !in setOf("HTTP/1.0", "HTTP/1.1")) return@withTimeout null
                val headers = mutableMapOf<String, String>()
                var remaining = 8_192
                while (true) {
                    val line = input.readUTF8Line(minOf(remaining, 2_048)) ?: return@withTimeout null
                    remaining -= line.length + 2
                    if (remaining <= 0) return@withTimeout null
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon <= 0) return@withTimeout null
                    val key = line.substring(0, colon).lowercase()
                    if (key in headers) return@withTimeout null
                    headers[key] = line.substring(colon + 1).trim()
                }
                Triple(first[0], first[1], headers)
            }
            if (parsed == null) { begin(400); return }
            val (method, path, headers) = parsed
            if (headers["host"] != "127.0.0.1:$port") { begin(404); return }
            if (method !in setOf("GET", "HEAD")) { begin(405, mapOf("Allow" to "GET, HEAD")); return }
            if (headers["transfer-encoding"] != null || headers["content-length"]?.let { it != "0" } == true) {
                begin(400); return
            }
            val lease = state.value.slots.values.firstOrNull { path == "/playback/${it.token}" }
            if (lease == null || !isCurrentProvider(lease.provider)) { begin(404); return }
            val range = headers["range"]
            if (range != null && !validPlaybackByteRange(range)) { begin(416); return }
            // Link cancellation to both the accepted socket request and its revocable playback lease.
            val requestJob = currentCoroutineContext()[Job]!!
            val revocation = lease.job.invokeOnCompletion { requestJob.cancel() }
            try {
                val success = lease.provider.streamTrackBytes(lease.request, range, method == "HEAD",
                    onResponse = { upstream ->
                        check(isCurrentProvider(lease.provider) && lease.job.isActive)
                        check(upstream.statusCode in 200..599)
                        begin(upstream.statusCode, buildMap {
                            put("Accept-Ranges", "bytes")
                            upstream.contentType?.takeIf(::safePlaybackHeader)?.let { put("Content-Type", it) }
                            upstream.contentLength?.takeIf { it >= 0 }?.let { put("Content-Length", it.toString()) }
                            upstream.contentRange?.takeIf(::safePlaybackHeader)?.let { put("Content-Range", it) }
                        })
                    },
                    writeChunk = { bytes, count ->
                        check(started && count in 0..bytes.size && isCurrentProvider(lease.provider))
                        if (method != "HEAD") { output.writeFully(bytes, 0, count); output.flush() }
                    },
                )
                if (!success && !started) begin(502)
            } finally { revocation.dispose() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (!started) runCatching { begin(502) }
        } finally { runCatching { output.flushAndClose() } }
    }
}

internal fun safePlaybackHeader(value: String): Boolean = value.length <= 1_024 && value.all { it.code in 32..126 }

internal fun validPlaybackByteRange(value: String): Boolean {
    if (!value.startsWith("bytes=")) return false
    val parts = value.removePrefix("bytes=").split('-', limit = 2)
    if (parts.size != 2 || parts.any { part -> part.any { it !in '0'..'9' } }) return false
    if (parts[0].isEmpty()) return parts[1].toLongOrNull()?.let { it > 0 } == true
    val first = parts[0].toLongOrNull() ?: return false
    return parts[1].isEmpty() || parts[1].toLongOrNull()?.let { it >= first } == true
}
