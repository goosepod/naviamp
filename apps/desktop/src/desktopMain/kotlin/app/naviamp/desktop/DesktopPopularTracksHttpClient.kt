package app.naviamp.desktop

import app.naviamp.domain.network.SharedHttpClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DesktopPopularTracksHttpClient(
    httpClient: HttpClient = HttpClient.newHttpClient(),
) : SharedHttpClient by DesktopSharedHttpClient(
    httpClient = httpClient,
    callRecorder = { call -> DesktopPopularTracksApiCallHistory.record(call.toPopularTracksCall()) },
)

class DesktopSharedHttpClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val callRecorder: ((DesktopSharedHttpCall) -> Unit)? = null,
) : SharedHttpClient {
    override suspend fun get(url: String, headers: Map<String, String>): String? {
        val startedAt = System.currentTimeMillis()
        val request = HttpRequest.newBuilder(URI.create(url)).apply {
            val mergedHeaders = mapOf(
                "Accept" to "application/json",
                "User-Agent" to "Naviamp/0.9.0 (https://github.com/jbmcmichael/Naviamp)",
            ) + headers
            mergedHeaders.forEach { (name, value) -> header(name, value) }
            GET()
        }.build()
        val responseResult = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val response = responseResult.getOrNull()
        val errorMessage = responseResult.exceptionOrNull()?.message
            ?: response?.statusCode()
                ?.takeUnless { it in 200..299 }
                ?.let { "HTTP $it." }
        callRecorder?.invoke(
            DesktopSharedHttpCall(
                url = url,
                startedAtEpochMillis = startedAt,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
                statusCode = response?.statusCode(),
                errorMessage = errorMessage,
            )
        )
        if (response == null || response.statusCode() !in 200..299) return null
        return response.body()
    }
}

data class DesktopSharedHttpCall(
    val url: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val statusCode: Int?,
    val errorMessage: String?,
) {
    val success: Boolean
        get() = statusCode != null && statusCode in 200..299
}

data class DesktopPopularTracksApiCall(
    val endpoint: String,
    val sanitizedUrl: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
)

object DesktopPopularTracksApiCallHistory {
    private const val MaxCalls = 150
    private val lock = Any()
    private val calls = ArrayDeque<DesktopPopularTracksApiCall>()

    fun record(call: DesktopPopularTracksApiCall) {
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    fun recent(limit: Int = 50): List<DesktopPopularTracksApiCall> =
        synchronized(lock) {
            calls.takeLast(limit.coerceAtLeast(0)).asReversed()
    }
}

private fun DesktopSharedHttpCall.toPopularTracksCall(): DesktopPopularTracksApiCall =
    DesktopPopularTracksApiCall(
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
