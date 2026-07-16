package app.naviamp.android

import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.provider.PendingActionReportNowPlaying
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.provider.PlaybackReportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidPlaybackReportController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val pendingProviderActions: PendingProviderActionRepository,
) {
    private var lastPlaybackStateReportSessionToken: Long? = null
    private var lastPlaybackStateReportState: PlaybackReportState? = null
    private var lastPlaybackStateReportAtMillis: Long = 0L

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
                    pendingProviderActions.enqueuePendingProviderAction(
                        sourceId = sourceId,
                        actionType = PendingActionReportNowPlaying,
                        entityId = track.id.value,
                    )
                }
            }
            return
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportNowPlaying(track.id)
                }
            }
            if (result.isFailure) {
                state.activeSourceId?.let { sourceId ->
                    withContext(Dispatchers.IO) {
                        pendingProviderActions.enqueuePendingProviderAction(
                            sourceId = sourceId,
                            actionType = PendingActionReportNowPlaying,
                            entityId = track.id.value,
                        )
                    }
                }
            }
        }
    }

    fun maybeReportPlaybackState(playbackState: PlaybackState, progress: PlaybackProgress = state.playbackProgress) {
        val reportState = playbackState.toPlaybackReportState() ?: return
        val activeProvider = state.provider ?: return
        val track = state.nowPlaying ?: return
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = activeProvider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        val activeSessionToken = state.playbackSessionToken
        val nowMillis = System.currentTimeMillis()
        val sameSession = lastPlaybackStateReportSessionToken == activeSessionToken
        val sameState = lastPlaybackStateReportState == reportState
        val shouldReport = !sameSession ||
            !sameState ||
            (reportState == PlaybackReportState.Playing &&
                nowMillis - lastPlaybackStateReportAtMillis >= PlaybackStateReportIntervalMillis)
        if (!shouldReport) return

        lastPlaybackStateReportSessionToken = activeSessionToken
        lastPlaybackStateReportState = reportState
        lastPlaybackStateReportAtMillis = nowMillis
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlaybackState(
                        trackId = track.id,
                        state = reportState,
                        positionSeconds = progress.positionSeconds,
                    )
                }
            }
        }
    }
}

internal fun PlaybackState.toPlaybackReportState(): PlaybackReportState? =
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

internal const val PlaybackStateReportIntervalMillis = 15_000L
