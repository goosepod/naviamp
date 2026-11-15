package app.naviamp.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.http.encodedPath
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeout

data class NaviampUpnpHttpRequest(val method: String, val url: String, val headers: Map<String, String> = emptyMap(), val body: String = "")
data class NaviampUpnpHttpResponse(val status: Int, val headers: Map<String, String>, val body: String) {
    fun header(name: String): String? {
        val matching = headers.filterKeys { it.equals(name, true) }.values
        upnpCheck(matching.size <= 1)
        return matching.firstOrNull()
    }
}

fun interface NaviampUpnpHttpTransport {
    suspend fun exchange(request: NaviampUpnpHttpRequest): NaviampUpnpHttpResponse
}

/** Core owns HTTP bounds, redirects and deadlines; the host supplies only the native engine. */
class NaviampUpnpKtorTransport(engine: HttpClientEngine) : NaviampUpnpHttpTransport {
    private val client = HttpClient(engine) { followRedirects = false; expectSuccess = false }
    override suspend fun exchange(request: NaviampUpnpHttpRequest): NaviampUpnpHttpResponse = withTimeout(5_000) {
        checkedUpnpUrl(request.url)
        upnpCheck(request.body.length <= 262_144)
        client.prepareRequest(request.url) {
            method = HttpMethod.parse(request.method)
            for ((name, value) in request.headers) headers.append(name, value)
            if (request.body.isNotEmpty()) setBody(request.body.encodeToByteArray())
        }.execute { response ->
            upnpCheck(response.status.value !in 300..399)
            val length = response.headers["Content-Length"]?.toLongOrNull()
            upnpCheck(length == null || length in 0..262_144)
            val buffer = ByteArray(262_145)
            var size = 0
            val channel = response.bodyAsChannel()
            while (true) {
                val count = channel.readAvailable(buffer, size, buffer.size - size)
                if (count == -1) break
                size += count
                upnpCheck(size <= 262_144)
            }
            upnpCheck(length == null || size.toLong() == length)
            val headers = response.headers.entries().associate { it.key to it.value.joinToString(",") }
            upnpCheck(headers.entries.sumOf { it.key.length + it.value.length } <= 32_768)
            NaviampUpnpHttpResponse(response.status.value, headers, buffer.decodeToString(0, size, throwOnInvalidSequence = true))
        }
    }
    /** Does not close the injected native engine; its creator owns that resource. */
    fun close() = client.close()
}

internal fun checkedUpnpUrl(value: String): Url {
    upnpCheck(value.length <= 4_096 && (value.startsWith("http://", true) || value.startsWith("https://", true)) &&
        value.none { it.code < 33 || it in "\\<>\"'" } && '#' !in value)
    val url = try { Url(value) } catch (_: Exception) { throw NaviampUpnpProtocolException() }
    upnpCheck(url.protocol.name in setOf("http", "https") && url.host.isNotBlank() && url.port in 1..65_535 && url.user == null && url.password == null)
    return url
}

/** Pin descriptions/services to the selected device origin; cross-origin endpoints need explicit future policy. */
internal fun resolveUpnpUrl(base: String, reference: String): String {
    val origin = checkedUpnpUrl(base)
    upnpCheck(reference.isNotBlank() && reference.none { it.code < 33 || it in "\\<>\"'" } && '#' !in reference)
    val builder = URLBuilder(origin)
    builder.encodedParameters.clear()
    if (reference.startsWith('?')) {
        return resolveUpnpUrl(base, origin.encodedPath + reference)
    }
    try { builder.takeFrom(reference) } catch (_: Exception) { throw NaviampUpnpProtocolException() }
    val segments = mutableListOf<String>()
    for (segment in builder.encodedPath.split('/')) when (segment) {
        "." -> Unit
        ".." -> if (segments.size > 1) segments.removeAt(segments.lastIndex)
        else -> segments += segment
    }
    if (builder.encodedPath.endsWith("/.") || builder.encodedPath.endsWith("/..")) segments += ""
    builder.encodedPath = segments.joinToString("/")
    val resolved = checkedUpnpUrl(builder.buildString())
    upnpCheck(origin.protocol == resolved.protocol && origin.host.equals(resolved.host, true) && origin.port == resolved.port)
    return resolved.toString()
}
