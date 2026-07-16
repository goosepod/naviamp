package app.naviamp.android.playback

import android.content.Context
import android.util.Log
import app.naviamp.android.AndroidPlaybackAudioAssets
import app.naviamp.android.AndroidGaplessPrepareWindowSeconds
import app.naviamp.android.AndroidPlaybackSessionSaveIntervalMillis
import app.naviamp.android.AndroidSettingsStore
import app.naviamp.android.AndroidStorageDependencies
import app.naviamp.android.PlaybackStateReportIntervalMillis
import app.naviamp.android.toPlaybackReportState
import app.naviamp.android.withAndroidPendingActions
import app.naviamp.domain.Album
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.isInternetRadioTrack
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackQueueController
import app.naviamp.domain.playback.PlaybackQueueFinishedCommand
import app.naviamp.domain.playback.PlaybackQueueManager
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PreparedNextPlaybackCoordinator
import app.naviamp.domain.playback.PreparedNextPlaybackSettings
import app.naviamp.domain.playback.PreparedNextPlaybackWork
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.canReportPlaybackTrack
import app.naviamp.domain.playback.fallbackPlaybackUrl
import app.naviamp.domain.playback.hasPendingSeekReachedTarget
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.playbackRequestForTrack
import app.naviamp.domain.playback.planAudioPrefetchWork
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.runAudioPrefetch
import app.naviamp.domain.playback.preparedNextPlaybackWork
import app.naviamp.domain.playback.shouldIgnoreProgressForPendingSeek
import app.naviamp.domain.provider.PlaybackReportState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.adjacentTrackSession
import app.naviamp.domain.settings.PlaybackSessionRestorePlan
import app.naviamp.domain.settings.planPlaybackSessionRestore
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.withPlaybackPosition
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.toNavidromeConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidServicePlaybackRuntimeController(
    private val context: Context,
    private val storage: () -> AndroidStorageDependencies,
    private val queueController: PlaybackQueueController,
    private val currentQueue: () -> List<Track>,
    private val currentQueueIndex: () -> Int,
    private val syncQueue: (PlaybackQueue) -> Unit,
    private val repeatMode: () -> RepeatMode,
    private val currentMetadata: () -> AndroidPlaybackNotificationMetadata,
    private val setCurrentMetadata: (AndroidPlaybackNotificationMetadata) -> Unit,
    private val updateMediaSession: (AndroidPlaybackNotificationMetadata) -> Unit,
    private val updateMediaSessionPlaybackState: () -> Unit,
    private val loadCoverArt: (String, AndroidPlaybackNotificationMetadata) -> Unit,
    private val playTrackQueue: (PlaybackSessionRepository, String, List<Track>, Int) -> Unit,
    private val playInternetRadioStation: (MediaSourceRepository, PlaybackSessionRepository, String, InternetRadioStation) -> Unit,
) {
    private val queueManager = PlaybackQueueManager()
    private var serviceOwnedPlayback = false
    private var serviceMediaSource: SavedMediaSource? = null
    private var lastServiceSessionSaveAtMillis = 0L
    private var lastServicePlaybackState: PlaybackState? = null
    private var pendingServiceSeekPositionSeconds: Double? = null
    private var pendingServiceSeekAtMillis = 0L
    private var serviceAudioPrefetchJob: Job? = null
    private var servicePreparedNextJob: Job? = null
    private var servicePlaybackSettings = PlaybackSettings()
    private var servicePlaybackSessionToken: Long = 0L
    private var lastServicePlaybackStateReportSessionToken: Long? = null
    private var lastServicePlaybackStateReportState: PlaybackReportState? = null
    private var lastServicePlaybackStateReportAtMillis: Long = 0L

    init {
        queueController.setPreparedNextInvalidationHandler {
            servicePreparedNextJob?.cancel()
            servicePreparedNextJob = null
            (AndroidPlaybackRuntime.get(context).playbackEngine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        }
    }

    fun ownsPlayback(): Boolean = serviceOwnedPlayback

    fun handleAutoPlayPause() {
        if (AndroidPlaybackNotificationControls.isPlaying) {
            pause("Auto pause")
            return
        }
        playSavedSession()
    }

    fun pause(reason: String) {
        if (!serviceOwnedPlayback) return
        Log.i("NaviampAutoCommand", "Pausing service-owned playback: $reason")
        runCatching { AndroidPlaybackRuntime.get(context).playbackEngine.pause() }
        AndroidPlaybackNotificationControls.isPlaying = false
        updateMediaSessionPlaybackState()
    }

    fun stop(reason: String) {
        Log.i("NaviampAutoCommand", "Stopping service-owned playback: $reason")
        runCatching { AndroidPlaybackRuntime.get(context).playbackEngine.stop() }
        AndroidPlaybackNotificationControls.isPlaying = false
        serviceOwnedPlayback = false
        serviceAudioPrefetchJob?.cancel()
        serviceAudioPrefetchJob = null
        servicePreparedNextJob?.cancel()
        servicePreparedNextJob = null
        queueController.clearPreparedNext()
        updateMediaSessionPlaybackState()
    }

    fun stopForUserRequest(reason: String) {
        AndroidPlaybackNotificationControls.onStop?.invoke()
            ?: stop(reason)
    }

    fun seek(positionMillis: Long) {
        val currentPositionSeconds = AndroidPlaybackNotificationControls.positionMillis
            ?.let { it / 1_000.0 }
            ?: 0.0
        if (positionMillis == 0L && currentPositionSeconds > ServiceIgnoreZeroSeekAfterSeconds) {
            Log.i(
                "NaviampAutoCommand",
                "Ignoring service zero seek while currentPosition=$currentPositionSeconds",
            )
            updateMediaSessionPlaybackState()
            return
        }
        val positionSeconds = (positionMillis.coerceAtLeast(0L) / 1_000.0)
        Log.i("NaviampAutoCommand", "Service seek requested seconds=$positionSeconds")
        pendingServiceSeekPositionSeconds = positionSeconds
        pendingServiceSeekAtMillis = System.currentTimeMillis()
        AndroidPlaybackNotificationControls.positionMillis = positionMillis.coerceAtLeast(0L)
        AndroidPlaybackRuntime.get(context).playbackEngine.seek(positionSeconds)
        savePlaybackPosition(positionSeconds)
        updateMediaSessionPlaybackState()
    }

    fun playSavedSessionAdjacent(delta: Int) {
        val storage = storage()
        val sourceId = serviceMediaSource(storage)?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        val nextSession = session.adjacentTrackSession(delta) ?: return
        storage.savePlaybackSession(sourceId = sourceId, session = nextSession)
        playSavedSession(nextSession)
    }

    fun playServiceOwnedAdjacent(delta: Int): Boolean {
        if (!serviceOwnedPlayback) return false
        val update = queueManager.selectAdjacent(
            queue = PlaybackQueue(currentQueue(), currentQueueIndex()),
            offset = delta,
            repeatMode = repeatMode(),
        )
        if (!update.changed) {
            Log.i(
                "NaviampAutoCommand",
                "Service-owned queue has no adjacent track delta=$delta index=${currentQueueIndex()} size=${currentQueue().size}",
            )
            AndroidPlaybackNotificationControls.isPlaying = false
            updateMediaSessionPlaybackState()
            return true
        }
        val storage = storage()
        val sourceId = serviceMediaSource(storage)?.id ?: return false
        Log.i(
            "NaviampAutoCommand",
            "Service-owned queue advancing delta=$delta from=${currentQueueIndex()} to=${update.queue.currentIndex} size=${update.queue.tracks.size}",
        )
        val selectingPreparedNext =
            delta == 1 && queueController.preparedNextIndex == update.queue.currentIndex
        queueController.replaceQueue(
            queue = update.queue,
            clearPreparedNext = !selectingPreparedNext,
        )
        playTrackQueue(storage, sourceId, update.queue.tracks, update.queue.currentIndex)
        return true
    }

    fun playSavedSession(existingSession: PlaybackSessionSettings? = null) {
        val storage = storage()
        val source = serviceMediaSource(storage) ?: return
        val session = existingSession ?: storage.loadPlaybackSession(source.id) ?: return
        val restorePlan = planPlaybackSessionRestore(session)
        if (restorePlan is PlaybackSessionRestorePlan.InternetRadio) {
            playInternetRadioStation(storage, storage, source.id, restorePlan.station)
            return
        }
        if (restorePlan !is PlaybackSessionRestorePlan.TrackSession) return
        val track = restorePlan.currentTrack
        syncQueue(restorePlan.playbackQueue)
        val connection = source.toNavidromeConnection()
        val provider = NavidromeProvider(connection)
        val runtime = AndroidPlaybackRuntime.get(context)
        val settingsStore = AndroidSettingsStore(context)
        val playbackSettings = settingsStore
            .loadPlaybackSettings()
            .effectiveForEngine(runtime.playbackEngine)
        servicePlaybackSettings = playbackSettings
        val cacheSettings = settingsStore.loadCacheSettings()
        val audioAssets = AndroidPlaybackAudioAssets(storage, storage)
        val quality = StreamQuality.Original
        val startPositionSeconds = restorePlan.restoredStartPositionSeconds?.takeIf { it > 0.0 }
        val coverArtUrl = (track.coverArtId ?: track.albumId?.value)?.let(provider::coverArtUrl)

        runtime.playbackEngine.applyTlsSettings(connection.tlsSettings)
        (runtime.playbackEngine as? QueueAwarePlaybackEngine)
            ?.setCrossfadeDuration(playbackSettings.crossfadeDurationSeconds)
        AndroidPlaybackNotificationControls.canFavorite = provider.capabilities.supportsTrackFavorites
        AndroidPlaybackNotificationControls.isFavorite = track.favoritedAtIso8601 != null
        AndroidPlaybackNotificationControls.isPlaying = true
        markStarted()
        AndroidPlaybackNotificationControls.positionMillis = startPositionSeconds
            ?.let { (it * 1_000.0).toLong() }
            ?: 0L
        AndroidPlaybackNotificationControls.durationMillis = track.durationSeconds
            ?.takeIf { it > 0 }
            ?.let { it * 1_000L }
        val metadata = AndroidPlaybackNotificationMetadata(
            title = track.title,
            subtitle = track.artistName,
            coverArtUrl = coverArtUrl,
        )
        setCurrentMetadata(metadata)
        metadata.coverArtUrl?.let { loadCoverArt(it, metadata) }
        updateMediaSession(metadata)
        Log.i(
            "NaviampAutoCommand",
            "Service playing saved session source=${source.id} title=${track.title} position=$startPositionSeconds",
        )
        reportServiceNowPlaying(storage, source.id, provider, track)

        runtime.scope.launch {
            runCatching {
                val audioSourcePlan = resolvePlaybackAudioSource(
                    sourceId = source.id,
                    track = track,
                    quality = quality,
                    audioCachingEnabled = true,
                    downloadedTrackPlayback = playbackSettings.downloadedTrackPlayback,
                    startPositionSeconds = startPositionSeconds,
                    audioAssets = audioAssets,
                )
                val streamUrl = audioSourcePlan.playbackStreamUrl(
                    providerStreamUrl = { target -> provider.streamUrl(target.providerStreamRequest) },
                )
                Triple(
                    streamUrl,
                    audioSourcePlan.target.engineStartPositionSeconds,
                    audioSourcePlan.fallbackPlaybackUrl(),
                )
            }.onSuccess { playbackTarget ->
                runtime.playbackEngine.updateNotificationMetadata(
                    title = track.title,
                    subtitle = track.artistName,
                    coverArtUrl = coverArtUrl,
                )
                startServiceAudioPrefetch(
                    runtime = runtime,
                    storage = storage,
                    sourceId = source.id,
                    provider = provider,
                    audioAssets = audioAssets,
                    queue = restorePlan.playbackQueue,
                    quality = quality,
                    enabled = cacheSettings.audioCachingEnabled,
                    configuredDepth = cacheSettings.audioPrefetchDepth,
                )
                runtime.playbackEngine.play(
                    scope = runtime.scope,
                    request = playbackRequestForTrack(
                        track = track,
                        url = playbackTarget.first,
                        fallbackUrl = playbackTarget.third,
                        replayGainMode = playbackSettings.replayGainMode,
                        replayGainPreampDb = playbackSettings.replayGainPreampDb,
                        supportsReplayGain = runtime.playbackEngine.supportsReplayGain,
                        replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                        startPositionSeconds = playbackTarget.second,
                    ),
                    onStateChanged = { state -> handlePlaybackStateChanged(state, handleFinished = true) },
                    onProgressChanged = { progress -> handlePlaybackProgress(storage, source.id, session, progress) },
                )
            }.onFailure { error ->
                Log.w("NaviampAutoCommand", "Service saved-session playback failed", error)
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
        }
    }

    fun markStarted() {
        serviceOwnedPlayback = true
        servicePlaybackSessionToken = System.nanoTime()
        lastServiceSessionSaveAtMillis = 0L
        lastServicePlaybackState = null
        serviceAudioPrefetchJob?.cancel()
        serviceAudioPrefetchJob = null
        servicePreparedNextJob?.cancel()
        servicePreparedNextJob = null
        queueController.clearPreparedNext()
    }

    fun handlePlaybackStateChanged(state: PlaybackState, handleFinished: Boolean = false) {
        if (state != lastServicePlaybackState) {
            lastServicePlaybackState = state
            AndroidPlaybackNotificationControls.isPlaying = state == PlaybackState.Playing
            updateMediaSessionPlaybackState()
            maybeReportServicePlaybackState(state)
        }
        if (handleFinished && state == PlaybackState.Finished) {
            handleTrackFinished()
        }
    }

    fun handlePlaybackProgress(
        playbackSessionRepository: PlaybackSessionRepository,
        sourceId: String,
        session: PlaybackSessionSettings,
        progress: PlaybackProgress,
    ) {
        val now = System.currentTimeMillis()
        val progressPositionSeconds = progress.positionSeconds
        if (progressPositionSeconds == null && progress.durationSeconds == null) return
        val pendingSeekPosition = pendingServiceSeekPositionSeconds
        if (
            shouldIgnoreProgressForPendingSeek(
                pendingSeekPositionSeconds = pendingSeekPosition,
                pendingSeekIssuedAtMillis = pendingServiceSeekAtMillis,
                incomingPositionSeconds = progressPositionSeconds,
                nowMillis = now,
                toleranceSeconds = ServiceSeekToleranceSeconds,
                staleWindowMillis = ServiceSeekStaleProgressWindowMillis,
            )
        ) {
            return
        }
        if (
            hasPendingSeekReachedTarget(
                pendingSeekPositionSeconds = pendingSeekPosition,
                incomingPositionSeconds = progressPositionSeconds,
                toleranceSeconds = ServiceSeekToleranceSeconds,
            )
        ) {
            pendingServiceSeekPositionSeconds = null
        }
        val previousPositionMillis = AndroidPlaybackNotificationControls.positionMillis
        val positionMillis = progressPositionSeconds
            ?.takeIf { it >= 0.0 }
            ?.let { (it * 1_000.0).toLong() }
        val previousDurationMillis = AndroidPlaybackNotificationControls.durationMillis
        val durationMillis = progress.durationSeconds
            ?.takeIf { it > 0.0 }
            ?.let { (it * 1_000.0).toLong() }
        AndroidPlaybackNotificationControls.positionMillis = positionMillis ?: previousPositionMillis
        AndroidPlaybackNotificationControls.durationMillis = durationMillis ?: previousDurationMillis
        val durationChanged = AndroidPlaybackNotificationControls.durationMillis != previousDurationMillis
        val positionChanged = AndroidPlaybackNotificationControls.positionMillis != previousPositionMillis
        if (durationChanged) {
            updateMediaSession(currentMetadata())
        } else if (!AndroidPlaybackNotificationControls.isPlaying && positionChanged) {
            updateMediaSessionPlaybackState()
        }
        if (now - lastServiceSessionSaveAtMillis >= ServicePlaybackSessionSaveIntervalMillis) {
            lastServiceSessionSaveAtMillis = now
            playbackSessionRepository.savePlaybackSession(
                sourceId = sourceId,
                session = session.withPlaybackPosition(progressPositionSeconds),
            )
        }
        prepareNextIfNeeded(progress)
        maybeReportServicePlaybackState(PlaybackState.Playing, progress)
    }

    private fun prepareNextIfNeeded(progress: PlaybackProgress) {
        val runtime = AndroidPlaybackRuntime.get(context)
        val queueAwareEngine = runtime.playbackEngine as? QueueAwarePlaybackEngine ?: return
        val playbackSettings = servicePlaybackSettings
        val queue = PlaybackQueue(currentQueue(), currentQueueIndex())
        val work = planAndroidServicePreparedNextPlayback(
            queue = queue,
            repeatMode = repeatMode(),
            progress = progress,
            preparedNextIndex = queueController.preparedNextIndex,
            playbackSettings = playbackSettings,
            supportsGapless = runtime.playbackEngine.supportsGapless,
            supportsCrossfade = runtime.playbackEngine.supportsCrossfade,
        ) ?: return
        val storage = storage()
        val source = serviceMediaSource(storage) ?: return
        val provider = NavidromeProvider(source.toNavidromeConnection())
        val sessionToken = servicePlaybackSessionToken
        val coordinator = PreparedNextPlaybackCoordinator(
            provider = { provider },
            sourceId = { source.id },
            quality = { StreamQuality.Original },
            audioCachingEnabled = { true },
            audioAssets = AndroidPlaybackAudioAssets(storage, storage),
            downloadedTrackPlayback = { playbackSettings.downloadedTrackPlayback },
            replayGainMode = { playbackSettings.replayGainMode },
            replayGainPreampDb = { playbackSettings.replayGainPreampDb },
            supportsReplayGain = { runtime.playbackEngine.supportsReplayGain },
            replayGainForTrack = { track, _ ->
                track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) }
            },
        )
        queueController.markPreparedNext(work.markPreparedNextIndex)
        servicePreparedNextJob = runtime.scope.launch {
            runCatching { coordinator.request(work) }
                .onSuccess { prepared ->
                    if (prepared == null) {
                        if (sessionToken == servicePlaybackSessionToken) {
                            queueController.clearPreparedNext()
                        }
                        return@onSuccess
                    }
                    if (sessionToken != servicePlaybackSessionToken || !serviceOwnedPlayback) {
                        return@onSuccess
                    }
                    if (queueController.preparedNextIndex != work.markPreparedNextIndex) return@onSuccess
                    if (currentQueue().getOrNull(work.markPreparedNextIndex)?.id != work.plan.track.id) return@onSuccess
                    queueAwareEngine.prepareNext(prepared.request)
                }
                .onFailure { error ->
                    if (sessionToken == servicePlaybackSessionToken) {
                        queueController.clearPreparedNext()
                    }
                    Log.w("NaviampAutoCommand", "Could not prepare next service-owned track", error)
                }
        }
    }

    private fun reportServiceNowPlaying(
        storage: AndroidStorageDependencies,
        sourceId: String,
        provider: NavidromeProvider,
        track: Track,
    ) {
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = provider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        AndroidPlaybackRuntime.get(context).scope.launch {
            withContext(Dispatchers.IO) {
                provider
                    .withAndroidPendingActions(sourceId, storage)
                    .reportNowPlaying(track.id)
            }
        }
    }

    private fun maybeReportServicePlaybackState(
        playbackState: PlaybackState,
        progress: PlaybackProgress = PlaybackProgress(
            positionSeconds = AndroidPlaybackNotificationControls.positionMillis?.let { it / 1_000.0 },
            durationSeconds = AndroidPlaybackNotificationControls.durationMillis?.let { it / 1_000.0 },
        ),
    ) {
        val reportState = playbackState.toPlaybackReportState() ?: return
        val track = currentQueue().getOrNull(currentQueueIndex()) ?: return
        val storage = storage()
        val source = serviceMediaSource(storage) ?: return
        val provider = NavidromeProvider(source.toNavidromeConnection())
        if (
            !canReportPlaybackTrack(
                supportsPlayReporting = provider.capabilities.supportsPlayReporting,
                isInternetRadioTrack = track.isInternetRadioTrack(),
            )
        ) {
            return
        }
        val activeSessionToken = servicePlaybackSessionToken
        val nowMillis = System.currentTimeMillis()
        val sameSession = lastServicePlaybackStateReportSessionToken == activeSessionToken
        val sameState = lastServicePlaybackStateReportState == reportState
        val shouldReport = !sameSession ||
            !sameState ||
            (reportState == PlaybackReportState.Playing &&
                nowMillis - lastServicePlaybackStateReportAtMillis >= PlaybackStateReportIntervalMillis)
        if (!shouldReport) return

        lastServicePlaybackStateReportSessionToken = activeSessionToken
        lastServicePlaybackStateReportState = reportState
        lastServicePlaybackStateReportAtMillis = nowMillis
        AndroidPlaybackRuntime.get(context).scope.launch {
            withContext(Dispatchers.IO) {
                provider
                    .withAndroidPendingActions(source.id, storage)
                    .reportPlaybackState(
                        trackId = track.id,
                        state = reportState,
                        positionSeconds = progress.positionSeconds,
                    )
            }
        }
    }

    private fun savePlaybackPosition(positionSeconds: Double) {
        val storage = storage()
        val sourceId = serviceMediaSource(storage)?.id ?: return
        val session = storage.loadPlaybackSession(sourceId) ?: return
        storage.savePlaybackSession(
            sourceId = sourceId,
            session = session.withPlaybackPosition(positionSeconds),
        )
    }

    private fun handleTrackFinished() {
        val removePlayedTracksFromQueue = AndroidSettingsStore(context)
            .loadPlaybackSettings()
            .removePlayedTracksFromQueue
        val update = queueManager.finishCurrentTrack(
            queue = PlaybackQueue(currentQueue(), currentQueueIndex()),
            repeatMode = repeatMode(),
            removePlayedTracksFromQueue = removePlayedTracksFromQueue,
        )
        when (update.command) {
            PlaybackQueueFinishedCommand.None -> {
                if (update.queue != PlaybackQueue(currentQueue(), currentQueueIndex())) {
                    queueController.replaceQueue(update.queue)
                    syncQueue(update.queue)
                }
                AndroidPlaybackNotificationControls.isPlaying = false
                updateMediaSessionPlaybackState()
            }
            PlaybackQueueFinishedCommand.ReplayCurrent,
            PlaybackQueueFinishedCommand.PlayNext,
            -> {
                val selectingPreparedNext =
                    update.command == PlaybackQueueFinishedCommand.PlayNext &&
                        queueController.preparedNextIndex == update.queue.currentIndex
                queueController.replaceQueue(
                    queue = update.queue,
                    clearPreparedNext = !selectingPreparedNext,
                )
                val storage = storage()
                val sourceId = serviceMediaSource(storage)?.id ?: return
                playTrackQueue(storage, sourceId, update.queue.tracks, update.queue.currentIndex)
            }
        }
    }

    private fun serviceMediaSource(storage: AndroidStorageDependencies): SavedMediaSource? =
        serviceMediaSource ?: storage.latestNavidromeSource()?.also { serviceMediaSource = it }

    private fun startServiceAudioPrefetch(
        runtime: AndroidPlaybackRuntime,
        storage: AndroidStorageDependencies,
        sourceId: String,
        provider: NavidromeProvider,
        audioAssets: AndroidPlaybackAudioAssets,
        queue: PlaybackQueue,
        quality: StreamQuality,
        enabled: Boolean,
        configuredDepth: Int,
    ) {
        val work = planAudioPrefetchWork(
            sourceId = sourceId,
            provider = provider,
            quality = quality,
            queue = queue,
            enabled = enabled,
            configuredDepth = configuredDepth,
            includeCurrentTrack = false,
        ) ?: return
        serviceAudioPrefetchJob?.cancel()
        serviceAudioPrefetchJob = runtime.scope.launch {
            runAudioPrefetch(
                stats = work.stats,
                tracks = work.tracks,
                isActive = { serviceOwnedPlayback },
                cacheAudio = { track ->
                    val resolved = resolvePlaybackAudioSource(
                        sourceId = sourceId,
                        track = track,
                        quality = quality,
                        audioCachingEnabled = true,
                        audioAssets = audioAssets,
                    )
                    resolved.localAudio ?: storage.cacheAudioTrack(sourceId, provider, track, quality)
                },
                warmCoverArt = { track ->
                    storage.warmServiceCoverArt(provider, track)
                },
                onTrackFailed = { track, error ->
                    Log.w("NaviampAutoCommand", "Could not prefetch Auto cached track=${track.id.value}", error)
                },
            )
        }
    }

    private companion object {
        const val ServicePlaybackSessionSaveIntervalMillis = AndroidPlaybackSessionSaveIntervalMillis
        const val ServiceSeekToleranceSeconds = 2.0
        const val ServiceSeekStaleProgressWindowMillis = 1_500L
        const val ServiceIgnoreZeroSeekAfterSeconds = 3.0
    }
}

internal fun planAndroidServicePreparedNextPlayback(
    queue: PlaybackQueue,
    repeatMode: RepeatMode,
    progress: PlaybackProgress,
    preparedNextIndex: Int?,
    playbackSettings: PlaybackSettings,
    supportsGapless: Boolean,
    supportsCrossfade: Boolean,
): PreparedNextPlaybackWork? {
    val nextQueueIndex = PlaybackQueueManager().nextPreparedQueueIndex(queue, repeatMode)
    return preparedNextPlaybackWork(
        queue = queue,
        progress = progress,
        nextQueueIndex = nextQueueIndex,
        preparedNextIndex = preparedNextIndex,
        settings = PreparedNextPlaybackSettings(
            gaplessEnabled = playbackSettings.gaplessEnabled,
            supportsGapless = supportsGapless,
            crossfadeDurationSeconds = playbackSettings.crossfadeDurationSeconds,
            supportsCrossfade = supportsCrossfade,
            gaplessPrepareWindowSeconds = AndroidGaplessPrepareWindowSeconds,
        ),
    )
}

private suspend fun AndroidStorageDependencies.warmServiceCoverArt(
    provider: NavidromeProvider,
    track: Track,
) {
    val coverArtId = track.coverArtId ?: track.albumId?.value ?: return
    val url = provider.coverArtUrl(coverArtId)
    imageBytes(url) {
        provider.bytes(url) ?: throw IllegalStateException("Could not download cover art.")
    }
}
