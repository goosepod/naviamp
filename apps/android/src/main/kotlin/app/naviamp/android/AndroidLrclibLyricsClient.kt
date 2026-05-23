package app.naviamp.android

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import java.net.URI

class AndroidLrclibLyricsClient(
    baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider(
    AndroidSharedHttpClient(
        callRecorder = { call -> AndroidLrclibApiCallHistory.record(call.toLrclibCall()) },
    ),
    baseUrl,
)

data class AndroidLrclibApiCall(
    val endpoint: String,
    val sanitizedUrl: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
)

object AndroidLrclibApiCallHistory {
    private const val MaxCalls = 150
    private val lock = Any()
    private val calls = ArrayDeque<AndroidLrclibApiCall>()

    fun record(call: AndroidLrclibApiCall) {
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    fun recent(limit: Int = 50): List<AndroidLrclibApiCall> =
        synchronized(lock) {
            calls.takeLast(limit.coerceAtLeast(0)).asReversed()
        }
}

private fun AndroidSharedHttpCall.toLrclibCall(): AndroidLrclibApiCall =
    AndroidLrclibApiCall(
        endpoint = url.lrclibEndpointLabel(),
        sanitizedUrl = url.sanitizedLrclibUrl(),
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )

private fun String.lrclibEndpointLabel(): String {
    val path = runCatching { URI.create(this).path }.getOrNull().orEmpty().trim('/')
    return path.ifBlank { "unknown" }
}

private fun String.sanitizedLrclibUrl(): String =
    replace(Regex("""([?&](track_name|artist_name|album_name)=)[^&]+"""), "$1***")
