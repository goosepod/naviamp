package app.naviamp.domain.settings

import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.queue.PlaybackQueue

data class RestoredPlaybackSession(
    val tracks: List<Track>,
    val currentIndex: Int,
    val currentTrack: Track,
    val playbackQueue: PlaybackQueue,
    val playbackProgress: PlaybackProgress,
)

fun PlaybackSessionSettings.restoredTrackSession(): RestoredPlaybackSession? {
    val tracks = toTracks()
    val currentIndex = currentIndex.takeIf { it in tracks.indices } ?: return null
    val currentTrack = tracks[currentIndex]
    return RestoredPlaybackSession(
        tracks = tracks,
        currentIndex = currentIndex,
        currentTrack = currentTrack,
        playbackQueue = PlaybackQueue(
            tracks = tracks,
            currentIndex = currentIndex,
            playNextCount = playNextCount.coerceIn(0, tracks.size - currentIndex - 1),
        ),
        playbackProgress = PlaybackProgress(
            positionSeconds = positionSeconds,
            durationSeconds = currentTrack.durationSeconds?.toDouble(),
        ),
    )
}

fun PlaybackSessionSettings.restoredPlaybackQueue(): PlaybackQueue =
    restoredTrackSession()?.playbackQueue ?: PlaybackQueue()

fun playbackSessionFromQueue(
    queue: PlaybackQueue,
    positionSeconds: Double? = null,
): PlaybackSessionSettings? =
    PlaybackSessionSettings.fromTracks(
        tracks = queue.tracks,
        currentIndex = queue.currentIndex,
        playNextCount = queue.playNextCount,
        positionSeconds = positionSeconds,
    )

fun playbackSessionFromCurrentTrack(
    currentTrack: Track,
    queue: PlaybackQueue,
    positionSeconds: Double? = null,
): PlaybackSessionSettings? {
    val sessionQueue = queue.takeIf { it.current?.id == currentTrack.id }
        ?: PlaybackQueue(tracks = listOf(currentTrack), currentIndex = 0)
    return playbackSessionFromQueue(sessionQueue, positionSeconds)
}

fun PlaybackSessionSettings.withPlaybackPosition(positionSeconds: Double?): PlaybackSessionSettings =
    copy(positionSeconds = positionSeconds?.takeIf { it > 0.0 })

fun PlaybackSessionSettings.adjacentTrackSession(delta: Int): PlaybackSessionSettings? {
    val tracks = toTracks()
    if (tracks.isEmpty()) return null
    return copy(
        currentIndex = (currentIndex + delta).coerceIn(tracks.indices),
        positionSeconds = null,
    )
}
