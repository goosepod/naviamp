package app.naviamp.android

import app.naviamp.domain.network.SharedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class AndroidSharedHttpClient(
    private val callRecorder: ((AndroidSharedHttpCall) -> Unit)? = null,
) : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (!headers.containsKey("Accept")) setRequestProperty("Accept", "application/json")
            if (!headers.containsKey("User-Agent")) setRequestProperty("User-Agent", "Naviamp/0.9.0")
        }
        try {
            if (connection.responseCode !in 200..299) {
                callRecorder?.invoke(connection.toSharedHttpCall(url, startedAt, errorMessage = "HTTP ${connection.responseCode}."))
                return@withContext null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
                .also {
                    callRecorder?.invoke(connection.toSharedHttpCall(url, startedAt, errorMessage = null))
                }
        } catch (exception: Exception) {
            callRecorder?.invoke(
                AndroidSharedHttpCall(
                    url = url,
                    startedAtEpochMillis = startedAt,
                    durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
                    statusCode = null,
                    errorMessage = exception.message ?: exception::class.simpleName,
                )
            )
            throw exception
        } finally {
            connection.disconnect()
        }
    }
}

data class AndroidSharedHttpCall(
    val url: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val statusCode: Int?,
    val errorMessage: String?,
) {
    val success: Boolean
        get() = statusCode != null && statusCode in 200..299
}

data class AndroidPopularTracksApiCall(
    val endpoint: String,
    val sanitizedUrl: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
)

object AndroidPopularTracksApiCallHistory {
    private const val MaxCalls = 150
    private val lock = Any()
    private val calls = ArrayDeque<AndroidPopularTracksApiCall>()

    fun record(call: AndroidPopularTracksApiCall) {
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    fun recent(limit: Int = 50): List<AndroidPopularTracksApiCall> =
        synchronized(lock) {
            calls.takeLast(limit.coerceAtLeast(0)).asReversed()
        }
}

class AndroidPopularTracksHttpClient : SharedHttpClient by AndroidSharedHttpClient(
    callRecorder = { call -> AndroidPopularTracksApiCallHistory.record(call.toPopularTracksCall()) },
)

private fun HttpURLConnection.toSharedHttpCall(
    url: String,
    startedAt: Long,
    errorMessage: String?,
): AndroidSharedHttpCall =
    AndroidSharedHttpCall(
        url = url,
        startedAtEpochMillis = startedAt,
        durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
        statusCode = responseCode,
        errorMessage = errorMessage,
    )

private fun AndroidSharedHttpCall.toPopularTracksCall(): AndroidPopularTracksApiCall =
    AndroidPopularTracksApiCall(
        endpoint = url.deezerEndpointLabel(),
        sanitizedUrl = url.sanitizedDeezerUrl(),
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )

private fun String.deezerEndpointLabel(): String {
    val path = runCatching { URI.create(this).path }.getOrNull().orEmpty().trim('/')
    return when {
        path == "search/artist" -> "search/artist"
        path.startsWith("artist/") && path.endsWith("/top") -> "artist/top"
        path.startsWith("artist/") && path.endsWith("/related") -> "artist/related"
        else -> path.ifBlank { "unknown" }
    }
}

private fun String.sanitizedDeezerUrl(): String =
    replace(Regex("""([?&]q=)[^&]+"""), "$1***")
