package app.naviamp.presentation

import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackRequest
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
import app.naviamp.domain.playback.planPrepareNextPlayback
import app.naviamp.domain.media.RelatedTracksSource
import app.naviamp.domain.playback.planPlaylistTrackStartWork
import app.naviamp.domain.playback.playbackTargetPlan
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.domain.radio.internetRadioTrack
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.TrackId
import app.naviamp.domain.settings.AudioOutputDeviceMode
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.effectiveForEngine
import app.naviamp.domain.settings.streamQualityForNetwork
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.ui.radioArtworkNeedsTrackLookup
import app.naviamp.ui.radioTrackArtworkKey
import app.naviamp.ui.radioTrackArtworkQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        engine.stop()
        playbackState = PlaybackState.Stopped
    }

    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        this.queue = queue
        if (clearPreparedNext) {
            (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
            preparedForGeneration = -1L
        }
    }

    override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
        this.queue = queue
        restoredStartPositionSeconds = startPositionSeconds
        (engine as? QueueAwarePlaybackEngine)?.clearPreparedNext()
        preparedForGeneration = -1L
    }

    override fun restoreInternetRadio(station: InternetRadioStation) {
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
        startCurrent(startPositionSeconds = null)
    }

    override fun applyRepeatMode(mode: RepeatMode) {
        repeatMode = mode
    }

    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        if (index !in queue.tracks.indices) return
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
            val target = externalStreamUrl?.let {
                null
            } ?: playbackTargetPlan(
                track = track,
                quality = playbackSettings.streamQualityForNetwork(isMobileData()),
                startPositionSeconds = startPositionSeconds,
                hasLocalAudio = false,
            )
            val streamUrl = externalStreamUrl ?: runCatching {
                requireNotNull(provider).streamUrl(requireNotNull(target).providerStreamRequest)
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
                engineStartPositionSeconds = requireNotNull(target).engineStartPositionSeconds,
                coverArtUrl = track.coverArtId?.let { requireNotNull(provider).coverArtUrl(it) },
            ).request
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
        val target = playbackTargetPlan(
            track = next,
            quality = playbackSettings.streamQualityForNetwork(isMobileData()),
            startPositionSeconds = null,
            hasLocalAudio = false,
        )
        val streamUrl = runCatching { provider.streamUrl(target.providerStreamRequest) }.getOrNull() ?: return
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
            engineStartPositionSeconds = null,
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
        relatedTracks: List<app.naviamp.domain.Track>,
        relatedTracksSource: RelatedTracksSource,
        relatedSimilarityByTrackId: Map<TrackId, Double>,
    ) {
        state = state.copy(
            waveform = waveform,
            relatedTracks = relatedTracks,
            relatedTracksSource = relatedTracksSource,
            relatedSimilarityByTrackId = relatedSimilarityByTrackId,
        )
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
    private val waveformService: AudioWaveformService,
    private val playbackSettings: () -> PlaybackSettings,
    private val audioCachingEnabled: () -> Boolean,
    private val delegate: NaviampCoreMutableNowPlayingSidecars = NaviampCoreMutableNowPlayingSidecars(),
) : NaviampCoreNowPlayingSidecarPort by delegate {
    private var loadGeneration = 0L

    override suspend fun loadForTrack(track: app.naviamp.domain.Track) {
        val generation = ++loadGeneration
        delegate.loadForTrack(track)
        val provider = providerSource.current() ?: return
        val settings = playbackSettings()
        val loaded = coroutineScope {
            val waveform = async {
                runCatching {
                    waveformService.loadOrCreateWaveform(
                        sourceId = provider.cacheNamespace,
                        provider = provider,
                        track = track,
                        quality = settings.streamQualityForNetwork(isMobileData = false),
                        audioCachingEnabled = audioCachingEnabled(),
                    ).waveform
                }.getOrNull()
            }
            val related = async {
                loadCoreRelatedTracks(provider, track, settings.sonicSimilarityEnabled)
            }
            LoadedTrackSidecars(waveform.await(), related.await())
        }
        if (generation == loadGeneration) {
            delegate.updateTrackSidecars(
                waveform = loaded.waveform,
                relatedTracks = loaded.related.tracks,
                relatedTracksSource = loaded.related.source,
                relatedSimilarityByTrackId = loaded.related.similarityByTrackId,
            )
        }
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

private data class LoadedTrackSidecars(
    val waveform: app.naviamp.domain.waveform.AudioWaveform?,
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
