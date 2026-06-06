package app.naviamp.domain.playback

import app.naviamp.domain.Track
import app.naviamp.domain.queue.PlaybackQueue

enum class SleepTimerTarget(
    val label: String,
) {
    Duration("duration"),
    TrackEnd("current track"),
    AlbumEnd("current album"),
    QueueEnd("queue end"),
}

data class SleepTimerState(
    val target: SleepTimerTarget,
    val startedAtEpochMillis: Long,
    val durationMillis: Long? = null,
    val trackId: String? = null,
    val albumId: String? = null,
) {
    val isDuration: Boolean
        get() = target == SleepTimerTarget.Duration
}

data class SleepTimerPlaybackSnapshot(
    val currentTrackId: String?,
    val currentAlbumId: String?,
    val nextTrackAlbumId: String? = null,
    val positionSeconds: Double? = null,
    val durationSeconds: Double? = null,
    val playbackEnded: Boolean = false,
)

sealed interface SleepTimerRequest {
    data class DurationMinutes(val minutes: Int) : SleepTimerRequest
    data object TrackEnd : SleepTimerRequest
    data object AlbumEnd : SleepTimerRequest
    data object QueueEnd : SleepTimerRequest
}

fun sleepTimerState(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    snapshot: SleepTimerPlaybackSnapshot,
    queueLastTrackId: String?,
): SleepTimerState =
    when (request) {
        is SleepTimerRequest.DurationMinutes -> SleepTimerState(
            target = SleepTimerTarget.Duration,
            startedAtEpochMillis = nowEpochMillis,
            durationMillis = request.minutes.coerceAtLeast(1) * 60_000L,
            trackId = snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
        SleepTimerRequest.TrackEnd -> SleepTimerState(
            target = SleepTimerTarget.TrackEnd,
            startedAtEpochMillis = nowEpochMillis,
            trackId = snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
        SleepTimerRequest.AlbumEnd -> SleepTimerState(
            target = SleepTimerTarget.AlbumEnd,
            startedAtEpochMillis = nowEpochMillis,
            trackId = snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
        SleepTimerRequest.QueueEnd -> SleepTimerState(
            target = SleepTimerTarget.QueueEnd,
            startedAtEpochMillis = nowEpochMillis,
            trackId = queueLastTrackId ?: snapshot.currentTrackId,
            albumId = snapshot.currentAlbumId,
        )
    }

fun sleepTimerStateForPlayback(
    request: SleepTimerRequest,
    nowEpochMillis: Long,
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
): SleepTimerState =
    sleepTimerState(
        request = request,
        nowEpochMillis = nowEpochMillis,
        snapshot = sleepTimerPlaybackSnapshot(
            nowPlaying = nowPlaying,
            playbackQueue = playbackQueue,
            playbackProgress = playbackProgress,
            playbackState = playbackState,
        ),
        queueLastTrackId = playbackQueue.tracks.lastOrNull()?.id?.value,
    )

fun sleepTimerPlaybackSnapshot(
    nowPlaying: Track?,
    playbackQueue: PlaybackQueue,
    playbackProgress: PlaybackProgress,
    playbackState: PlaybackState,
): SleepTimerPlaybackSnapshot {
    val queueIndex = playbackQueue.currentIndex
    val nextTrack = playbackQueue.tracks.getOrNull(queueIndex + 1)
    return SleepTimerPlaybackSnapshot(
        currentTrackId = nowPlaying?.id?.value,
        currentAlbumId = nowPlaying?.albumId?.value,
        nextTrackAlbumId = nextTrack?.albumId?.value,
        positionSeconds = playbackProgress.positionSeconds,
        durationSeconds = playbackProgress.durationSeconds ?: nowPlaying?.durationSeconds?.toDouble(),
        playbackEnded = playbackState == PlaybackState.Finished ||
            playbackState == PlaybackState.Stopped ||
            playbackState == PlaybackState.Idle,
    )
}

fun sleepTimerRemainingMillis(timer: SleepTimerState, nowEpochMillis: Long): Long? {
    val duration = timer.durationMillis ?: return null
    return (timer.startedAtEpochMillis + duration - nowEpochMillis).coerceAtLeast(0L)
}

fun sleepTimerDisplayLabel(timer: SleepTimerState, nowEpochMillis: Long): String =
    when (timer.target) {
        SleepTimerTarget.Duration -> sleepTimerRemainingMillis(timer, nowEpochMillis)
            ?.let { "Sleep in ${sleepTimerDurationLabel(it)}" }
            ?: "Sleep timer"
        SleepTimerTarget.TrackEnd -> "Sleep after track"
        SleepTimerTarget.AlbumEnd -> "Sleep after album"
        SleepTimerTarget.QueueEnd -> "Sleep after queue"
    }

fun sleepTimerDurationLabel(millis: Long): String {
    val totalSeconds = ((millis + 999L) / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) {
        "${minutes}m ${seconds.toString().padStart(2, '0')}s"
    } else {
        "${seconds}s"
    }
}

fun shouldExpireSleepTimer(
    timer: SleepTimerState?,
    nowEpochMillis: Long,
    snapshot: SleepTimerPlaybackSnapshot,
): Boolean {
    timer ?: return false
    if (snapshot.playbackEnded) return true
    return when (timer.target) {
        SleepTimerTarget.Duration -> sleepTimerRemainingMillis(timer, nowEpochMillis) == 0L
        SleepTimerTarget.TrackEnd -> timer.trackId != null &&
            (
                snapshot.currentTrackId != timer.trackId ||
                    snapshot.isCurrentTrackEnding(timer.trackId)
            )
        SleepTimerTarget.AlbumEnd -> timer.albumId != null &&
            (
                snapshot.currentAlbumId != timer.albumId ||
                    (
                        snapshot.currentAlbumId == timer.albumId &&
                            snapshot.nextTrackAlbumId != timer.albumId &&
                            snapshot.isCurrentTrackEnding(targetTrackId = null)
                    )
            )
        SleepTimerTarget.QueueEnd -> timer.trackId != null &&
            snapshot.isCurrentTrackEnding(timer.trackId)
    }
}

private fun SleepTimerPlaybackSnapshot.isCurrentTrackEnding(targetTrackId: String?): Boolean {
    if (targetTrackId != null && currentTrackId != targetTrackId) return false
    val position = positionSeconds ?: return false
    val duration = durationSeconds ?: return false
    if (duration <= 0.0) return false
    return position >= duration - 0.5
}
