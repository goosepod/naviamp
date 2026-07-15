package app.naviamp.domain.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.queue.resolveTrackOccurrenceIndex

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

data class PlaybackTrackStartEffectsPlan(
    val presentation: PlaybackTrackStartedPlan,
    val clearRadioContinuation: Boolean,
    val savePlaybackSession: Boolean,
    val refillRadioQueue: Boolean,
    val loadRelatedTracks: Boolean,
    val startAudioPrefetch: Boolean,
    val startSidecarPrep: Boolean,
    val updateNotificationMetadata: Boolean,
    val notificationTitle: String?,
    val notificationSubtitle: String?,
    val engineMediaId: String?,
    val engineStartPositionSeconds: Double?,
    val finishedAdjacentOffset: Int?,
)

data class PlaylistTrackStartWork<SessionId>(
    val sessionId: SessionId,
    val track: Track,
    val playbackSource: PlaybackSource,
    val coverArtUrl: String?,
    val request: PlaybackRequest,
    val startAudioPrefetch: Boolean,
    val startSidecarPrep: Boolean,
)

fun planPlaybackTrackStartEffects(
    track: Track,
    presentation: PlaybackTrackStartedPlan,
    startPlan: PlaybackStartPlan? = null,
    keepRadioQueueActive: Boolean,
): PlaybackTrackStartEffectsPlan =
    PlaybackTrackStartEffectsPlan(
        presentation = presentation,
        clearRadioContinuation = !keepRadioQueueActive,
        savePlaybackSession = true,
        refillRadioQueue = true,
        loadRelatedTracks = true,
        startAudioPrefetch = true,
        startSidecarPrep = true,
        updateNotificationMetadata = true,
        notificationTitle = track.title,
        notificationSubtitle = track.artistName,
        engineMediaId = track.id.value,
        engineStartPositionSeconds = startPlan?.target?.engineStartPositionSeconds,
        finishedAdjacentOffset = 1,
    )

fun <SessionId> planPlaylistTrackStartWork(
    sessionId: SessionId,
    track: Track,
    playbackSource: PlaybackSource,
    streamUrl: String,
    fallbackStreamUrl: String? = null,
    replayGainMode: ReplayGainMode,
    replayGainPreampDb: Float = 0f,
    replayGain: PlaybackReplayGain?,
    supportsReplayGain: Boolean,
    engineStartPositionSeconds: Double?,
    coverArtUrl: String?,
    startAudioPrefetch: Boolean = true,
    startSidecarPrep: Boolean = true,
): PlaylistTrackStartWork<SessionId> =
    PlaylistTrackStartWork(
        sessionId = sessionId,
        track = track,
        playbackSource = playbackSource,
        coverArtUrl = coverArtUrl,
        request = PlaybackRequest(
            url = streamUrl,
            fallbackUrl = fallbackStreamUrl,
            mediaId = track.id.value,
            samplingRateHz = track.audioInfo?.samplingRateHz,
            replayGainMode = if (supportsReplayGain) replayGainMode else ReplayGainMode.Off,
            replayGainPreampDb = if (supportsReplayGain) replayGainPreampDb else 0f,
            replayGain = replayGain,
            startPositionSeconds = engineStartPositionSeconds?.takeIf { it > 0.0 },
        ),
        startAudioPrefetch = startAudioPrefetch,
        startSidecarPrep = startSidecarPrep,
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
    requestedQueueIndex: Int? = null,
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
        queueIndex = resolveTrackOccurrenceIndex(
            tracks = queue,
            track = track,
            preferredIndex = requestedQueueIndex,
        )
            ?: 0,
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
