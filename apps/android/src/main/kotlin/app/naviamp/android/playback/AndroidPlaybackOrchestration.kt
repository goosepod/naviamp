package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.planPlaybackProgressUpdate
import app.naviamp.domain.playback.planPlaybackStart
import app.naviamp.domain.playback.planPlaybackTrackStartEffects
import app.naviamp.domain.playback.planPlaybackTrackStarted
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.provider.navidrome.NavidromeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun beginAndroidPlaybackSession(
    state: AndroidAppState,
    playbackQueueController: PlaybackQueueController,
    resetProgress: Boolean = true,
): Long {
    with(state) {
        playbackSessionToken += 1
        submittedPlayReportSessionToken = null
        audioPrefetchJob?.cancel()
        audioPrefetchJob = null
        sidecarPrepJob?.cancel()
        sidecarPrepJob = null
        playbackQueueController.clearPreparedNext()
        pendingSeekPositionSeconds = null
        pendingSeekIssuedAtMillis = null
        if (resetProgress) {
            playbackProgress = PlaybackProgress.Unknown
        }
        return playbackSessionToken
    }
}

fun playAndroidTrack(
    scope: CoroutineScope,
    state: AndroidAppState,
    storage: AndroidStorage,
    playbackEngine: AndroidPlaybackEngine,
    playbackQueueController: PlaybackQueueController,
    track: Track,
    queue: List<Track>? = null,
    openNowPlaying: Boolean = true,
    startPositionSeconds: Double? = null,
    keepRadioQueueActive: Boolean = false,
    activeQueue: () -> List<Track>,
    currentStreamQuality: () -> StreamQuality,
    savePlaybackSessionThrottled: (force: Boolean) -> Unit,
    reportNowPlaying: (Track) -> Unit,
    loadRelatedTracks: (Track) -> Unit,
    loadLyrics: (Track) -> Unit,
    startAudioPrefetch: (Long, NavidromeProvider, PlaybackQueue) -> Unit,
    startSidecarPrep: (Long, NavidromeProvider, PlaybackQueue) -> Unit,
    handlePlaybackProgressChanged: (Long, PlaybackProgress) -> Unit,
    playAdjacentTrack: (Int) -> Unit,
) {
    android.util.Log.i("NaviampBass", "playTrack requested id=${track.id.value} title=${track.title}")
    val activeProvider = state.provider
    if (activeProvider == null) {
        state.status = "Connect before playing a track."
        return
    }
    scope.launch {
        with(state) {
            status = "Loading ${track.title}..."
            val streamQuality = currentStreamQuality()
            val localAudioFile = activeSourceId?.let { sourceId ->
                storage.downloadedAudioFile(sourceId, track.id, streamQuality)?.file
                    ?: storage.cachedAudioFile(sourceId, track.id, streamQuality)?.file
            }
            val startPlan = planPlaybackStart(
                track = track,
                requestedQueue = queue,
                activeQueue = activeQueue(),
                quality = streamQuality,
                startPositionSeconds = startPositionSeconds,
                hasLocalAudio = localAudioFile != null,
            )
            runCatching {
                localAudioFile?.toURI()?.toString()
                    ?: activeProvider.streamUrl(startPlan.target.providerStreamRequest)
            }.onSuccess { streamUrl ->
                playbackEngine.applyTlsSettings(activeTlsSettings)
                playbackQueueController.start(
                    tracks = startPlan.queue,
                    index = startPlan.queueIndex,
                )
                playbackQueue = playbackQueueController.queue
                val trackStartedPlan = planPlaybackTrackStarted(
                    previousTrack = nowPlaying,
                    track = track,
                    openNowPlaying = openNowPlaying,
                    nowPlayingOpen = nowPlayingOpen,
                    lyricsVisible = lyricsVisible,
                    supportsTrackFavorites = activeProvider.capabilities.supportsTrackFavorites,
                )
                val effectsPlan = planPlaybackTrackStartEffects(
                    track = track,
                    presentation = trackStartedPlan,
                    startPlan = startPlan,
                    keepRadioQueueActive = keepRadioQueueActive,
                )
                if (effectsPlan.presentation.clearShuffleSnapshot) shuffledUpNextSnapshot = null
                if (effectsPlan.clearRadioContinuation) {
                    radioQueueActive = false
                    radioRefilling = false
                    lastRadioRefillSeedId = null
                }
                val sessionToken = beginAndroidPlaybackSession(
                    state = state,
                    playbackQueueController = playbackQueueController,
                    resetProgress = startPlan.shouldResetProgress,
                )
                startPlan.restoredStartPositionSeconds?.let { restoredPosition ->
                    playbackProgress = startPlan.initialProgress ?: PlaybackProgress(
                        positionSeconds = restoredPosition,
                        durationSeconds = track.durationSeconds?.toDouble(),
                    )
                    pendingSeekPositionSeconds = restoredPosition
                    pendingSeekIssuedAtMillis = System.currentTimeMillis()
                    pendingRestoreStartPositionSeconds = restoredPosition
                    AndroidPlaybackNotificationControls.positionMillis = restoredPosition.secondsToMillis()
                    AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds?.toDouble()?.secondsToMillis()
                } ?: run {
                    AndroidPlaybackNotificationControls.positionMillis = null
                    AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds?.toDouble()?.secondsToMillis()
                }
                nowPlaying = track
                AndroidPlaybackNotificationControls.canFavorite = effectsPlan.presentation.canFavoriteTrack
                AndroidPlaybackNotificationControls.isFavorite = effectsPlan.presentation.isFavoriteTrack
                if (effectsPlan.presentation.clearInternetRadioNowPlaying) nowPlayingStation = null
                if (effectsPlan.presentation.resetStreamMetadata) nowPlayingStreamMetadata = PlaybackStreamMetadata()
                if (effectsPlan.savePlaybackSession) savePlaybackSessionThrottled(true)
                if (effectsPlan.presentation.shouldOpenNowPlaying) {
                    nowPlayingOpen = true
                }
                if (effectsPlan.presentation.shouldReportNowPlaying) reportNowPlaying(track)
                if (effectsPlan.refillRadioQueue) {
                    refillAndroidRadioIfNeeded(
                        scope = scope,
                        state = state,
                        queue = playbackQueue,
                        queueController = playbackQueueController,
                    )
                }
                if (effectsPlan.loadRelatedTracks) loadRelatedTracks(track)
                if (effectsPlan.presentation.shouldLoadLyrics) loadLyrics(track)
                if (effectsPlan.startAudioPrefetch) startAudioPrefetch(sessionToken, activeProvider, playbackQueue)
                if (effectsPlan.startSidecarPrep) startSidecarPrep(sessionToken, activeProvider, playbackQueue)
                if (effectsPlan.updateNotificationMetadata) {
                    playbackEngine.updateNotificationMetadata(
                        title = effectsPlan.notificationTitle,
                        subtitle = effectsPlan.notificationSubtitle,
                        coverArtUrl = track.coverArtUrl(activeProvider),
                    )
                }
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(
                        url = streamUrl,
                        mediaId = effectsPlan.engineMediaId,
                        replayGainMode = playbackSettings.replayGainMode,
                        replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                        startPositionSeconds = effectsPlan.engineStartPositionSeconds,
                    ),
                    onStateChanged = { playbackState ->
                        state.playbackState = playbackState
                        when (playbackState) {
                            PlaybackState.Finished -> effectsPlan.finishedAdjacentOffset?.let(playAdjacentTrack)
                            is PlaybackState.Error -> status = playbackState.message
                            else -> Unit
                        }
                    },
                    onProgressChanged = { progress -> handlePlaybackProgressChanged(sessionToken, progress) },
                )
                status = "Loading ${track.title}..."
            }.onFailure { error ->
                status = error.message ?: "Playback failed."
            }
        }
    }
}

fun handleAndroidPlaybackProgressChanged(
    context: Context,
    state: AndroidAppState,
    sessionToken: Long,
    progress: PlaybackProgress,
    maybeReportPlayed: (PlaybackProgress) -> Unit,
    prepareNextIfNeeded: (Long, PlaybackProgress) -> Unit,
) {
    with(state) {
        val nowMillis = System.currentTimeMillis()
        val plan = planPlaybackProgressUpdate(
            sessionToken = sessionToken,
            activeSessionToken = playbackSessionToken,
            incomingProgress = progress,
            currentProgress = playbackProgress,
            pendingSeekPositionSeconds = pendingSeekPositionSeconds,
            pendingSeekIssuedAtMillis = pendingSeekIssuedAtMillis,
            pendingRestoreStartPositionSeconds = pendingRestoreStartPositionSeconds,
            nowMillis = nowMillis,
            lastExternalProgressPublishAtMillis = lastAndroidAutoProgressPublishAtMillis,
            externalProgressPublishIntervalMillis = AndroidAutoProgressPublishIntervalMillis,
        )
        if (plan.ignore) return
        if (plan.resetToUnknown) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
            playbackProgress = PlaybackProgress.Unknown
            AndroidPlaybackNotificationControls.positionMillis = null
            AndroidPlaybackNotificationControls.durationMillis = null
            AndroidPlaybackForegroundService.updateProgress(context, null, null)
            return
        }
        if (plan.clearPendingSeek) {
            pendingSeekPositionSeconds = null
            pendingSeekIssuedAtMillis = null
        }
        if (plan.clearPendingRestoreStart) {
            pendingRestoreStartPositionSeconds = null
        }
        playbackProgress = plan.progress ?: return
        if (plan.shouldReportPlayed) maybeReportPlayed(playbackProgress)
        val positionMillis = playbackProgress.positionSeconds?.secondsToMillis()
        val durationMillis = playbackProgress.durationSeconds
            ?.secondsToMillis()
            ?: nowPlaying?.durationSeconds?.toDouble()?.secondsToMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis
        if (plan.shouldPublishExternalProgress) {
            lastAndroidAutoProgressPublishAtMillis = nowMillis
            AndroidPlaybackForegroundService.updateProgress(context, positionMillis, durationMillis)
        }
        if (plan.shouldPrepareNext) prepareNextIfNeeded(sessionToken, playbackProgress)
    }
}
