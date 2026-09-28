package app.naviamp.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.naviamp.app.NaviampCastHttpRequest
import app.naviamp.app.NaviampCastHttpResponse
import app.naviamp.app.NaviampCastHttpServerEffect
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Android network and socket boundary for the shared Cast media endpoint. */
class AndroidNaviampCastHttpServerEffect(context: Context) : NaviampCastHttpServerEffect {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var socket: ServerSocket? = null
    private var scope: CoroutineScope? = null
    private var acceptJob: Job? = null

    override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String {
        check(socket == null) { "Cast media server is already running." }
        val address = lanAddress() ?: error("No receiver-reachable Wi-Fi or Ethernet address is available.")
        val server = ServerSocket(0, 32, address)
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        socket = server
        scope = serverScope
        acceptJob = serverScope.launch {
            while (isActive) {
                val client = try { server.accept() } catch (_: Exception) { break }
                launch { serve(client, handler) }
            }
        }
        return "http://${address.hostAddress}:${server.localPort}"
    }

    override suspend fun stop() {
        socket?.close()
        socket = null
        acceptJob?.cancelAndJoin()
        acceptJob = null
        scope?.coroutineContext?.get(Job)?.cancelAndJoin()
        scope = null
    }

    private fun lanAddress(): Inet4Address? = connectivity.allNetworks.asSequence()
        .filter { network ->
            connectivity.getNetworkCapabilities(network)?.let { capabilities ->
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } == true
        }
        .flatMap { network -> connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().asSequence() }
        .mapNotNull { it.address as? Inet4Address }
        .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }

    private suspend fun serve(
        client: Socket,
        handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit,
    ) {
        client.use { socket ->
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
            val response = SocketResponse(BufferedOutputStream(socket.getOutputStream()))
            var completed = false
            try {
                val request = readRequest(input)
                if (request == null) response.begin(400, emptyMap())
                else {
                    response.headOnly = request.method == "HEAD"
                    handler(request, response)
                }
                completed = true
            } catch (_: Exception) {
                if (!response.started) response.begin(500, emptyMap())
            } finally {
                response.finish(completed)
            }
        }
    }

    private fun readRequest(input: BufferedInputStream): NaviampCastHttpRequest? {
        val first = readLine(input, 2_048) ?: return null
        val parts = first.split(' ')
        if (parts.size != 3 || !parts[2].startsWith("HTTP/1.")) return null
        var remaining = 16_384
        var range: String? = null
        while (true) {
            val line = readLine(input, remaining) ?: return null
            remaining -= line.length + 2
            if (line.isEmpty()) break
            if (remaining <= 0) return null
            val separator = line.indexOf(':')
            if (separator <= 0) return null
            if (line.substring(0, separator).equals("Range", ignoreCase = true)) {
                if (range != null) return null
                range = line.substring(separator + 1).trim()
            }
        }
        return NaviampCastHttpRequest(parts[0], parts[1], range)
    }

    private fun readLine(input: BufferedInputStream, limit: Int): String? {
        if (limit <= 0) return null
        val bytes = ArrayList<Byte>()
        while (bytes.size < limit) {
            val next = input.read()
            if (next == -1) return null
            if (next == '\n'.code) {
                if (bytes.lastOrNull() == '\r'.code.toByte()) bytes.removeAt(bytes.lastIndex)
                return bytes.toByteArray().toString(Charsets.US_ASCII)
            }
            bytes += next.toByte()
        }
        return null
    }

    private class SocketResponse(private val output: BufferedOutputStream) : NaviampCastHttpResponse {
        var started = false
        var headOnly = false
        private var chunked = false

        override suspend fun begin(statusCode: Int, headers: Map<String, String>) {
            check(!started) { "Cast HTTP response was already started." }
            started = true
            val reason = when (statusCode) {
                200 -> "OK"
                206 -> "Partial Content"
                400 -> "Bad Request"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                416 -> "Range Not Satisfiable"
                502 -> "Bad Gateway"
                else -> "Internal Server Error"
            }
            output.write("HTTP/1.1 $statusCode $reason\r\n".toByteArray(Charsets.US_ASCII))
            val lengthKnown = headers.keys.any { it.equals("Content-Length", ignoreCase = true) }
            chunked = !headOnly && !lengthKnown && statusCode in 200..299
            val effective = headers + mapOf("Connection" to "close") +
                (if (chunked) mapOf("Transfer-Encoding" to "chunked")
                 else if (!lengthKnown && statusCode !in 200..299) mapOf("Content-Length" to "0")
                 else emptyMap())
            effective.forEach { (name, value) ->
                require(name.all { it.isLetterOrDigit() || it == '-' } && '\r' !in value && '\n' !in value)
                output.write("$name: $value\r\n".toByteArray(Charsets.US_ASCII))
            }
            output.write("\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        override suspend fun write(bytes: ByteArray, count: Int) {
            check(started && !headOnly && count in 0..bytes.size)
            if (count == 0) return
            if (chunked) output.write("${count.toString(16)}\r\n".toByteArray(Charsets.US_ASCII))
            output.write(bytes, 0, count)
            if (chunked) output.write("\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        }

        fun finish(completed: Boolean) {
            if (started && chunked && completed) output.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.flush()
        }
    }
}
