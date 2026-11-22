package app.naviamp.presentation

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.ui.NowPlayingUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToLong
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The playback position a remote Connect target last reported.
 *
 * Targets deliberately do not stream progress-only snapshots, so a controller presents the remote
 * position by extrapolating from this report the same way local smooth progress advances.
 */
internal data class NaviampRemoteProgressReport(
    val itemId: String,
    val positionMillis: Long,
    val durationMillis: Long?,
    val playing: Boolean,
) {
    /** The reported position advanced by [elapsedMillis] while playing, bounded by the duration. */
    fun progressAfter(elapsedMillis: Long): PlaybackProgress {
        val advanced = if (playing) positionMillis + elapsedMillis.coerceAtLeast(0L) else positionMillis
        val bounded = durationMillis?.let { advanced.coerceAtMost(it) } ?: advanced
        return PlaybackProgress(
            positionSeconds = bounded / 1_000.0,
            durationSeconds = durationMillis?.div(1_000.0),
        )
    }
}

/** Remote presentation reports only the fields that define its playback timeline. */
internal fun NowPlayingUi.toRemoteProgressReport(): NaviampRemoteProgressReport = NaviampRemoteProgressReport(
    itemId = id,
    positionMillis = ((positionSeconds ?: 0.0).takeIf { it.isFinite() } ?: 0.0).coerceAtLeast(0.0)
        .times(1_000.0).roundToLong(),
    durationMillis = durationSeconds?.takeIf { it.isFinite() && it >= 0.0 }?.times(1_000.0)?.roundToLong(),
    playing = isPlaying && !isLive,
)

/**
 * The single progress timeline every host presents: local engine progress, or the attached remote
 * target's last report extrapolated while it plays.
 *
 * A playing remote timeline publishes only when the displayed whole second changes, matching the
 * local engine's cadence; continuous scrubber motion stays with the shared compositor timeline.
 * Paused, buffering, ended, and detached timelines publish nothing further.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun naviampPresentedPlaybackProgress(
    scope: CoroutineScope,
    local: StateFlow<PlaybackProgress>,
    remote: Flow<NaviampRemoteProgressReport?>,
    timeSource: TimeSource = TimeSource.Monotonic,
): StateFlow<PlaybackProgress> = remote
    .distinctUntilChanged()
    // Stamp each report when it arrives, not when a screen starts collecting: a Now Playing view
    // opened mid-track must extrapolate from the report's receipt. Observing reports does no ticking.
    .map { report -> report?.let { ReceivedRemoteProgressReport(it, timeSource.markNow()) } }
    .stateIn(scope, SharingStarted.Eagerly, null)
    .flatMapLatest { received ->
        if (received == null) local else remoteProgressTimeline(received.report, received.receivedAt)
    }
    .stateIn(scope, SharingStarted.WhileSubscribed(), local.value)

private class ReceivedRemoteProgressReport(
    val report: NaviampRemoteProgressReport,
    val receivedAt: TimeMark,
)

/** Maps remote presentation state into reports; detaching from the target returns `null`. */
internal fun Flow<NowPlayingUi?>.remoteProgressReports(): Flow<NaviampRemoteProgressReport?> =
    map { it?.toRemoteProgressReport() }

private fun remoteProgressTimeline(
    report: NaviampRemoteProgressReport,
    reportedAt: TimeMark,
): Flow<PlaybackProgress> {
    if (!report.playing) return flowOf(report.progressAfter(0L))
    return flow {
        while (true) {
            val elapsed = reportedAt.elapsedNow().inWholeMilliseconds
            val progress = report.progressAfter(elapsed)
            emit(progress)
            val positionMillis = report.positionMillis + elapsed
            if (report.durationMillis != null && positionMillis >= report.durationMillis) break
            delay(RemoteProgressSecondMillis - positionMillis.mod(RemoteProgressSecondMillis))
        }
    }
}

private const val RemoteProgressSecondMillis = 1_000L
