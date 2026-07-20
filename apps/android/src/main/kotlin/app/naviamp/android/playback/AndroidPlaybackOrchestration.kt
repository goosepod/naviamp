package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidPlaybackForegroundService
import app.naviamp.android.playback.AndroidPlaybackEngine
import app.naviamp.android.playback.AndroidPlaybackNotificationControls
import app.naviamp.app.NaviampPlaybackQueueCoordinator
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
import app.naviamp.domain.playback.applyPlaybackProgressEffects
import app.naviamp.domain.playback.PlaybackProgressEffectApplier
import app.naviamp.domain.playback.fallbackPlaybackUrl
import app.naviamp.domain.playback.planPlaylistTrackStartWork
import app.naviamp.domain.playback.planPlaybackProgressUpdate
import app.naviamp.domain.playback.planPlaybackStart
import app.naviamp.domain.playback.planPlaybackTrackStart
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.radio.InternetRadioStartApplier
import app.naviamp.domain.radio.InternetRadioMetadataUpdateApplier
import app.naviamp.domain.radio.applyInternetRadioStart
import app.naviamp.domain.radio.applyInternetRadioPlaybackState
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
    queueCoordinator: NaviampPlaybackQueueCoordinator,
    track: Track,
    queue: List<Track>? = null,
    selectedQueue: PlaybackQueue? = null,
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
    maybeReportPlaybackState: (PlaybackState, PlaybackProgress) -> Unit,
    playAdjacentTrack: (Int, Boolean) -> Unit,
    coverArtUrl: (Track, NavidromeProvider?) -> String? = { item, provider -> item.coverArtUrl(provider) },
) {
    android.util.Log.i("NaviampBass", "playTrack requested id=${track.id.value} title=${track.title}")
    val activeProvider = state.provider
    scope.launch {
        with(state) {
            status = "Loading ${track.title}..."
            val streamQuality = currentStreamQuality()
            val audioSourcePlan = resolvePlaybackAudioSource(
                sourceId = activeSourceId,
                track = track,
                quality = streamQuality,
                audioCachingEnabled = true,
                downloadedTrackPlayback = playbackSettings.downloadedTrackPlayback,
                startPositionSeconds = startPositionSeconds,
                audioAssets = audioAssets,
            )
            if (activeProvider == null && !audioSourcePlan.hasLocalAudio) {
                status = "Connect before playing a track."
                return@with
            }
            val occurrenceQueue = selectedQueue ?: playbackQueue.takeIf { queueState ->
                queue == queueState.tracks && queueState.current?.id == track.id
            }
            val startPlan = planPlaybackStart(
                track = track,
                requestedQueue = occurrenceQueue?.tracks ?: queue,
                requestedQueueIndex = occurrenceQueue?.currentIndex,
                activeQueue = activeQueue(),
                quality = streamQuality,
                startPositionSeconds = startPositionSeconds,
                hasLocalAudio = audioSourcePlan.hasLocalAudio,
            )
            runCatching {
                audioSourcePlan.playbackStreamUrl(
                    providerStreamUrl = { target ->
                        requireNotNull(activeProvider) { "Connect before playing a track." }
                            .streamUrl(target.providerStreamRequest)
                    },
                )
            }.onSuccess { streamUrl ->
                playbackEngine.applyTlsSettings(activeTlsSettings)
                val selectedQueueState = occurrenceQueue?.takeIf { queueState ->
                    queueState.current?.id == track.id &&
                        queueState.currentIndex == startPlan.queueIndex
                }
                if (selectedQueueState != null) {
                    val update = queueCoordinator.replaceQueue(selectedQueueState)
                    playbackQueueController.replaceQueue(
                        update.queue,
                        clearPreparedNext = update.clearPreparedNext,
                    )
                } else {
                    val update = queueCoordinator.startQueue(
                        tracks = startPlan.queue,
                        index = startPlan.queueIndex,
                    )
                    if (!update.changed) return@onSuccess
                    playbackQueueController.start(
                        tracks = update.queue.tracks,
                        index = update.queue.currentIndex,
                    )
                }
                val effectsPlan = planPlaybackTrackStart(
                    previousTrack = nowPlaying,
                    track = track,
                    openNowPlaying = openNowPlaying,
                    nowPlayingOpen = nowPlayingOpen,
                    lyricsVisible = lyricsVisible,
                    supportsTrackFavorites = activeProvider?.capabilities?.supportsTrackFavorites
                        ?: (activeSourceId != null),
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
                    pendingSeekIssuedAtMillis = AndroidSystemClock.nowEpochMillis()
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
                    fallbackStreamUrl = audioSourcePlan.fallbackPlaybackUrl(),
                    replayGainMode = playbackSettings.replayGainMode,
                    replayGainPreampDb = playbackSettings.replayGainPreampDb,
                    replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                    supportsReplayGain = playbackEngine.supportsReplayGain,
                    engineStartPositionSeconds = effectsPlan.engineStartPositionSeconds,
                    coverArtUrl = coverArtUrl(track, activeProvider),
                    startAudioPrefetch = effectsPlan.startAudioPrefetch,
                    startSidecarPrep = effectsPlan.startSidecarPrep,
                )
                applyPlaybackTrackStartEffects(
                    track = trackStartWork.track,
                    coverArtUrl = trackStartWork.coverArtUrl,
                    effects = effectsPlan,
                    applier = PlaybackTrackStartEffectApplier(
                        clearShuffleSnapshot = { shuffledUpNextSnapshot = null },
                        clearRadioContinuation = radioContinuation::stop,
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
                        loadRelatedTracks = { relatedTrack ->
                            if (activeProvider != null) loadRelatedTracks(relatedTrack)
                        },
                        loadAudioTags = loadAudioTags,
                        loadLyrics = loadLyrics,
                        startAudioPrefetch = {
                            activeProvider?.let { provider -> startAudioPrefetch(sessionToken, provider, playbackQueue) }
                        },
                        startSidecarPrep = {
                            activeProvider?.let { provider -> startSidecarPrep(sessionToken, provider, playbackQueue) }
                        },
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
                        state.sharedLivePlaybackController.applyPlaybackStateChange(
                            playbackState = playbackState,
                            progress = state.playbackProgress,
                            report = maybeReportPlaybackState,
                        )
                        when (playbackState) {
                            PlaybackState.Finished -> effectsPlan.finishedAdjacentOffset?.let { offset ->
                                playAdjacentTrack(offset, true)
                            }
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
    queueCoordinator: NaviampPlaybackQueueCoordinator,
    station: InternetRadioStation,
    savePlaybackSessionThrottled: (force: Boolean) -> Unit,
    handlePlaybackProgressChanged: (Long, PlaybackProgress) -> Unit,
    onSyncedSettingsChanged: () -> Unit = {},
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
            saveRecentStations = { stations ->
                settingsStore.saveRecentInternetRadioStations(stations)
                onSyncedSettingsChanged()
            },
            setRecentStations = { recentStations ->
                state.homeState = state.homeState.copy(recentInternetRadioStations = recentStations)
            },
            clearRadioContinuation = state.radioContinuation::stop,
            clearShuffleSnapshot = { state.shuffledUpNextSnapshot = null },
            clearPlaybackQueue = {
                queueCoordinator.clearQueue()
                playbackQueueController.clear()
            },
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
                    applyInternetRadioPlaybackState(playbackState, { state.playbackState = it }) { state.status = it }
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
    maybeReportPlaybackState: (PlaybackState, PlaybackProgress) -> Unit,
    prepareNextIfNeeded: (Long, PlaybackProgress) -> Unit,
) {
    with(state) {
        val nowMillis = AndroidSystemClock.nowEpochMillis()
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
        val result = applyPlaybackProgressEffects(
            plan = plan,
            applier = PlaybackProgressEffectApplier(
                clearPendingSeek = {
                    pendingSeekPositionSeconds = null
                    pendingSeekIssuedAtMillis = null
                },
                clearPendingRestoreStart = { pendingRestoreStartPositionSeconds = null },
                resetProgress = { playbackProgress = PlaybackProgress.Unknown },
                reportPlaybackProgress = { mergedProgress ->
                    maybeReportPlaybackState(PlaybackState.Playing, mergedProgress)
                },
                updateProgress = { mergedProgress -> playbackProgress = mergedProgress },
            ),
        )
        if (result.ignored) return
        if (result.resetToUnknown) {
            AndroidPlaybackNotificationControls.positionMillis = null
            AndroidPlaybackNotificationControls.durationMillis = null
            AndroidPlaybackForegroundService.updateProgress(context, null, null)
            return
        }
        playbackProgress = result.progress ?: return
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
