package app.naviamp.presentation

import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.domain.playback.PlaybackSidecarPrepResult
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackReplayGain
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackEngineDiagnostics
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainPlaybackEngine
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.SampleRateConverterPlaybackEngine
import app.naviamp.domain.playback.SampleRateMatchingPlaybackEngine
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.lyricsLoadingStatus
import app.naviamp.domain.playback.lyricsUnavailableStatus
import app.naviamp.domain.playback.emptyPlaybackAudioAssetRepository
import app.naviamp.domain.playback.planAudioPrefetchWork
import app.naviamp.domain.playback.planPrepareNextPlayback
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.planPlaylistTrackStartWork
import app.naviamp.domain.playback.playbackStreamUrl
import app.naviamp.domain.playback.resolvePlaybackAudioSource
import app.naviamp.domain.playback.runAudioPrefetch
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.AudioOutputDeviceMode
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.audio.AudioMetadataSidecarService
import app.naviamp.domain.lyrics.LyricsOffsetController
import app.naviamp.domain.lyrics.LyricsSidecarService
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.ui.radioArtworkNeedsTrackLookup
import app.naviamp.ui.radioTrackArtworkKey
import app.naviamp.ui.radioTrackArtworkQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one playback-engine adapter used by every Naviamp host.
 *
 * Hosts create a native [PlaybackEngine]. Core owns provider URL resolution, queue navigation,
 * prepared-next behavior, callbacks, and settings application through this class.
 */
class NaviampCorePlaybackEngineAdapter(
    private val scope: CoroutineScope,
    private val engine: PlaybackEngine,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val settings: () -> PlaybackSettings,
    private val isMobileData: () -> Boolean = { false },
    private val activeSourceId: () -> String? = { null },
    private val cacheSettings: () -> CacheSettings = {
        CacheSettings(audioCachingEnabled = false, audioPrefetchDepth = 0)
    },
    private val audioAssets: PlaybackAudioAssetRepository = emptyPlaybackAudioAssetRepository(),
    private val cacheAudio: suspend (
        sourceId: String,
        provider: app.naviamp.domain.provider.MediaProvider,
        track: app.naviamp.domain.Track,
        quality: app.naviamp.domain.StreamQuality,
    ) -> PlaybackLocalAudio? = { _, _, _, _ -> null },
    private val preparePrefetchedSidecars: suspend (
        sourceId: String,
        provider: app.naviamp.domain.provider.MediaProvider,
        track: app.naviamp.domain.Track,
        quality: app.naviamp.domain.StreamQuality,
        cachedAudio: PlaybackLocalAudio?,
    ) -> PlaybackSidecarPrepResult = { _, _, _, _, _ -> PlaybackSidecarPrepResult() },
) : NaviampCorePlaybackEffectPort, NaviampCoreInternetRadioPlaybackPort {
    override val capabilities = NaviampCorePlaybackCapabilities(
        engineName = engine.name,
        supportsPause = engine.supportsPause,
        supportsSeek = engine.supportsSeek,
        supportsSoftwareVolume = engine.supportsSoftwareVolume,
        supportsVisualizer = (engine as? VisualizerPlaybackEngine)?.supportsVisualizer == true,
    )
    override val playbackSource: PlaybackSource = PlaybackSource.ProviderStream

    private var observer: NaviampCorePlaybackObserver? = null
    private var queue = PlaybackQueue()
    private var repeatMode = RepeatMode.Off
    private var resolutionJob: Job? = null
    private var prefetchJob: Job? = null
    private var prefetchGeneration = 0L
    private var generation = 0L
    private var preparedForGeneration = -1L
    private var playbackState: PlaybackState = PlaybackState.Stopped
    private var restoredStartPositionSeconds: Double? = null
    private val externalStreamUrls = mutableMapOf<TrackId, String>()

    override fun attach(observer: NaviampCorePlaybackObserver) {
        this.observer = observer
    }

    override fun pause() = engine.pause()
    override fun resume() = engine.resume()

    override fun startOrRestore(): Boolean {
        queue.currentIndex.takeIf { it in queue.tracks.indices } ?: return false
        val position = restoredStartPositionSeconds
        restoredStartPositionSeconds = null
        startCurrent(position)
        return true
    }

    override fun seek(positionSeconds: Double) = engine.seek(positionSeconds)

    override fun replayCurrent(positionSeconds: Double) {
        startCurrent(positionSeconds)
    }

    override fun setVolume(percent: Int) = engine.setVolume(percent)

    override fun stop() {
        generation += 1
        resolutionJob?.cancel()
        resolutionJob = null
        cancelAudioPrefetch()
        (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        engine.stop()
        playbackState = PlaybackState.Stopped
    }

    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        this.queue = queue
        cancelAudioPrefetch()
        if (clearPreparedNext) {
            (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
            preparedForGeneration = -1L
        }
        if (
            playbackState == PlaybackState.Loading ||
            playbackState == PlaybackState.Playing ||
            playbackState == PlaybackState.Paused
        ) {
            providerSource.current()?.let { provider ->
                startAudioPrefetch(
                    provider = provider,
                    quality = settings().effectiveForEngine(engine).streamQualityForNetwork(isMobileData()),
                    requestGeneration = generation,
                )
            }
        }
    }

    override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
        cancelAudioPrefetch()
        this.queue = queue
        restoredStartPositionSeconds = startPositionSeconds
        (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        preparedForGeneration = -1L
    }

    override fun restoreInternetRadio(station: InternetRadioStation) {
        cancelAudioPrefetch()
        val track = internetRadioTrack(station)
        externalStreamUrls[track.id] = station.streamUrl
        queue = PlaybackQueue(listOf(track), 0)
        restoredStartPositionSeconds = null
    }

    override fun applyNavigation(command: PlaybackQueueNavigationCommand) {
        applyNavigation(command, preservePreparedTransition = false)
    }

    override fun applyAutomaticNavigation(command: PlaybackQueueNavigationCommand) {
        applyNavigation(command, preservePreparedTransition = true)
    }

    private fun applyNavigation(
        command: PlaybackQueueNavigationCommand,
        preservePreparedTransition: Boolean,
    ) {
        restoredStartPositionSeconds = null
        when (command) {
            PlaybackQueueNavigationCommand.Previous -> queue = queue.previous(repeatMode)
            PlaybackQueueNavigationCommand.Next -> queue = queue.next(repeatMode)
            is PlaybackQueueNavigationCommand.JumpTo -> queue = queue.jumpTo(command.index)
            PlaybackQueueNavigationCommand.RestartCurrent -> {
                engine.seek(0.0)
                return
            }
            PlaybackQueueNavigationCommand.None -> return
        }
        if (!preservePreparedTransition) clearPreparedCrossfade()
        cancelAudioPrefetch()
        startCurrent(startPositionSeconds = null)
    }

    override fun applyRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        if (index !in queue.tracks.indices) return
        cancelAudioPrefetch()
        restoredStartPositionSeconds = null
        this.queue = queue.jumpTo(index)
        clearPreparedCrossfade()
        startCurrent(startPositionSeconds = null)
    }

    private fun clearPreparedCrossfade() {
        if (settings().effectiveForEngine(engine).crossfadeDurationSeconds <= 0) return
        (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        preparedForGeneration = -1L
    }

    override suspend fun play(station: InternetRadioStation) {
        val track = internetRadioTrack(station)
        externalStreamUrls[track.id] = station.streamUrl
        playQueueSelection(PlaybackQueue(listOf(track), 0), 0)
    }

    override fun diagnostics(): List<Pair<String, String>> =
        (engine as? PlaybackEngineDiagnostics)?.statsRows().orEmpty()

    private fun startCurrent(startPositionSeconds: Double?) {
        val track = queue.current ?: return
        val requestGeneration = ++generation
        resolutionJob?.cancel()
        resolutionJob = scope.launch {
            val provider = providerSource.current()
            val externalStreamUrl = externalStreamUrls[track.id]
            if (provider == null && externalStreamUrl == null) {
                observer?.onStateChanged(PlaybackState.Error("Connect to Navidrome to play music."))
                return@launch
            }
            val playbackSettings = settings().effectiveForEngine(engine)
            val quality = playbackSettings.streamQualityForNetwork(isMobileData())
            val audioSource = externalStreamUrl?.let { null } ?: resolvePlaybackAudioSource(
                sourceId = activeSourceId(),
                track = track,
                quality = quality,
                audioCachingEnabled = cacheSettings().audioCachingEnabled,
                audioAssets = audioAssets,
                startPositionSeconds = startPositionSeconds,
            )
            val streamUrl = externalStreamUrl ?: runCatching {
                requireNotNull(audioSource).playbackStreamUrl { target ->
                    requireNotNull(provider).streamUrl(target.providerStreamRequest)
                }
            }.getOrElse { failure ->
                if (requestGeneration == generation) {
                    observer?.onStateChanged(
                        PlaybackState.Error(failure.message ?: "Could not resolve the audio stream."),
                    )
                }
                return@launch
            }
            if (requestGeneration != generation) return@launch

            val request = if (externalStreamUrl != null) {
                PlaybackRequest(
                    url = streamUrl,
                    mediaId = track.id.value,
                    replayGainMode = app.naviamp.domain.playback.ReplayGainMode.Off,
                )
            } else planPlaylistTrackStartWork(
                sessionId = requestGeneration,
                track = track,
                playbackSource = PlaybackSource.ProviderStream,
                streamUrl = streamUrl,
                replayGainMode = playbackSettings.replayGainMode,
                replayGainPreampDb = playbackSettings.replayGainPreampDb,
                replayGain = track.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
                supportsReplayGain = engine.supportsReplayGain,
                engineStartPositionSeconds = requireNotNull(audioSource).target.engineStartPositionSeconds,
                coverArtUrl = track.coverArtId?.let { requireNotNull(provider).coverArtUrl(it) },
            ).request
            if (provider != null) startAudioPrefetch(provider, quality, requestGeneration)
            engine.play(
                scope = scope,
                request = request,
                onStateChanged = { state ->
                    if (requestGeneration == generation) {
                        playbackState = state
                        observer?.onStateChanged(state)
                    }
                },
                onProgressChanged = { progress ->
                    if (requestGeneration == generation) {
                        observer?.onProgressChanged(progress)
                        observer?.onVisualizerFrameChanged(
                            (engine as? VisualizerPlaybackEngine)?.visualizerFrame(),
                        )
                        if (
                            provider != null &&
                            preparedForGeneration != requestGeneration &&
                            shouldPrepareNext(progress, playbackSettings)
                        ) {
                            preparedForGeneration = requestGeneration
                            scope.launch { prepareNext(provider, playbackSettings, requestGeneration) }
                        }
                    }
                },
                onMetadataChanged = { metadata ->
                    if (requestGeneration == generation) observer?.onMetadataChanged(metadata)
                },
            )
        }
    }

    private fun startAudioPrefetch(
        provider: app.naviamp.domain.provider.MediaProvider,
        quality: app.naviamp.domain.StreamQuality,
        requestGeneration: Long,
    ) {
        val activePrefetchGeneration = ++prefetchGeneration
        prefetchJob?.cancel()
        prefetchJob = null
        val configured = cacheSettings()
        val work = planAudioPrefetchWork(
            sourceId = activeSourceId(),
            provider = provider,
            quality = quality,
            queue = queue,
            enabled = configured.audioCachingEnabled,
            configuredDepth = configured.audioPrefetchDepth,
        ) ?: return
        prefetchJob = scope.launch {
            runAudioPrefetch(
                stats = work.stats,
                tracks = work.tracks,
                isActive = {
                    requestGeneration == generation && activePrefetchGeneration == prefetchGeneration
                },
                cacheAudio = { track ->
                    cacheAudio(work.sourceId, work.provider, track, work.quality)
                },
                prepareSidecars = { track, cachedAudio ->
                    preparePrefetchedSidecars(
                        work.sourceId,
                        work.provider,
                        track,
                        work.quality,
                        cachedAudio,
                    )
                },
            )
        }
    }

    private fun cancelAudioPrefetch() {
        prefetchGeneration += 1
        prefetchJob?.cancel()
        prefetchJob = null
    }

    private fun shouldPrepareNext(progress: app.naviamp.domain.playback.PlaybackProgress, settings: PlaybackSettings): Boolean {
        val nextIndex = queue.nextIndex(repeatMode, repeatTrack = true)
        return planPrepareNextPlayback(
            progress = progress,
            nextQueueIndex = nextIndex,
            alreadyPreparedNext = preparedForGeneration == generation,
            gaplessEnabled = settings.gaplessEnabled,
            supportsGapless = engine.supportsGapless,
            crossfadeDurationSeconds = settings.crossfadeDurationSeconds,
            supportsCrossfade = engine.supportsCrossfade,
            gaplessPrepareWindowSeconds = CoreGaplessPrepareWindowSeconds,
        ).shouldPrepare
    }

    private suspend fun prepareNext(
        provider: app.naviamp.domain.provider.MediaProvider,
        playbackSettings: PlaybackSettings,
        requestGeneration: Long,
    ) {
        val queueEngine = engine as? QueueAwarePlaybackEngine ?: return
        val nextIndex = queue.nextIndex(repeatMode, repeatTrack = true) ?: return
        val next = queue.tracks.getOrNull(nextIndex) ?: return
        val audioSource = resolvePlaybackAudioSource(
            sourceId = activeSourceId(),
            track = next,
            quality = playbackSettings.streamQualityForNetwork(isMobileData()),
            startPositionSeconds = null,
            audioCachingEnabled = cacheSettings().audioCachingEnabled,
            audioAssets = audioAssets,
        )
        val streamUrl = runCatching {
            audioSource.playbackStreamUrl { target -> provider.streamUrl(target.providerStreamRequest) }
        }.getOrNull() ?: return
        if (requestGeneration != generation) return
        val work = planPlaylistTrackStartWork(
            sessionId = requestGeneration,
            track = next,
            playbackSource = PlaybackSource.ProviderStream,
            streamUrl = streamUrl,
            replayGainMode = playbackSettings.replayGainMode,
            replayGainPreampDb = playbackSettings.replayGainPreampDb,
            replayGain = next.replayGain?.let { PlaybackReplayGain(it, ReplayGainSource.Provider) },
            supportsReplayGain = engine.supportsReplayGain,
            engineStartPositionSeconds = audioSource.target.engineStartPositionSeconds,
            coverArtUrl = next.coverArtId?.let(provider::coverArtUrl),
        )
        queueEngine.prepareNext(work.request)
    }
}

private const val CoreGaplessPrepareWindowSeconds = 8.0

/** Shared playback-settings owner and complete engine application policy. */
class NaviampCorePlaybackEngineSettings(
    private val engine: PlaybackEngine,
    initial: PlaybackSettings = PlaybackSettings(),
    private val persist: (PlaybackSettings) -> Unit = {},
) : NaviampCorePlaybackSettingsPort {
    private var current = initial.effectiveForEngine(engine)

    init {
        applyToEngine(current)
    }

    fun current(): PlaybackSettings = current

    override fun apply(settings: PlaybackSettings, redownload: Boolean): PlaybackSettings {
        current = settings.effectiveForEngine(engine)
        applyToEngine(current)
        persist(current)
        return current
    }

    private fun applyToEngine(settings: PlaybackSettings) {
        engine.setVolume(settings.volumePercent)
        (engine as? QueueAwarePlaybackEngine)?.setCrossfadeDuration(settings.crossfadeDurationSeconds)
        (engine as? EqualizerPlaybackEngine)?.setEqualizer(settings.equalizer)
        (engine as? ReplayGainPlaybackEngine)?.setReplayGain(
            settings.replayGainMode,
            settings.replayGainPreampDb,
        )
        (engine as? SampleRateConverterPlaybackEngine)?.setSampleRateConverter(settings.sampleRateConverter)
        (engine as? SampleRateMatchingPlaybackEngine)?.setSampleRateMatching(settings.sampleRateMatching)
        (engine as? AudioOutputDevicePlaybackEngine)?.let { output ->
            val deviceId = settings.outputDevice.deviceId
                .takeIf { settings.outputDevice.mode == AudioOutputDeviceMode.Pinned }
            output.setAudioOutputDevice(deviceId)
        }
    }
}

/** Thread-safe enough for callback publication: immutable snapshots are replaced atomically. */
class NaviampCoreMutableNowPlayingSidecars : NaviampCoreNowPlayingSidecarPort {
    private var state = NaviampCoreNowPlayingSidecars()

    override fun snapshot(): NaviampCoreNowPlayingSidecars = state

    override suspend fun loadForTrack(track: app.naviamp.domain.Track) {
        state = NaviampCoreNowPlayingSidecars()
    }

    override suspend fun loadLyrics(track: app.naviamp.domain.Track) = Unit

    override suspend fun changeLyricsOffset(track: app.naviamp.domain.Track, offsetMillis: Int) = Unit

    override fun updateStreamMetadata(metadata: PlaybackStreamMetadata) {
        state = state.copy(streamMetadata = metadata)
    }

    override fun updateVisualizerFrame(frame: app.naviamp.domain.playback.PlaybackVisualizerFrame?) {
        state = state.copy(visualizerFrame = frame)
    }

    fun updateWaveform(waveform: app.naviamp.domain.waveform.AudioWaveform?) {
        state = state.copy(waveform = waveform)
    }

    fun updateTrackSidecars(
        waveform: app.naviamp.domain.waveform.AudioWaveform?,
        audioTags: List<app.naviamp.domain.audio.AudioTag>?,
        relatedTracks: List<app.naviamp.domain.Track>,
        relatedTracksSource: RelatedTracksSource,
        relatedSimilarityByTrackId: Map<TrackId, Double>,
    ) {
        state = state.copy(
            waveform = waveform,
            audioTags = audioTags,
            relatedTracks = relatedTracks,
            relatedTracksSource = relatedTracksSource,
            relatedSimilarityByTrackId = relatedSimilarityByTrackId,
        )
    }

    fun updateLyrics(
        lyrics: app.naviamp.domain.Lyrics?,
        status: String?,
    ) {
        state = state.copy(lyrics = lyrics, lyricsStatus = status)
    }

    fun updateInternetRadioArtwork(
        station: InternetRadioStation,
        key: String,
        artworkUrl: String?,
    ) {
        state = state.copy(
            internetRadioStations = (state.internetRadioStations + station).distinctBy { it.id },
            currentInternetRadioStationId = station.id,
            radioTrackArtworkByKey = state.radioTrackArtworkByKey + (key to artworkUrl),
        )
    }
}

/** Core-owned provider sidecar loading; hosts supply repositories and native analyzers only. */
class NaviampCoreProviderNowPlayingSidecars(
    private val providerSource: NaviampCoreMediaProviderSource,
    private val sourceId: () -> String? = { null },
    private val waveformService: AudioWaveformService,
    private val playbackSettings: () -> PlaybackSettings,
    private val audioCachingEnabled: () -> Boolean,
    private val isMobileData: () -> Boolean = { false },
    private val audioMetadataSidecarService: AudioMetadataSidecarService? = null,
    private val lyricsSidecarService: LyricsSidecarService? = null,
    private val lyricsOffsetController: LyricsOffsetController? = null,
    private val delegate: NaviampCoreMutableNowPlayingSidecars = NaviampCoreMutableNowPlayingSidecars(),
) : NaviampCoreNowPlayingSidecarPort by delegate {
    private var loadGeneration = 0L

    override suspend fun loadForTrack(track: app.naviamp.domain.Track) {
        val generation = ++loadGeneration
        delegate.loadForTrack(track)
        val provider = providerSource.current() ?: return
        val activeSourceId = sourceId() ?: provider.cacheNamespace
        val settings = playbackSettings()
        val loaded = coroutineScope {
            val waveform = async {
                runCatching {
                    waveformService.loadOrCreateWaveform(
                        sourceId = activeSourceId,
                        provider = provider,
                        track = track,
                        quality = settings.streamQualityForNetwork(isMobileData()),
                        audioCachingEnabled = audioCachingEnabled(),
                    )
                }.getOrNull()
            }
            val related = async {
                loadCoreRelatedTracks(provider, track, settings.sonicSimilarityEnabled)
            }
            val waveformResult = waveform.await()
            val audioTags = runCatching {
                audioMetadataSidecarService?.let { service ->
                    waveformResult?.localAudio
                        ?.let { service.audioTags(it) }
                        ?: service.audioTagsForTrack(
                            sourceId = activeSourceId,
                            track = track,
                            quality = settings.streamQualityForNetwork(isMobileData()),
                            audioCachingEnabled = audioCachingEnabled(),
                        )
                }
            }.getOrNull()
            LoadedTrackSidecars(waveformResult?.waveform, audioTags, related.await())
        }
        if (generation == loadGeneration) {
            delegate.updateTrackSidecars(
                waveform = loaded.waveform,
                audioTags = loaded.audioTags,
                relatedTracks = loaded.related.tracks,
                relatedTracksSource = loaded.related.source,
                relatedSimilarityByTrackId = loaded.related.similarityByTrackId,
            )
        }
    }

    override suspend fun loadLyrics(track: app.naviamp.domain.Track) {
        val generation = loadGeneration
        val provider = providerSource.current()
        val service = lyricsSidecarService
        if (provider == null || service == null) {
            if (generation == loadGeneration) delegate.updateLyrics(null, "Lyrics unavailable")
            return
        }
        val settings = playbackSettings()
        val activeSourceId = sourceId() ?: provider.cacheNamespace
        runCatching {
            coroutineScope {
                val loadingStatus = launch {
                    delay(CoreLyricsLoadingStatusDelayMillis)
                    if (generation == loadGeneration) {
                        delegate.updateLyrics(
                            lyrics = delegate.snapshot().lyrics,
                            status = lyricsLoadingStatus(settings.lrclibLyricsEnabled),
                        )
                    }
                }
                try {
                    service.loadLyrics(
                        sourceId = activeSourceId,
                        provider = provider,
                        track = track,
                        quality = settings.streamQualityForNetwork(isMobileData()),
                        audioCachingEnabled = audioCachingEnabled(),
                        onlineLyricsEnabled = settings.lrclibLyricsEnabled,
                        preferSyncedLyrics = settings.preferSyncedLyrics,
                        searchOrder = settings.lyricsSearchOrder,
                    ).lyrics
                } finally {
                    loadingStatus.cancel()
                }
            }
        }.onSuccess { loaded ->
            if (generation == loadGeneration) {
                val lyrics = lyricsOffsetController?.withSavedOffset(
                    sourceId = activeSourceId,
                    track = track,
                    lyrics = loaded,
                ) ?: loaded
                delegate.updateLyrics(
                    lyrics = lyrics,
                    status = if (lyrics == null) "Lyrics unavailable" else null,
                )
            }
        }.onFailure { error ->
            if (generation == loadGeneration) {
                delegate.updateLyrics(null, lyricsUnavailableStatus(error))
            }
        }
    }

    override suspend fun changeLyricsOffset(track: app.naviamp.domain.Track, offsetMillis: Int) {
        val provider = providerSource.current() ?: return
        val activeSourceId = sourceId() ?: provider.cacheNamespace
        val updated = lyricsOffsetController?.saveOffset(
            sourceId = activeSourceId,
            track = track,
            lyrics = delegate.snapshot().lyrics,
            offsetMillis = offsetMillis,
        ) ?: delegate.snapshot().lyrics?.copy(offsetMillis = offsetMillis)
        delegate.updateLyrics(updated, delegate.snapshot().lyricsStatus)
    }

    override suspend fun loadInternetRadioArtwork(
        station: InternetRadioStation,
        metadata: PlaybackStreamMetadata,
    ) {
        if (!radioArtworkNeedsTrackLookup(station, metadata.title, metadata.properties)) return
        val key = radioTrackArtworkKey(station, metadata.title) ?: return
        if (delegate.snapshot().radioTrackArtworkByKey.containsKey(key)) return
        val provider = providerSource.current() ?: return
        val query = radioTrackArtworkQuery(metadata.title) ?: return
        val artworkUrl = runCatching {
            provider.search(query, limit = 5)
                .tracks
                .firstOrNull { it.coverArtId != null }
                ?.coverArtId
                ?.let(provider::coverArtUrl)
        }.getOrNull()
        delegate.updateInternetRadioArtwork(station, key, artworkUrl)
    }
}

private const val CoreLyricsLoadingStatusDelayMillis = 150L

private data class LoadedTrackSidecars(
    val waveform: app.naviamp.domain.waveform.AudioWaveform?,
    val audioTags: List<app.naviamp.domain.audio.AudioTag>?,
    val related: LoadedRelatedTracks,
)

internal data class LoadedRelatedTracks(
    val tracks: List<app.naviamp.domain.Track> = emptyList(),
    val source: RelatedTracksSource = RelatedTracksSource.None,
    val similarityByTrackId: Map<TrackId, Double> = emptyMap(),
)

internal suspend fun loadCoreRelatedTracks(
    provider: app.naviamp.domain.provider.MediaProvider,
    track: app.naviamp.domain.Track,
    sonicSimilarityEnabled: Boolean,
): LoadedRelatedTracks {
    if (!sonicSimilarityEnabled || !provider.capabilities.supportsSonicSimilarity) {
        return LoadedRelatedTracks()
    }
    val matches = runCatching {
        provider.sonicSimilarTrackMatches(track.id, count = CoreRelatedTrackLimit)
    }.getOrDefault(emptyList())
        .filterNot { it.track.id == track.id }
        .distinctBy { it.track.id }
    return LoadedRelatedTracks(
        tracks = matches.map { it.track },
        source = RelatedTracksSource.SonicSimilarity,
        similarityByTrackId = matches.mapNotNull { match ->
            match.similarity?.let { match.track.id to it }
        }.toMap(),
    )
}

private const val CoreRelatedTrackLimit = 50
