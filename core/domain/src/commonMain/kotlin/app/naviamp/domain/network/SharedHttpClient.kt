package app.naviamp.domain.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable

interface SharedHttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String?

    suspend fun getBytes(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray?

    suspend fun getResponse(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): SharedHttpResponse?

    suspend fun download(
        url: String,
        headers: Map<String, String> = emptyMap(),
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean
}

data class SharedHttpResponse(
    val url: String,
    val finalUrl: String,
    val statusCode: Int,
    val contentType: String?,
    val body: ByteArray,
) {
    fun bodyText(): String = body.decodeToString()
}

data class SharedHttpCall(
    val url: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val statusCode: Int?,
    val errorMessage: String?,
) {
    val success: Boolean
        get() = statusCode != null && statusCode in 200..299
}

class KtorSharedHttpClient(
    private val callRecorder: ((SharedHttpCall) -> Unit)? = null,
    private val defaultHeaders: Map<String, String> = DefaultSharedHttpHeaders,
) : SharedHttpClient {
    private val client = HttpClient {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }

    override suspend fun get(url: String, headers: Map<String, String>): String? =
        getResponse(url, headers)?.bodyText()

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? =
        getResponse(url, mapOf(HttpHeaders.Accept to "*/*") + headers)?.body

    override suspend fun getResponse(url: String, headers: Map<String, String>): SharedHttpResponse? {
        val startedAt = currentTimeMillis()
        return runCatching {
            val response = client.get(url) {
                headers {
                    (defaultHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                callRecorder?.invoke(
                    sharedHttpCall(
                        url = url,
                        startedAt = startedAt,
                        statusCode = statusCode,
                        errorMessage = "HTTP $statusCode.",
                    ),
                )
                return null
            }
            val body = response.body<ByteArray>()
            SharedHttpResponse(
                url = url,
                finalUrl = response.call.request.url.toString(),
                statusCode = statusCode,
                contentType = response.headers[HttpHeaders.ContentType],
                body = body,
            ).also {
                callRecorder?.invoke(
                    sharedHttpCall(
                        url = url,
                        startedAt = startedAt,
                        statusCode = statusCode,
                        errorMessage = null,
                    ),
                )
            }
        }.getOrElse { error ->
            callRecorder?.invoke(
                sharedHttpCall(
                    url = url,
                    startedAt = startedAt,
                    statusCode = null,
                    errorMessage = error.message ?: error::class.simpleName,
                ),
            )
            throw error
        }
    }

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean {
        val startedAt = currentTimeMillis()
        return runCatching {
            val response = client.get(url) {
                headers {
                    (defaultHeaders + mapOf(HttpHeaders.Accept to "*/*") + headers).forEach { (name, value) ->
                        append(name, value)
                    }
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                callRecorder?.invoke(
                    sharedHttpCall(
                        url = url,
                        startedAt = startedAt,
                        statusCode = statusCode,
                        errorMessage = "HTTP $statusCode.",
                    ),
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
            callRecorder?.invoke(
                sharedHttpCall(
                    url = url,
                    startedAt = startedAt,
                    statusCode = statusCode,
                    errorMessage = null,
                ),
            )
            true
        }.getOrElse { error ->
            callRecorder?.invoke(
                sharedHttpCall(
                    url = url,
                    startedAt = startedAt,
                    statusCode = null,
                    errorMessage = error.message ?: error::class.simpleName,
                ),
            )
            throw error
        }
    }

    private fun sharedHttpCall(
        url: String,
        startedAt: Long,
        statusCode: Int?,
        errorMessage: String?,
    ): SharedHttpCall =
        SharedHttpCall(
            url = url,
            startedAtEpochMillis = startedAt,
            durationMillis = (currentTimeMillis() - startedAt).coerceAtLeast(0),
            statusCode = statusCode,
            errorMessage = errorMessage,
        )
}

expect fun String.urlEncodedParameter(): String

internal expect fun currentTimeMillis(): Long

private val DefaultSharedHttpHeaders = mapOf(
    HttpHeaders.Accept to "application/json",
    HttpHeaders.UserAgent to "Naviamp/0.9.0",
)
