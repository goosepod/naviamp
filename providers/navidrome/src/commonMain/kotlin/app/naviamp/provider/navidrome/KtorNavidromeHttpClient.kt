package app.naviamp.provider.navidrome

import app.naviamp.domain.network.NaviampUserAgent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException

class KtorNavidromeHttpClient(
    private val client: HttpClient = createDefaultNavidromeKtorClient(NavidromeTlsSettings()),
) : NavidromeHttpClient {
    private val rateLimitBackoff = NavidromeRateLimitBackoff()

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
            rateLimitBackoff.activeExceptionOrNull()?.let { throw it }
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
                rateLimitBackoff.record(statusCode, response.headers[HttpHeaders.RetryAfter])?.let { throw it }
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
            if (error is CancellationException) throw error
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
        rateLimitBackoff.activeExceptionOrNull()?.let { error ->
            recordApiCall(
                method = HttpMethod.Get.value,
                url = url,
                startedAt = startedAt,
                success = false,
                errorMessage = error.message,
            )
            return null
        }
        return runCatching {
            val response = client.request(url) {
                method = HttpMethod.Get
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                val rateLimitError = rateLimitBackoff.record(statusCode, response.headers[HttpHeaders.RetryAfter])
                recordApiCall(
                    method = HttpMethod.Get.value,
                    url = url,
                    startedAt = startedAt,
                    success = false,
                    errorMessage = rateLimitError?.message ?: "HTTP $statusCode.",
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
            if (error is CancellationException) throw error
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
        rateLimitBackoff.activeExceptionOrNull()?.let { error ->
            recordApiCall(
                method = HttpMethod.Get.value,
                url = url,
                startedAt = startedAt,
                success = false,
                errorMessage = error.message,
            )
            return false
        }
        return runCatching {
            val response = client.request(url) {
                method = HttpMethod.Get
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                val rateLimitError = rateLimitBackoff.record(statusCode, response.headers[HttpHeaders.RetryAfter])
                recordApiCall(
                    method = HttpMethod.Get.value,
                    url = url,
                    startedAt = startedAt,
                    success = false,
                    errorMessage = rateLimitError?.message ?: "HTTP $statusCode.",
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
            if (error is CancellationException) throw error
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

internal class NavidromeRateLimitBackoff(
    private val nowMillis: () -> Long = ::navidromeCurrentTimeMillis,
) {
    private var retryAtEpochMillis: Long? = null

    fun activeExceptionOrNull(): NavidromeRateLimitException? {
        val retryAt = retryAtEpochMillis ?: return null
        val now = nowMillis()
        return if (retryAt > now) {
            NavidromeRateLimitException(
                retryAtEpochMillis = retryAt,
                message = "Navidrome rate limit is active. Try again in ${remainingDescription(retryAt, now)}.",
            )
        } else {
            retryAtEpochMillis = null
            null
        }
    }

    fun record(statusCode: Int, retryAfterHeader: String?): NavidromeRateLimitException? {
        if (statusCode != TooManyRequestsStatusCode) return null
        val now = nowMillis()
        val retryAt = retryAfterHeader.retryAfterEpochMillis(now) ?: (now + DefaultRateLimitBackoffMillis)
        retryAtEpochMillis = maxOf(retryAtEpochMillis ?: 0L, retryAt)
        return NavidromeRateLimitException(
            retryAtEpochMillis = retryAtEpochMillis ?: retryAt,
            message = "Navidrome returned HTTP 429. Try again in ${remainingDescription(retryAtEpochMillis ?: retryAt, now)}.",
        )
    }

    private fun String?.retryAfterEpochMillis(now: Long): Long? {
        val value = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toLongOrNull()?.let { seconds ->
            if (seconds >= 0) return now + seconds * 1_000L
        }
        return runCatching { value.fromHttpToGmtDate().timestamp }.getOrNull()
    }

    private fun remainingDescription(retryAt: Long, now: Long): String {
        val seconds = ((retryAt - now).coerceAtLeast(0L) + 999L) / 1_000L
        return when (seconds) {
            0L -> "less than a second"
            1L -> "1 second"
            else -> "$seconds seconds"
        }
    }
}

private val DefaultNavidromeHeaders = mapOf(
    HttpHeaders.Accept to "application/json",
    HttpHeaders.UserAgent to NaviampUserAgent,
)

private const val TooManyRequestsStatusCode = 429
private const val DefaultRateLimitBackoffMillis = 60_000L
