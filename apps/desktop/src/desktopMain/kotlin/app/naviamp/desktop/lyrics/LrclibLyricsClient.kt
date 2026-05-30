package app.naviamp.desktop

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.lyrics.LrclibApiCall
import app.naviamp.domain.lyrics.LrclibApiCallHistory
import app.naviamp.domain.lyrics.lrclibApiCall
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

typealias DesktopLrclibApiCall = LrclibApiCall

object DesktopLrclibApiCallHistory {
    private val history = LrclibApiCallHistory()

    fun record(call: DesktopLrclibApiCall) {
        synchronized(history) { history.record(call) }
    }

    fun recent(limit: Int = 50): List<DesktopLrclibApiCall> =
        synchronized(history) { history.recent(limit) }
}

private fun DesktopSharedHttpCall.toLrclibCall(): DesktopLrclibApiCall =
    lrclibApiCall(
        url = url,
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )
