package app.naviamp.android

import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.shouldSubmitPlayReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidPlaybackReportController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
) {
    fun reportNowPlaying(track: Track) {
        val activeProvider = state.provider ?: return
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportNowPlaying(track.id)
                }
            }
        }
    }

    fun maybeReportPlayed(progress: PlaybackProgress) {
        val activeProvider = state.provider ?: return
        val track = state.nowPlaying ?: return
        val durationSeconds = progress.durationSeconds ?: track.durationSeconds?.toDouble()
        if (
            !shouldSubmitPlayReport(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                activeSessionId = state.playbackSessionToken,
                submittedSessionId = state.submittedPlayReportSessionToken,
                positionSeconds = progress.positionSeconds,
                durationSeconds = durationSeconds,
            )
        ) {
            return
        }

        val activeSessionToken = state.playbackSessionToken
        state.submittedPlayReportSessionToken = activeSessionToken
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlayed(track.id, System.currentTimeMillis())
                }
            }.onFailure {
                if (state.submittedPlayReportSessionToken == activeSessionToken) {
                    state.submittedPlayReportSessionToken = null
                }
            }
        }
    }
}
