package app.naviamp.desktop

import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.NaviampProjectUserAgent
import app.naviamp.domain.network.SharedHttpCall
import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.lyrics.LrclibApiCall
import app.naviamp.domain.lyrics.LrclibApiCallHistory
import app.naviamp.domain.lyrics.lrclibApiCall

class DesktopLrclibLyricsClient(
    baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider(
    KtorSharedHttpClient(
        callRecorder = { call -> DesktopLrclibApiCallHistory.record(call.toLrclibCall()) },
        defaultHeaders = mapOf(
            "Accept" to "application/json",
            "User-Agent" to NaviampProjectUserAgent,
        ),
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

private fun SharedHttpCall.toLrclibCall(): DesktopLrclibApiCall =
    lrclibApiCall(
        url = url,
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )
