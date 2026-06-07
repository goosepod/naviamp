package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassPlaybackSnapshot

data class BassPlaybackPollingState(
    val lastProgress: PlaybackProgress = PlaybackProgress.Unknown,
    val lastMetadata: PlaybackStreamMetadata = PlaybackStreamMetadata(),
    val lastActiveState: Int? = null,
)

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
    emitDuplicateProgress: Boolean,
    finishOnSourceEnd: Boolean,
): BassPlaybackPollingUpdate {
    val activeStateChanged = snapshot.activeState != previous.lastActiveState
    val progressChanged = snapshot.progress != previous.lastProgress
    val metadataChanged = snapshot.metadata != previous.lastMetadata
    val finished = finishOnSourceEnd &&
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
            lastProgress = if (progressChanged || emitDuplicateProgress) snapshot.progress else previous.lastProgress,
            lastMetadata = if (metadataChanged) snapshot.metadata else previous.lastMetadata,
            lastActiveState = if (activeStateChanged) snapshot.activeState else previous.lastActiveState,
        ),
        activeStateChanged = activeStateChanged,
        progress = snapshot.progress.takeIf { progressChanged || emitDuplicateProgress },
        metadata = snapshot.metadata.takeIf { metadataChanged },
        playbackState = playbackState,
        finished = finished,
        shouldContinue = !finished && shouldContinueBassPlaybackPolling(snapshot.activeState),
    )
}
