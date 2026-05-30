package app.naviamp.desktop

import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.SharedHttpCall
import app.naviamp.domain.network.SharedHttpClient

class DesktopPopularTracksHttpClient : SharedHttpClient by KtorSharedHttpClient(
    callRecorder = { call -> DesktopPopularTracksApiCallHistory.record(call.toPopularTracksCall()) },
    defaultHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Naviamp/0.9.0 (https://github.com/jbmcmichael/Naviamp)",
    ),
)

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

private fun SharedHttpCall.toPopularTracksCall(): DesktopPopularTracksApiCall =
    DesktopPopularTracksApiCall(
        endpoint = url.deezerEndpointLabel(),
        sanitizedUrl = url.sanitizedDeezerUrl(),
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )

private fun String.deezerEndpointLabel(): String {
    val withoutQuery = substringBefore('?')
    val path = withoutQuery
        .substringAfter("://", withoutQuery)
        .substringAfter('/', "")
        .trim('/')
    return when {
        path == "search/artist" -> "search/artist"
        path.startsWith("artist/") && path.endsWith("/top") -> "artist/top"
        path.startsWith("artist/") && path.endsWith("/related") -> "artist/related"
        else -> path.ifBlank { "unknown" }
    }
}

private fun String.sanitizedDeezerUrl(): String =
    replace(Regex("""([?&]q=)[^&]+"""), "$1***")
