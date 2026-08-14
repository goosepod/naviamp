package app.naviamp.provider.jellyfin

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
        while (!channel.isClosedForRead) {
            val count = channel.readAvailable(buffer, 0, buffer.size)
            if (count == -1) break
            if (count > 0) writeChunk(buffer, count)
        }
        true
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
