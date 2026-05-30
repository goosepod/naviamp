package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable

class KtorNavidromeHttpClient(
    private val client: HttpClient = createDefaultNavidromeKtorClient(NavidromeTlsSettings()),
) : NavidromeHttpClient {
    override suspend fun get(url: String): String =
        request(url = url, method = HttpMethod.Get)

    override suspend fun get(url: String, headers: Map<String, String>): String =
        request(url = url, method = HttpMethod.Get, headers = headers)

    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        request(url = url, method = HttpMethod.Post, body = body, headers = headers)

    override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String =
        request(url = url, method = HttpMethod.Put, body = body, headers = headers)

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? =
        requestBytes(url = url, headers = mapOf(HttpHeaders.Accept to "*/*") + headers)

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean =
        downloadBytes(url = url, headers = mapOf(HttpHeaders.Accept to "*/*") + headers, writeChunk = writeChunk)

    private suspend fun request(
        url: String,
        method: HttpMethod,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val startedAt = navidromeCurrentTimeMillis()
        return runCatching {
            val response = client.request(url) {
                this.method = method
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                throw NavidromeException("Navidrome returned HTTP $statusCode.")
            }
            response.body<String>().also {
                recordApiCall(
                    method = method.value,
                    url = url,
                    startedAt = startedAt,
                    success = true,
                    errorMessage = null,
                )
            }
        }.getOrElse { error ->
            recordApiCall(
                method = method.value,
                url = url,
                startedAt = startedAt,
                success = false,
                errorMessage = error.message ?: error::class.simpleName,
            )
            throw error
        }
    }

    private fun recordApiCall(
        method: String,
        url: String,
        startedAt: Long,
        success: Boolean,
        errorMessage: String?,
    ) {
        recordNavidromeApiCall(
            url = url,
            method = method,
            startedAt = startedAt,
            durationMillis = (navidromeCurrentTimeMillis() - startedAt).coerceAtLeast(0),
            success = success,
            errorMessage = errorMessage,
        )
    }

    private suspend fun requestBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        val startedAt = navidromeCurrentTimeMillis()
        return runCatching {
            val response = client.request(url) {
                method = HttpMethod.Get
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                recordApiCall(
                    method = HttpMethod.Get.value,
                    url = url,
                    startedAt = startedAt,
                    success = false,
                    errorMessage = "HTTP $statusCode.",
                )
                return null
            }
            response.body<ByteArray>().also {
                recordApiCall(
                    method = HttpMethod.Get.value,
                    url = url,
                    startedAt = startedAt,
                    success = true,
                    errorMessage = null,
                )
            }
        }.getOrElse { error ->
            recordApiCall(
                method = HttpMethod.Get.value,
                url = url,
                startedAt = startedAt,
                success = false,
                errorMessage = error.message ?: error::class.simpleName,
            )
            throw error
        }
    }

    private suspend fun downloadBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        val startedAt = navidromeCurrentTimeMillis()
        return runCatching {
            val response = client.request(url) {
                method = HttpMethod.Get
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                recordApiCall(
                    method = HttpMethod.Get.value,
                    url = url,
                    startedAt = startedAt,
                    success = false,
                    errorMessage = "HTTP $statusCode.",
                )
                return false
            }

            val channel = response.bodyAsChannel()
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read == -1) break
                if (read > 0) writeChunk(buffer, read)
            }
            recordApiCall(
                method = HttpMethod.Get.value,
                url = url,
                startedAt = startedAt,
                success = true,
                errorMessage = null,
            )
            true
        }.getOrElse { error ->
            recordApiCall(
                method = HttpMethod.Get.value,
                url = url,
                startedAt = startedAt,
                success = false,
                errorMessage = error.message ?: error::class.simpleName,
            )
            throw error
        }
    }
}

private val DefaultNavidromeHeaders = mapOf(
    HttpHeaders.Accept to "application/json",
    HttpHeaders.UserAgent to "Naviamp/0.9.0",
)
