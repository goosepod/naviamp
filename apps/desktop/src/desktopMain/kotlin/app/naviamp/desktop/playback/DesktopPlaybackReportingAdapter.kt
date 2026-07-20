package app.naviamp.desktop

import app.naviamp.app.NaviampNowPlayingReportRequest
import app.naviamp.app.NaviampPlaybackReportingController
import app.naviamp.app.NaviampPlaybackStateReportRequest
import app.naviamp.app.NaviampProviderActionController
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desktop host adapter for best-effort provider report execution. */
internal class DesktopPlaybackReportingAdapter(
    private val scope: CoroutineScope,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val providerActions: NaviampProviderActionController,
    private val reporting: NaviampPlaybackReportingController,
    private val playbackProgress: () -> PlaybackProgress,
    private val nowPlayingTrack: () -> Track?,
    private val playReportSessionId: () -> Int,
    private val nowEpochMillis: () -> Long = DesktopSystemClock::nowEpochMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    fun reportNowPlaying(track: Track) {
        val activeProvider = provider() ?: return
        val report = reporting.nowPlayingReport(
            NaviampNowPlayingReportRequest(
                trackId = track.id,
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            ),
        ) ?: return
        scope.launch {
            runCatching {
                withContext(dispatcher) {
                    reportNowPlaying(report.trackId)
                }
            }
        }
    }

    suspend fun reportNowPlaying(trackId: TrackId) {
        val activeProvider = provider() ?: return
        providerActions.offlineCapable(activeProvider, sourceId()).reportNowPlaying(trackId)
    }

    fun maybeReportPlaybackState(
        state: PlaybackState,
        progress: PlaybackProgress = playbackProgress(),
    ) {
        val activeProvider = provider() ?: return
        val track = nowPlayingTrack() ?: return
        val report = reporting.stateReport(
            NaviampPlaybackStateReportRequest(
                sessionId = playReportSessionId().toLong(),
                trackId = track.id,
                isInternetRadioTrack = track.isInternetRadioTrack(),
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                playbackState = state,
                progress = progress,
                nowEpochMillis = nowEpochMillis(),
            ),
        ) ?: return
        scope.launch {
            runCatching {
                withContext(dispatcher) {
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
