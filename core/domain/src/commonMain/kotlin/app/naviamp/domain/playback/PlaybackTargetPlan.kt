package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track

data class PlaybackTargetPlan(
    val engineStartPositionSeconds: Double?,
    val providerStreamRequest: StreamRequest,
)

data class PlaybackStartPlan(
    val queue: List<Track>,
    val queueIndex: Int,
    val target: PlaybackTargetPlan,
    val restoredStartPositionSeconds: Double?,
    val initialProgress: PlaybackProgress?,
) {
    val shouldResetProgress: Boolean = restoredStartPositionSeconds == null
}

data class PlaybackTrackStartedPlan(
    val trackChanged: Boolean,
    val clearShuffleSnapshot: Boolean,
    val clearInternetRadioNowPlaying: Boolean,
    val resetStreamMetadata: Boolean,
    val resetProgress: Boolean,
    val resetSidecars: Boolean,
    val shouldOpenNowPlaying: Boolean,
    val shouldReportNowPlaying: Boolean,
    val canFavoriteTrack: Boolean,
    val isFavoriteTrack: Boolean,
    val shouldLoadLyrics: Boolean,
)

fun planPlaybackTrackStarted(
    previousTrack: Track?,
    track: Track,
    openNowPlaying: Boolean,
    nowPlayingOpen: Boolean,
    lyricsVisible: Boolean,
    supportsTrackFavorites: Boolean,
): PlaybackTrackStartedPlan {
    val trackChanged = previousTrack?.id != track.id
    val nextNowPlayingOpen = nowPlayingOpen || openNowPlaying
    return PlaybackTrackStartedPlan(
        trackChanged = trackChanged,
        clearShuffleSnapshot = trackChanged,
        clearInternetRadioNowPlaying = true,
        resetStreamMetadata = true,
        resetProgress = true,
        resetSidecars = trackChanged,
        shouldOpenNowPlaying = openNowPlaying,
        shouldReportNowPlaying = true,
        canFavoriteTrack = supportsTrackFavorites,
        isFavoriteTrack = track.favoritedAtIso8601 != null,
        shouldLoadLyrics = lyricsVisible && nextNowPlayingOpen,
    )
}

fun planPlaybackStart(
    track: Track,
    requestedQueue: List<Track>?,
    activeQueue: List<Track>,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    hasLocalAudio: Boolean,
): PlaybackStartPlan {
    val queue = requestedQueue
        ?.takeIf { tracks -> tracks.any { it.id == track.id } }
        ?: activeQueue.takeIf { tracks -> tracks.any { it.id == track.id } }
        ?: listOf(track)
    val target = playbackTargetPlan(
        track = track,
        quality = quality,
        startPositionSeconds = startPositionSeconds,
        hasLocalAudio = hasLocalAudio,
    )
    val restoredStartPosition = target.engineStartPositionSeconds?.takeIf { it > 0.0 }
    return PlaybackStartPlan(
        queue = queue,
        queueIndex = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0,
        target = target,
        restoredStartPositionSeconds = restoredStartPosition,
        initialProgress = restoredStartPosition?.let { positionSeconds ->
            PlaybackProgress(
                positionSeconds = positionSeconds,
                durationSeconds = track.durationSeconds?.toDouble(),
            )
        },
    )
}

fun playbackTargetPlan(
    track: Track,
    quality: StreamQuality,
    startPositionSeconds: Double?,
    hasLocalAudio: Boolean,
): PlaybackTargetPlan {
    val engineStartPositionSeconds = startPositionSeconds
        ?.takeIf { hasLocalAudio || quality !is StreamQuality.Transcoded }
    val providerStartPositionSeconds = startPositionSeconds
        ?.takeIf { !hasLocalAudio && quality is StreamQuality.Transcoded }
    return PlaybackTargetPlan(
        engineStartPositionSeconds = engineStartPositionSeconds,
        providerStreamRequest = StreamRequest(
            trackId = track.id,
            quality = quality,
            startPositionSeconds = providerStartPositionSeconds,
        ),
    )
}
