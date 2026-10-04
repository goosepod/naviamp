package app.naviamp.desktop.cast

import app.naviamp.app.NaviampCastHttpRequest
import app.naviamp.app.NaviampCastHttpResponse
import app.naviamp.app.NaviampCastHttpServerEffect
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** JDK HTTP socket boundary. Core supplies the routed address and handles access/ranges/provider bytes. */
class DesktopNaviampCastHttpServerEffect : NaviampCastHttpServerEffect, AutoCloseable {
    private var server: HttpServer? = null
    private var executor: ExecutorService? = null
    private var closed = false

    override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String =
        error("A routed Cast local address is required.")

    override suspend fun start(
        localAddress: String?,
        handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        check(server == null)
        val address = InetAddress.getByName(checkNotNull(localAddress))
        val bound = HttpServer.create(InetSocketAddress(address, 0), 32)
        val workers = Executors.newFixedThreadPool(8) { task ->
            Thread(task, "naviamp-cast-media").apply { isDaemon = true }
        }
        try {
            bound.executor = workers
            bound.createContext("/") { exchange ->
                val response = ExchangeResponse(exchange)
                try {
                    runBlocking {
                        handler(NaviampCastHttpRequest(exchange.requestMethod,
                            exchange.requestURI.toASCIIString(), exchange.requestHeaders["Range"]?.joinToString(",")), response)
                    }
                } catch (_: Exception) {
                    if (!response.started) runCatching { exchange.sendResponseHeaders(500, -1) }
                } finally { exchange.close() }
            }
            bound.start()
            install(bound, workers)
            val host = if (address.hostAddress.contains(':')) "[${address.hostAddress}]" else address.hostAddress
            "http://$host:${bound.address.port}"
        } catch (failure: Exception) {
            bound.stop(0); workers.shutdownNow(); throw failure
        }
    }

    override suspend fun stop() = withContext(Dispatchers.IO) {
        release()
    }

    @Synchronized private fun install(bound: HttpServer, workers: ExecutorService) {
        check(!closed && server == null)
        server = bound
        executor = workers
    }

    @Synchronized private fun release() {
        server?.stop(0); server = null
        executor?.shutdownNow(); executor = null
    }

    @Synchronized override fun close() { closed = true; release() }

    private class ExchangeResponse(private val exchange: HttpExchange) : NaviampCastHttpResponse {
        var started = false
        override suspend fun begin(statusCode: Int, headers: Map<String, String>) {
            check(!started)
            headers.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
            val length = headers["Content-Length"]?.toLongOrNull()
            val head = exchange.requestMethod == "HEAD"
            exchange.sendResponseHeaders(statusCode,
                if (head || statusCode !in 200..299 || length == 0L) -1 else length ?: 0)
            started = true
        }
        override suspend fun write(bytes: ByteArray, count: Int) {
            check(started && count in 0..bytes.size && exchange.requestMethod != "HEAD")
            exchange.responseBody.write(bytes, 0, count)
            exchange.responseBody.flush()
        }
    }
}
