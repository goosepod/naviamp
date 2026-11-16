package app.naviamp.app

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

/** Shared bounded HTTP framing and listener lifetime over Ktor's multiplatform socket boundary. */
class NaviampKtorMediaHttpServerEffect : NaviampCastHttpServerEffect {
    private class Listener(val socket: ServerSocket, val selector: SelectorManager) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val clients = mutableSetOf<Socket>()
        val guard = Mutex()
        var stopped = false
    }
    private val lifetime = Mutex()
    private var listener: Listener? = null

    override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String =
        error("A receiver-reachable local address is required")

    override suspend fun start(localAddress: String?, handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String = lifetime.withLock {
        check(listener == null)
        val address = checkNotNull(localAddress)
        require(address.isNotBlank() && address.none { it.isWhitespace() || it in "/\\?#[]" })
        withContext(Dispatchers.Default) {
            val selector = SelectorManager(Dispatchers.Default)
            try {
                val resource = Listener(aSocket(selector).tcp().bind(address, 0), selector)
                listener = resource
                resource.scope.launch {
                    val slots = Semaphore(8)
                    try {
                        while (isActive) {
                            val client = resource.socket.accept()
                            if (!slots.tryAcquire()) { client.close(); continue }
                            val accepted = resource.guard.withLock {
                                if (resource.stopped) false else { resource.clients += client; true }
                            }
                            if (!accepted) { slots.release(); client.close(); continue }
                            launch {
                                try { serve(client, handler) }
                                finally {
                                    client.close(); slots.release()
                                    withContext(NonCancellable) { resource.guard.withLock { resource.clients -= client } }
                                }
                            }
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* stop owns resource teardown, including failed accept. */ }
                }
                val host = if (':' in address) "[$address]" else address
                "http://$host:${resource.socket.port}"
            } catch (failure: Throwable) { selector.close(); throw failure }
        }
    }

    override suspend fun stop() = withContext(NonCancellable) { lifetime.withLock {
        val resource = listener ?: return@withLock
        listener = null
        resource.guard.withLock {
            resource.stopped = true
            resource.socket.close()
            resource.clients.forEach { it.close() }
        }
        resource.scope.coroutineContext[Job]?.cancelAndJoin()
        resource.selector.close()
    } }

    private suspend fun serve(socket: Socket, handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit) {
        val output = socket.openWriteChannel(autoFlush = true)
        val response = Response(output)
        try {
            val request = withTimeout(5_000) {
                val input = socket.openReadChannel()
                val first = input.readUTF8Line(2_048)?.split(' ') ?: return@withTimeout null
                if (first.size != 3 || first[2] !in setOf("HTTP/1.0", "HTTP/1.1")) return@withTimeout null
                var remaining = 16_384
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = input.readUTF8Line(minOf(remaining, 2_048)) ?: return@withTimeout null
                    remaining -= line.length + 2
                    if (remaining < 0) return@withTimeout null
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon <= 0) return@withTimeout null
                    val name = line.substring(0, colon).lowercase()
                    if (name in headers) return@withTimeout null
                    headers[name] = line.substring(colon + 1).trim()
                }
                if (headers["transfer-encoding"] != null || headers["content-length"]?.let { it != "0" } == true) return@withTimeout null
                NaviampCastHttpRequest(first[0], first[1], headers["range"])
            }
            if (request == null) response.begin(400, emptyMap()) else {
                response.headOnly = request.method == "HEAD"
                handler(request, response)
            }
            response.finish()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (!response.started) runCatching { response.begin(500, emptyMap()) } }
        finally { runCatching { output.flushAndClose() } }
    }

    private class Response(private val output: ByteWriteChannel) : NaviampCastHttpResponse {
        var started = false
        var headOnly = false
        private var chunked = false
        private var bodyAllowed = false
        override suspend fun begin(statusCode: Int, headers: Map<String, String>) {
            check(!started && statusCode in 100..599)
            require(headers.all { (name, value) -> name.isNotEmpty() && name.all { it.isLetterOrDigit() || it in "-._" } && value.all { it.code in 32..126 } })
            started = true
            val known = headers.keys.any { it.equals("Content-Length", true) }
            bodyAllowed = !headOnly && statusCode >= 200 && statusCode !in setOf(204, 304)
            chunked = bodyAllowed && !known && statusCode in 200..299
            output.writeStringUtf8("HTTP/1.1 $statusCode Response\r\n")
            headers.filterKeys { !it.equals("Connection", true) && !it.equals("Transfer-Encoding", true) }.forEach { (name, value) -> output.writeStringUtf8("$name: $value\r\n") }
            output.writeStringUtf8("Connection: close\r\n")
            if (chunked) output.writeStringUtf8("Transfer-Encoding: chunked\r\n")
            else if (bodyAllowed && !known) output.writeStringUtf8("Content-Length: 0\r\n")
            output.writeStringUtf8("\r\n"); output.flush()
        }
        override suspend fun write(bytes: ByteArray, count: Int) {
            check(started && bodyAllowed && count in 0..bytes.size)
            if (count == 0) return
            if (chunked) output.writeStringUtf8("${count.toString(16)}\r\n")
            output.writeFully(bytes, 0, count)
            if (chunked) output.writeStringUtf8("\r\n")
            output.flush()
        }
        suspend fun finish() { if (chunked) output.writeStringUtf8("0\r\n\r\n"); output.flush() }
    }
}
