package app.naviamp.app

import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.DefaultNowPlayingHeartbeatIntervalMillis
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.shouldReportNowPlaying
import app.naviamp.domain.provider.PlaybackReportState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

const val NaviampPlaybackStateReportIntervalMillis = 15_000L

data class NaviampPlaybackStateReportRequest(
    val sessionId: Long,
    val trackId: TrackId,
    val isInternetRadioTrack: Boolean,
    val supportsPlayReporting: Boolean,
    val playbackState: PlaybackState,
    val progress: PlaybackProgress,
    val nowEpochMillis: Long,
)

data class NaviampPlaybackStateReport(
    val trackId: TrackId,
    val state: PlaybackReportState,
    val positionSeconds: Double?,
)

data class NaviampNowPlayingReportRequest(
    val trackId: TrackId,
    val isInternetRadioTrack: Boolean,
    val supportsPlayReporting: Boolean,
)

data class NaviampNowPlayingReport(val trackId: TrackId)

data class NaviampNowPlayingHeartbeatRequest(
    val trackId: TrackId,
    val isInternetRadioTrack: Boolean,
    val supportsPlayReporting: Boolean,
    val playbackState: PlaybackState,
)

suspend fun runNaviampNowPlayingHeartbeat(
    request: NaviampNowPlayingHeartbeatRequest,
    report: suspend (TrackId) -> Unit,
    waitForNext: suspend () -> Unit = { delay(DefaultNowPlayingHeartbeatIntervalMillis) },
) {
    if (
        !shouldReportNowPlaying(
            supportsPlayReporting = request.supportsPlayReporting,
            isInternetRadioTrack = request.isInternetRadioTrack,
            playbackState = request.playbackState,
        )
    ) {
        return
    }
    while (true) {
        try {
            report(request.trackId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Heartbeats are best effort. Offline-capable provider adapters may persist failures.
        }
        waitForNext()
    }
}

/** Owns cross-platform playback-report mapping, eligibility, and throttling. */
class NaviampPlaybackReportingController(
    private val reportIntervalMillis: Long = NaviampPlaybackStateReportIntervalMillis,
) {
    private var lastSessionId: Long? = null
    private var lastState: PlaybackReportState? = null
    private var lastReportAtMillis: Long = 0L

    fun nowPlayingReport(request: NaviampNowPlayingReportRequest): NaviampNowPlayingReport? =
        if (
            canReportPlaybackTrack(
                supportsPlayReporting = request.supportsPlayReporting,
                isInternetRadioTrack = request.isInternetRadioTrack,
            )
        ) {
            NaviampNowPlayingReport(request.trackId)
        } else {
            null
        }

    fun stateReport(request: NaviampPlaybackStateReportRequest): NaviampPlaybackStateReport? {
        val reportState = request.playbackState.toPlaybackReportState() ?: return null
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = request.supportsPlayReporting,
                isInternetRadioTrack = request.isInternetRadioTrack,
            )
        ) {
            return null
        }
        val shouldReport = lastSessionId != request.sessionId ||
            lastState != reportState ||
            (reportState == PlaybackReportState.Playing &&
                request.nowEpochMillis - lastReportAtMillis >= reportIntervalMillis)
        if (!shouldReport) return null

        lastSessionId = request.sessionId
        lastState = reportState
        lastReportAtMillis = request.nowEpochMillis
        return NaviampPlaybackStateReport(
            trackId = request.trackId,
            state = reportState,
            positionSeconds = request.progress.positionSeconds,
        )
    }
}

fun PlaybackState.toPlaybackReportState(): PlaybackReportState? =
    when (this) {
        PlaybackState.Loading -> PlaybackReportState.Starting
        PlaybackState.Playing -> PlaybackReportState.Playing
        PlaybackState.Paused -> PlaybackReportState.Paused
        PlaybackState.Stopped,
        PlaybackState.Finished,
        is PlaybackState.Error,
        -> PlaybackReportState.Stopped
        PlaybackState.Idle -> null
    }
