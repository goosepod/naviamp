package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackTrackStartEffectApplier
import app.naviamp.domain.playback.applyPlaybackTrackStartEffects
import app.naviamp.domain.playback.planPlaylistTrackStartWork
import app.naviamp.domain.playback.planPlaybackProgressUpdate
import app.naviamp.domain.playback.planPlaybackStart
import app.naviamp.domain.playback.planPlaybackTrackStartEffects
import app.naviamp.domain.playback.planPlaybackTrackStarted
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.InternetRadioStartApplier
import app.naviamp.domain.radio.InternetRadioMetadataUpdateApplier
import app.naviamp.domain.radio.applyInternetRadioStart
import app.naviamp.domain.radio.applyInternetRadioMetadataUpdate
import app.naviamp.domain.radio.planInternetRadioMetadataUpdate
import app.naviamp.domain.radio.planInternetRadioPlaybackRequest
import app.naviamp.domain.radio.planInternetRadioStart
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
    audioAssets: PlaybackAudioAssetRepository,
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
    loadAudioTags: (Track) -> Unit,
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
            val audioSourcePlan = resolvePlaybackAudioSource(
                sourceId = activeSourceId,
                track = track,
                quality = streamQuality,
                audioCachingEnabled = true,
                startPositionSeconds = startPositionSeconds,
                audioAssets = audioAssets,
            )
            val startPlan = planPlaybackStart(
                track = track,
                requestedQueue = queue,
                activeQueue = activeQueue(),
                quality = streamQuality,
                startPositionSeconds = startPositionSeconds,
                hasLocalAudio = audioSourcePlan.hasLocalAudio,
            )
            runCatching {
                audioSourcePlan.playbackStreamUrl(
                    providerStreamUrl = { target -> activeProvider.streamUrl(target.providerStreamRequest) },
                )
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
                val trackStartWork = planPlaylistTrackStartWork(
                    sessionId = sessionToken,
                    track = track,
                    playbackSource = audioSourcePlan.source,
                    streamUrl = streamUrl,
                    replayGainMode = playbackSettings.replayGainMode,
                    replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    engineStartPositionSeconds = effectsPlan.engineStartPositionSeconds,
                    coverArtUrl = track.coverArtUrl(activeProvider),
                    startAudioPrefetch = effectsPlan.startAudioPrefetch,
                    startSidecarPrep = effectsPlan.startSidecarPrep,
                )
                applyPlaybackTrackStartEffects(
                    track = trackStartWork.track,
                    coverArtUrl = trackStartWork.coverArtUrl,
                    effects = effectsPlan,
                    applier = PlaybackTrackStartEffectApplier(
                        clearShuffleSnapshot = { shuffledUpNextSnapshot = null },
                        clearRadioContinuation = {
                            radioQueueActive = false
                            radioRefilling = false
                            lastRadioRefillSeedId = null
                        },
                        clearInternetRadioNowPlaying = { nowPlayingStation = null },
                        resetStreamMetadata = { nowPlayingStreamMetadata = PlaybackStreamMetadata() },
                        setNowPlayingTrack = { startedTrack -> nowPlaying = startedTrack },
                        applyFavoriteState = { canFavorite, isFavorite ->
                            AndroidPlaybackNotificationControls.canFavorite = canFavorite
                            AndroidPlaybackNotificationControls.isFavorite = isFavorite
                        },
                        savePlaybackSession = { savePlaybackSessionThrottled(true) },
                        openNowPlaying = { nowPlayingOpen = true },
                        reportNowPlaying = reportNowPlaying,
                        refillRadioQueue = {
                            refillAndroidRadioIfNeeded(
                                scope = scope,
                                state = state,
                                queue = playbackQueue,
                                queueController = playbackQueueController,
                            )
                        },
                        loadRelatedTracks = loadRelatedTracks,
                        loadAudioTags = loadAudioTags,
                        loadLyrics = loadLyrics,
                        startAudioPrefetch = { startAudioPrefetch(sessionToken, activeProvider, playbackQueue) },
                        startSidecarPrep = { startSidecarPrep(sessionToken, activeProvider, playbackQueue) },
                        updateNotificationMetadata = { title, subtitle, cover ->
                            playbackEngine.updateNotificationMetadata(
                                title = title,
                                subtitle = subtitle,
                                coverArtUrl = cover,
                            )
                        },
                    ),
                )
                playbackEngine.play(
                    scope = scope,
                    request = trackStartWork.request,
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

fun playAndroidInternetRadioStation(
    scope: CoroutineScope,
    state: AndroidAppState,
    settingsStore: AndroidSettingsStore,
    playbackEngine: AndroidPlaybackEngine,
    playbackQueueController: PlaybackQueueController,
    station: InternetRadioStation,
    savePlaybackSessionThrottled: (force: Boolean) -> Unit,
    handlePlaybackProgressChanged: (Long, PlaybackProgress) -> Unit,
) {
    val sessionToken = beginAndroidPlaybackSession(
        state = state,
        playbackQueueController = playbackQueueController,
    )
    val plan = planInternetRadioStart(
        station = station,
        recentStations = state.homeState.recentInternetRadioStations,
        recentSavedStations = settingsStore.loadRecentInternetRadioStations(),
    )
    applyInternetRadioStart(
        plan = plan,
        applier = InternetRadioStartApplier(
            saveRecentStations = settingsStore::saveRecentInternetRadioStations,
            setRecentStations = { recentStations ->
                state.homeState = state.homeState.copy(recentInternetRadioStations = recentStations)
            },
            clearRadioContinuation = {
                state.radioQueueActive = false
                state.radioRefilling = false
                state.lastRadioRefillSeedId = null
            },
            clearShuffleSnapshot = { state.shuffledUpNextSnapshot = null },
            clearPlaybackQueue = { playbackQueueController.clear() },
            setNowPlayingTrack = { track -> state.nowPlaying = track },
            applyFavoriteState = { canFavorite, isFavorite ->
                AndroidPlaybackNotificationControls.canFavorite = canFavorite
                AndroidPlaybackNotificationControls.isFavorite = isFavorite
            },
            setNowPlayingStation = { startedStation -> state.nowPlayingStation = startedStation },
            setStreamMetadata = { metadata -> state.nowPlayingStreamMetadata = metadata },
            setPlaybackProgress = { progress -> state.playbackProgress = progress },
            setPlaybackQueue = { queue -> state.playbackQueue = queue },
            setStatus = { status -> state.status = status },
            savePlaybackSession = { savePlaybackSessionThrottled(true) },
            openNowPlaying = { state.nowPlayingOpen = true },
            updateNotificationMetadata = { title, subtitle, coverArtUrl ->
                playbackEngine.updateNotificationMetadata(
                    title = title,
                    subtitle = subtitle,
                    coverArtUrl = coverArtUrl,
                )
            },
        ),
    )
    playbackEngine.applyTlsSettings(state.activeTlsSettings)
    scope.launch {
        runCatching {
            resolveInternetRadioStreamUrl(station.streamUrl.trim())
        }.onSuccess { streamUrl ->
            val requestPlan = planInternetRadioPlaybackRequest(
                startPlan = plan,
                streamUrl = streamUrl,
                replayGainMode = state.playbackSettings.replayGainMode,
            )
            playbackEngine.play(
                scope = scope,
                request = requestPlan.request,
                onStateChanged = { playbackState ->
                    state.playbackState = playbackState
                    if (playbackState is PlaybackState.Error) {
                        state.status = playbackState.message
                    }
                },
                onProgressChanged = { progress -> handlePlaybackProgressChanged(sessionToken, progress) },
                onMetadataChanged = { metadata ->
                    applyInternetRadioMetadataUpdate(
                        plan = planInternetRadioMetadataUpdate(
                            station = station,
                            metadata = metadata,
                        ),
                        applier = InternetRadioMetadataUpdateApplier(
                            setStreamMetadata = { streamMetadata -> state.nowPlayingStreamMetadata = streamMetadata },
                            updateNotificationMetadata = { title, subtitle, coverArtUrl ->
                                playbackEngine.updateNotificationMetadata(
                                    title = title,
                                    subtitle = subtitle,
                                    coverArtUrl = coverArtUrl,
                                )
                            },
                        ),
                    )
                },
            )
        }.onFailure { error ->
            state.status = error.message ?: "Radio stream failed."
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
