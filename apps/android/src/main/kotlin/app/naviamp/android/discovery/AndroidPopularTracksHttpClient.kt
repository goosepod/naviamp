package app.naviamp.android

import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.SharedHttpCall
import app.naviamp.domain.network.SharedHttpClient

class AndroidPopularTracksHttpClient : SharedHttpClient by KtorSharedHttpClient(
    callRecorder = { call -> AndroidPopularTracksApiCallHistory.record(call.toPopularTracksCall()) },
    defaultHeaders = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Naviamp/0.9.0 (https://github.com/jbmcmichael/Naviamp)",
    ),
)

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

private fun SharedHttpCall.toPopularTracksCall(): AndroidPopularTracksApiCall =
    AndroidPopularTracksApiCall(
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
