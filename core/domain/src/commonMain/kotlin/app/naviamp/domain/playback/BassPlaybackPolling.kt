package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassPlaybackSnapshot

data class BassPlaybackPollingState(
    val lastProgress: PlaybackProgress = PlaybackProgress.Unknown,
    val lastMetadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
    val lastActiveState: Int? = null,
)

data class BassPlaybackPollingPolicy(
    val pollIntervalMillis: Long,
    val emitDuplicateProgress: Boolean,
    val finishOnSourceEnd: Boolean,
    val finishWhenPollingStops: Boolean,
) {
    companion object {
        val AndroidService: BassPlaybackPollingPolicy =
            BassPlaybackPollingPolicy(
                pollIntervalMillis = DefaultAndroidBassPollingIntervalMillis,
                emitDuplicateProgress = false,
                finishOnSourceEnd = true,
                finishWhenPollingStops = false,
            )

        val DesktopEngine: BassPlaybackPollingPolicy =
            BassPlaybackPollingPolicy(
                pollIntervalMillis = DefaultDesktopBassPollingIntervalMillis,
                emitDuplicateProgress = false,
                finishOnSourceEnd = false,
                finishWhenPollingStops = true,
            )
    }
}

data class BassPlaybackPollingUpdate(
    val state: BassPlaybackPollingState,
    val activeStateChanged: Boolean,
    val progress: PlaybackProgress?,
    val metadata: PlaybackStreamMetadata?,
    val playbackState: PlaybackState?,
    val finished: Boolean,
    val shouldContinue: Boolean,
)

fun planBassPlaybackPollingUpdate(
    snapshot: BassPlaybackSnapshot,
    previous: BassPlaybackPollingState,
    policy: BassPlaybackPollingPolicy,
): BassPlaybackPollingUpdate {
    val activeStateChanged = snapshot.activeState != previous.lastActiveState
    val progressChanged = snapshot.progress != previous.lastProgress
    val metadataChanged = snapshot.metadata != previous.lastMetadata
    val finished = policy.finishOnSourceEnd &&
        shouldFinishPlaybackForBassState(
            activeState = snapshot.activeState,
            progress = snapshot.progress,
            currentSourceActiveState = snapshot.sourceActiveState,
        )
    val playbackState = if (finished) {
        PlaybackState.Finished
    } else if (activeStateChanged) {
        playbackStateForBassActiveState(snapshot.activeState)
    } else {
        null
    }
    return BassPlaybackPollingUpdate(
        state = BassPlaybackPollingState(
            lastProgress = if (progressChanged || policy.emitDuplicateProgress) snapshot.progress else previous.lastProgress,
            lastMetadata = if (metadataChanged) snapshot.metadata else previous.lastMetadata,
            lastActiveState = if (activeStateChanged) snapshot.activeState else previous.lastActiveState,
        ),
        activeStateChanged = activeStateChanged,
        progress = snapshot.progress.takeIf { progressChanged || policy.emitDuplicateProgress },
        metadata = snapshot.metadata.takeIf { metadataChanged },
        playbackState = playbackState,
        finished = finished,
        shouldContinue = !finished && shouldContinueBassPlaybackPolling(snapshot.activeState),
    )
}

fun planBassPlaybackPollingUpdate(
    snapshot: BassPlaybackSnapshot,
    previous: BassPlaybackPollingState,
    emitDuplicateProgress: Boolean,
    finishOnSourceEnd: Boolean,
): BassPlaybackPollingUpdate =
    planBassPlaybackPollingUpdate(
        snapshot = snapshot,
        previous = previous,
        policy = BassPlaybackPollingPolicy(
            pollIntervalMillis = DefaultBassPollingIntervalMillis,
            emitDuplicateProgress = emitDuplicateProgress,
            finishOnSourceEnd = finishOnSourceEnd,
            finishWhenPollingStops = false,
        ),
    )

const val DefaultBassPollingIntervalMillis = 250L
const val DefaultAndroidBassPollingIntervalMillis = 1_000L
const val DefaultDesktopBassPollingIntervalMillis = 250L
