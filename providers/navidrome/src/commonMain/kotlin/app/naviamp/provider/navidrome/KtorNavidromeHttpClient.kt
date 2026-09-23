package app.naviamp.provider.navidrome

import app.naviamp.domain.provider.ProviderMediaByteResponse
import app.naviamp.domain.network.isHttpDownloadComplete
import app.naviamp.domain.network.NaviampUserAgent
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
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
        getResponse(url).body

    override suspend fun get(url: String, headers: Map<String, String>): String =
        getResponse(url, headers).body

    override suspend fun getResponse(url: String, headers: Map<String, String>): NavidromeHttpResponse =
        request(url = url, method = HttpMethod.Get, headers = headers)

    override suspend fun postForm(url: String, body: String, headers: Map<String, String>): String =
        request(url, HttpMethod.Post, body, headers, ContentType.Application.FormUrlEncoded).body

    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        postJsonResponse(url, body, headers).body

    override suspend fun postJsonResponse(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): NavidromeHttpResponse = request(url = url, method = HttpMethod.Post, body = body, headers = headers)

    override suspend fun putJson(url: String, body: String, headers: Map<String, String>): String =
        putJsonResponse(url, body, headers).body

    override suspend fun putJsonResponse(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): NavidromeHttpResponse = request(url = url, method = HttpMethod.Put, body = body, headers = headers)

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray? =
        requestBytes(url = url, headers = mapOf(HttpHeaders.Accept to "*/*") + headers)

    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean =
        downloadBytes(url = url, headers = mapOf(HttpHeaders.Accept to "*/*") + headers, writeChunk = writeChunk)

    override suspend fun stream(
        url: String,
        headers: Map<String, String>,
        headOnly: Boolean,
        onResponse: suspend (ProviderMediaByteResponse) -> Unit,
        writeChunk: suspend (bytes: ByteArray, count: Int) -> Unit,
    ): Boolean = downloadBytes(
        url = url,
        headers = mapOf(HttpHeaders.Accept to "*/*", HttpHeaders.AcceptEncoding to "identity") + headers,
        writeChunk = writeChunk,
        onResponse = onResponse,
        headOnly = headOnly,
    )

    private suspend fun request(
        url: String,
        method: HttpMethod,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
        bodyContentType: ContentType = ContentType.Application.Json,
    ): NavidromeHttpResponse {
        val startedAt = navidromeCurrentTimeMillis()
        return runCatching {
            rateLimitBackoff.activeExceptionOrNull()?.let { throw it }
            val response = client.request(url) {
                this.method = method
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
                if (body != null) {
                    contentType(bodyContentType)
                    setBody(body)
                }
            }
            val statusCode = response.status.value
            if (!response.status.isSuccess()) {
                rateLimitBackoff.record(statusCode, response.headers[HttpHeaders.RetryAfter])?.let { throw it }
                throw NavidromeHttpException(statusCode)
            }
            NavidromeHttpResponse(
                body = response.body<String>(),
                headers = response.headers.entries().associate { (name, values) -> name to values.joinToString(",") },
            ).also {
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
        onResponse: (suspend (ProviderMediaByteResponse) -> Unit)? = null,
        headOnly: Boolean = false,
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
            // Keep audio on the live response channel instead of allowing Ktor to save the full
            // response in memory before the cache writer receives its first chunk.
            client.prepareGet(url) {
                headers {
                    (DefaultNavidromeHeaders + headers).forEach { (name, value) -> append(name, value) }
                }
            }.execute { response ->
                val statusCode = response.status.value
                if (!response.status.isSuccess()) {
                    if (statusCode == 416 && onResponse != null) {
                        onResponse(ProviderMediaByteResponse(
                            statusCode = 416,
                            contentType = null,
                            contentLength = 0,
                            contentRange = response.headers[HttpHeaders.ContentRange],
                        ))
                        return@execute true
                    }
                    val rateLimitError = rateLimitBackoff.record(statusCode, response.headers[HttpHeaders.RetryAfter])
                    recordApiCall(
                        method = HttpMethod.Get.value,
                        url = url,
                        startedAt = startedAt,
                        success = false,
                        errorMessage = rateLimitError?.message ?: "HTTP $statusCode.",
                    )
                    return@execute false
                }
                if (onResponse != null && response.headers[HttpHeaders.ContentEncoding]
                        ?.equals("identity", ignoreCase = true) == false) return@execute false

                val channel = response.bodyAsChannel()
                val prefix = ByteArray(4096)
                var prefixSize = 0
                while (prefixSize < prefix.size && !channel.isClosedForRead) {
                    val read = channel.readAvailable(prefix, prefixSize, prefix.size - prefixSize)
                    if (read == -1) break
                    prefixSize += read
                }
                validateSubsonicMediaResponse(response.headers[HttpHeaders.ContentType], prefix.copyOf(prefixSize))
                onResponse?.invoke(ProviderMediaByteResponse(
                    statusCode = statusCode,
                    contentType = response.headers[HttpHeaders.ContentType],
                    contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull(),
                    contentRange = response.headers[HttpHeaders.ContentRange],
                ))
                if (headOnly) return@execute true
                if (prefixSize > 0) writeChunk(prefix, prefixSize)
                val buffer = ByteArray(64 * 1024)
                var receivedBytes = prefixSize.toLong()
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        writeChunk(buffer, read)
                        receivedBytes += read
                    }
                }
                channel.closedCause?.let { throw it }
                val complete = isHttpDownloadComplete(response.headers[HttpHeaders.ContentLength],
                    response.headers[HttpHeaders.ContentEncoding], receivedBytes)
                recordApiCall(
                    method = HttpMethod.Get.value, url = url, startedAt = startedAt,
                    success = complete, errorMessage = if (complete) null else "HTTP $statusCode.",
                )
                complete
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
