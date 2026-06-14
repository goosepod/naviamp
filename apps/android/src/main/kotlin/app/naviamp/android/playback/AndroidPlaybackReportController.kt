package app.naviamp.android

import app.naviamp.domain.Track
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.shouldSubmitPlayReport
import app.naviamp.domain.provider.PendingActionReportNowPlaying
import app.naviamp.domain.provider.PendingActionReportPlayed
import app.naviamp.domain.provider.PendingProviderActionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidPlaybackReportController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    private val pendingProviderActions: PendingProviderActionRepository,
) {
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

    fun maybeReportPlayed(progress: PlaybackProgress) {
        val activeProvider = state.provider
        val track = state.nowPlaying ?: return
        val durationSeconds = progress.durationSeconds ?: track.durationSeconds?.toDouble()
        val activeSourceId = state.activeSourceId
        if (
            !shouldSubmitPlayReport(
                supportsPlayReporting = activeProvider?.capabilities?.supportsPlayReporting ?: (activeSourceId != null),
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
        val playedAtEpochMillis = System.currentTimeMillis()
        state.submittedPlayReportSessionToken = activeSessionToken
        if (activeProvider == null) {
            if (activeSourceId == null) {
                state.submittedPlayReportSessionToken = null
                return
            }
            scope.launch {
                withContext(Dispatchers.IO) {
                    pendingProviderActions.enqueuePendingProviderAction(
                        sourceId = activeSourceId,
                        actionType = PendingActionReportPlayed,
                        entityId = track.id.value,
                        longValue = playedAtEpochMillis,
                    )
                }
            }
            return
        }
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    activeProvider.reportPlayed(track.id, playedAtEpochMillis)
                }
            }
            if (result.isFailure) {
                val queued = state.activeSourceId?.let { sourceId ->
                    withContext(Dispatchers.IO) {
                        pendingProviderActions.enqueuePendingProviderAction(
                            sourceId = sourceId,
                            actionType = PendingActionReportPlayed,
                            entityId = track.id.value,
                            longValue = playedAtEpochMillis,
                        )
                    }
                    true
                } == true
                if (!queued && state.submittedPlayReportSessionToken == activeSessionToken) {
                    state.submittedPlayReportSessionToken = null
                }
            }
        }
    }
}
