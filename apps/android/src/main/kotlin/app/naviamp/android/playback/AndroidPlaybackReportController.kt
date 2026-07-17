package app.naviamp.android

import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.app.NaviampPlaybackReportingController
import app.naviamp.app.NaviampPlaybackStateReportRequest
import app.naviamp.app.NaviampProviderActionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidPlaybackReportController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val providerActions: NaviampProviderActionController,
) {
    private val reporting = NaviampPlaybackReportingController()

    fun reportNowPlaying(track: Track) {
        val activeProvider = state.provider
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = activeProvider?.capabilities?.supportsPlayReporting ?: (state.activeSourceId != null),
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        if (activeProvider == null) {
            val sourceId = state.activeSourceId ?: return
            scope.launch {
                withContext(Dispatchers.IO) {
                    providerActions.enqueueNowPlaying(sourceId, track.id)
                }
            }
            return
        }
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    providerActions
                        .offlineCapable(activeProvider, state.activeSourceId)
                        .reportNowPlaying(track.id)
                }
            }
        }
    }

    fun maybeReportPlaybackState(playbackState: PlaybackState, progress: PlaybackProgress = state.playbackProgress) {
        val activeProvider = state.provider ?: return
        val track = state.nowPlaying ?: return
        val report = reporting.stateReport(
            NaviampPlaybackStateReportRequest(
                sessionId = state.playbackSessionToken,
                trackId = track.id,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                playbackState = playbackState,
                progress = progress,
                nowEpochMillis = System.currentTimeMillis(),
            ),
        ) ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlaybackState(
                        trackId = report.trackId,
                        state = report.state,
                        positionSeconds = report.positionSeconds,
                    )
                }
            }
        }
    }
}
