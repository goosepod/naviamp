package app.naviamp.domain.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess

interface SharedHttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String?
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

    override suspend fun get(url: String, headers: Map<String, String>): String? {
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
            response.body<String>().also {
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
