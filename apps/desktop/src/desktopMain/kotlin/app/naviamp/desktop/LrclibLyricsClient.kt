package app.naviamp.desktop

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import java.net.URI
import java.net.http.HttpClient

class LrclibLyricsClient(
    httpClient: HttpClient = HttpClient.newHttpClient(),
    baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider(
    DesktopSharedHttpClient(
        httpClient = httpClient,
        callRecorder = { call -> DesktopLrclibApiCallHistory.record(call.toLrclibCall()) },
    ),
    baseUrl,
)

data class DesktopLrclibApiCall(
    val endpoint: String,
    val sanitizedUrl: String,
    val startedAtEpochMillis: Long,
    val durationMillis: Long,
    val success: Boolean,
    val errorMessage: String?,
)

object DesktopLrclibApiCallHistory {
    private const val MaxCalls = 150
    private val lock = Any()
    private val calls = ArrayDeque<DesktopLrclibApiCall>()

    fun record(call: DesktopLrclibApiCall) {
        synchronized(lock) {
            calls.addLast(call)
            while (calls.size > MaxCalls) {
                calls.removeFirst()
            }
        }
    }

    fun recent(limit: Int = 50): List<DesktopLrclibApiCall> =
        synchronized(lock) {
            calls.takeLast(limit.coerceAtLeast(0)).asReversed()
        }
}

private fun DesktopSharedHttpCall.toLrclibCall(): DesktopLrclibApiCall =
    DesktopLrclibApiCall(
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
