package app.naviamp.desktop

import app.naviamp.desktop.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.shouldRestartInsteadOfPrevious
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PreviousButtonBehavior
import kotlin.math.abs

fun shouldSavePlaybackPosition(
    queue: PlaybackQueue,
    positionSeconds: Double?,
    lastSavedPositionSeconds: Double?,
    saveThresholdSeconds: Double = PlaybackPositionSaveThresholdSeconds,
): Boolean {
    val position = positionSeconds ?: return false
    if (queue.currentIndex !in queue.tracks.indices) return false
    val lastSaved = lastSavedPositionSeconds
    return lastSaved == null || abs(position - lastSaved) >= saveThresholdSeconds
}

fun canUsePreviousButton(
    queue: PlaybackQueue,
    previousButtonBehavior: PreviousButtonBehavior,
    positionSeconds: Double?,
    restartThresholdSeconds: Double = PreviousRestartThresholdSeconds,
): Boolean =
    queue.hasPrevious() ||
        shouldRestartInsteadOfPrevious(
            previousButtonBehavior = previousButtonBehavior,
            positionSeconds = positionSeconds,
            restartThresholdSeconds = restartThresholdSeconds,
        )

fun canUseNextButton(
    queue: PlaybackQueue,
    repeatMode: RepeatMode,
): Boolean =
    queue.hasNext() ||
        queue.nextIndex(repeatMode = repeatMode, repeatTrack = false) != null

fun shouldReplayCurrentForSeek(playbackSource: PlaybackSource): Boolean =
    playbackSource == PlaybackSource.ProviderStream ||
        playbackSource == PlaybackSource.ProviderStreamCacheDisabled
