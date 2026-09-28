package app.naviamp.provider.jellyfin

import app.naviamp.domain.provider.ProviderMediaByteResponse
import app.naviamp.domain.network.isHttpDownloadComplete
import io.ktor.http.HttpHeaders
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable

class KtorJellyfinHttpClient(
    private val client: HttpClient,
) : JellyfinHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): JellyfinHttpResponse =
        request(url, HttpMethod.Get, headers = headers)

    override suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): JellyfinHttpResponse = request(url, HttpMethod.Post, body, headers)

    override suspend fun post(url: String, headers: Map<String, String>): JellyfinHttpResponse =
        request(url, HttpMethod.Post, headers = headers)

    override suspend fun delete(url: String, headers: Map<String, String>): JellyfinHttpResponse =
        request(url, HttpMethod.Delete, headers = headers)

    override suspend fun getBytes(url: String, headers: Map<String, String>): JellyfinBinaryResponse {
        val response = client.request(url) {
            method = HttpMethod.Get
            headers { headers.forEach { (name, value) -> append(name, value) } }
        }
        return JellyfinBinaryResponse(response.status.value, response.body<ByteArray>())
    }

    // The scoped request keeps large audio responses on the live channel instead of in memory.
    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = client.prepareGet(url) {
        headers { headers.forEach { (name, value) -> append(name, value) } }
    }.execute { response ->
        if (response.status.value !in 200..299) return@execute false
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var receivedBytes = 0L
        while (!channel.isClosedForRead) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count == -1) break
            if (count > 0) {
                writeChunk(buffer, count)
                receivedBytes += count
            }
        }
        channel.closedCause?.let { throw it }
        isHttpDownloadComplete(response.headers[HttpHeaders.ContentLength],
            response.headers[HttpHeaders.ContentEncoding], receivedBytes)
    }

    override suspend fun stream(
        url: String,
        headers: Map<String, String>,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = client.prepareGet(url) {
        headers { (headers + (HttpHeaders.AcceptEncoding to "identity")).forEach { (name, value) -> append(name, value) } }
    }.execute { response ->
        if (response.status.value == 416) {
            onResponse(ProviderMediaByteResponse(416, null, 0, response.headers[HttpHeaders.ContentRange]))
            return@execute true
        }
        if (response.status.value !in 200..299) return@execute false
        if (response.headers[HttpHeaders.ContentEncoding]?.equals("identity", ignoreCase = true) == false)
            return@execute false
        onResponse(ProviderMediaByteResponse(
            statusCode = response.status.value,
            contentType = response.headers[HttpHeaders.ContentType],
            contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
            contentRange = response.headers[HttpHeaders.ContentRange],
        ))
        if (headOnly) return@execute true
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(64 * 1024)
        var receivedBytes = 0L
        while (!channel.isClosedForRead) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count == -1) break
            if (count > 0) {
                writeChunk(buffer, count)
                receivedBytes += count
            }
        }
        channel.closedCause?.let { throw it }
        isHttpDownloadComplete(response.headers[HttpHeaders.ContentLength],
            response.headers[HttpHeaders.ContentEncoding], receivedBytes)
    }

    private suspend fun request(
        url: String,
        method: HttpMethod,
        body: String? = null,
        headers: Map<String, String>,
    ): JellyfinHttpResponse {
        val response = client.request(url) {
            this.method = method
            headers { headers.forEach { (name, value) -> append(name, value) } }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return JellyfinHttpResponse(
            statusCode = response.status.value,
            body = response.bodyAsText(),
        )
    }
}
