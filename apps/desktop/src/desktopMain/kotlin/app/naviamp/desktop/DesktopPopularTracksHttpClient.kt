package app.naviamp.desktop

import app.naviamp.domain.popular.PopularTracksHttpClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DesktopPopularTracksHttpClient(
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
) : PopularTracksHttpClient {
    override suspend fun get(url: String): String? {
        val startedAt = System.currentTimeMillis()
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", "Naviamp/0.9.0 (https://github.com/jbmcmichael/Naviamp)")
            .GET()
            .build()
        val responseResult = runCatching {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val response = responseResult.getOrNull()
        val errorMessage = responseResult.exceptionOrNull()?.message
            ?: response?.statusCode()
                ?.takeUnless { it in 200..299 }
                ?.let { "Deezer returned HTTP $it." }
        DesktopPopularTracksApiCallHistory.record(
            DesktopPopularTracksApiCall(
                endpoint = url.deezerEndpointLabel(),
                sanitizedUrl = url.sanitizedDeezerUrl(),
                startedAtEpochMillis = startedAt,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
                success = response != null && response.statusCode() in 200..299,
                errorMessage = errorMessage,
            ),
        )
        if (response == null || response.statusCode() !in 200..299) return null
        return response.body()
    }
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

private fun String.deezerEndpointLabel(): String {
    val path = runCatching { URI.create(this).path }.getOrNull().orEmpty().trim('/')
    return when {
        path == "search/artist" -> "search/artist"
        path.startsWith("artist/") && path.endsWith("/top") -> "artist/top"
        else -> path.ifBlank { "unknown" }
    }
}

private fun String.sanitizedDeezerUrl(): String =
    replace(Regex("""([?&]q=)[^&]+"""), "$1***")
