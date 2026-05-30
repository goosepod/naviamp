package app.naviamp.android

import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.lyrics.LrclibApiCall
import app.naviamp.domain.lyrics.LrclibApiCallHistory
import app.naviamp.domain.lyrics.lrclibApiCall

class AndroidLrclibLyricsClient(
    baseUrl: String = "https://lrclib.net",
) : LrclibLyricsProvider(
    AndroidSharedHttpClient(
        callRecorder = { call -> AndroidLrclibApiCallHistory.record(call.toLrclibCall()) },
    ),
    baseUrl,
)

typealias AndroidLrclibApiCall = LrclibApiCall

object AndroidLrclibApiCallHistory {
    private val history = LrclibApiCallHistory()

    fun record(call: AndroidLrclibApiCall) {
        synchronized(history) { history.record(call) }
    }

    fun recent(limit: Int = 50): List<AndroidLrclibApiCall> =
        synchronized(history) { history.recent(limit) }
}

private fun AndroidSharedHttpCall.toLrclibCall(): AndroidLrclibApiCall =
    lrclibApiCall(
        url = url,
        startedAtEpochMillis = startedAtEpochMillis,
        durationMillis = durationMillis,
        success = success,
        errorMessage = errorMessage,
    )
