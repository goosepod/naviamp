package app.naviamp.desktop.playback.bass

import app.naviamp.desktop.playback.DesktopPlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.EqualizerPlaybackEngine
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.playback.PlaybackReplayGainAdjustment
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerBandCount
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.activeState
import app.naviamp.domain.bass.adoptPreparedBassSource
import app.naviamp.domain.bass.applyBassPlaybackVolume
import app.naviamp.domain.bass.applyEqualizer
import app.naviamp.domain.bass.bassErrorMessage
import app.naviamp.domain.bass.bassPlaybackSnapshot
import app.naviamp.domain.bass.bassPlaybackVisualizerFrame
import app.naviamp.domain.bass.bassStreamActiveStateLabel
import app.naviamp.domain.bass.bassVersionLabel
import app.naviamp.domain.bass.createBassPlayback
import app.naviamp.domain.bass.pause
import app.naviamp.domain.bass.play
import app.naviamp.domain.bass.prepareNextBassMixerSource
import app.naviamp.domain.bass.releaseBassStream
import app.naviamp.domain.bass.releaseBassStreams
import app.naviamp.domain.bass.seekBassPlaybackSource
import app.naviamp.domain.bass.setEndSync
import app.naviamp.domain.bass.stopAndReleaseBassPlayback
import app.naviamp.domain.playback.bassPlaybackFeatureSupport
import app.naviamp.domain.playback.canPrepareBassMixerSource
import app.naviamp.domain.playback.clearPreparedPlaybackMetadata
import app.naviamp.domain.playback.clearPlaybackStreamState
import app.naviamp.domain.playback.failedPreparedPlaybackMetadata
import app.naviamp.domain.playback.normalizedCrossfadeDurationSeconds
import app.naviamp.domain.playback.planPreparedPlaybackAdoption
import app.naviamp.domain.playback.playbackSourceHandle
import app.naviamp.domain.playback.playbackReplayGainAdjustment
import app.naviamp.domain.playback.playbackStartSeekPosition
import app.naviamp.domain.playback.playbackStateForBassActiveState
import app.naviamp.domain.playback.playbackUserVolumeFactor
import app.naviamp.domain.playback.shouldContinueBassPlaybackPolling
import app.naviamp.domain.playback.shouldReusePreparedPlayback
import app.naviamp.domain.playback.shouldUseBassMixerPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class DesktopBassPlaybackEngine(
    private val backendResult: Result<BassAudioBackend> = loadDesktopBassAudioBackend(),
) : QueueAwarePlaybackEngine, VisualizerPlaybackEngine, EqualizerPlaybackEngine, DesktopPlaybackEngineDiagnostics {
    private val backend: BassAudioBackend? = backendResult.getOrNull()
    private val loadError: Throwable? = backendResult.exceptionOrNull()

    override val name: String = "BASS"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    private val featureSupport = bassPlaybackFeatureSupport(backend?.supportsMixer == true)

    override val supportsGapless: Boolean = featureSupport.supportsGapless
    override val supportsCrossfade: Boolean = featureSupport.supportsCrossfade
    override val supportsReplayGain: Boolean = true
    override val supportsEqualizer: Boolean = backend != null
    override val supportsVisualizer: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private var job: Job? = null
    private var stream: Int = 0
    private var currentSourceStream: Int = 0
    private var playbackId: Int = 0
    private var volumePercent: Int = 100
    private var initialized = false
    private var internetStreamsConfigured = false
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var lastRequestUrl: String? = null
    private var lastError: String? = loadError?.message
    private var preparedStream: Int = 0
    private var preparedRequest: PlaybackRequest? = null
    private var preparedReplayGainAdjustment: PlaybackReplayGainAdjustment? = null
    private var preparedError: String? = null
    private var endSyncCallbacks: MutableMap<Int, Int> = mutableMapOf()
    private var crossfadeDurationSeconds: Int = 0
    private var crossfadeActive: Boolean = false
    private var currentReplayGainAdjustment: PlaybackReplayGainAdjustment = PlaybackReplayGainAdjustment.off()
    private var equalizerSettings: EqualizerSettings = EqualizerSettings()
    @Volatile
    private var currentVisualizerFrame: PlaybackVisualizerFrame? = null

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        lastRequestUrl = request.url
        this.onStateChanged = onStateChanged
        if (adoptQueuedPreparedStream(scope, request, onStateChanged, onProgressChanged, onMetadataChanged)) {
            return
        }

        stopActiveStream()
        val currentPlaybackId = nextPlaybackId()
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        val bass = backend
        if (bass == null) {
            val message = loadError?.message ?: "BASS native library is not available."
            lastError = message
            onStateChanged(PlaybackState.Error(message))
            return
        }

        job = scope.launch(Dispatchers.IO) {
            var createdPlayback: CreatedPlayback? = null
            try {
                ensureInitialized(bass)
                createdPlayback = createPlayback(
                    bass = bass,
                    request = request,
                    useMixer = shouldUseBassMixerPlayback(
                        request = request,
                        supportsMixer = bass.supportsMixer,
                        requireMediaId = false,
                    ),
                ).getOrThrow()
                if (!isCurrentPlayback(currentPlaybackId)) {
                    freeCreatedPlayback(bass, createdPlayback)
                    createdPlayback = null
                    return@launch
                }
                val playbackHandle = createdPlayback.playbackHandle
                stream = playbackHandle
                currentSourceStream = createdPlayback.sourceHandle
                currentReplayGainAdjustment = createdPlayback.replayGainAdjustment
                attachEndSync(bass, createdPlayback.sourceHandle, currentPlaybackId, onStateChanged)
                createdPlayback = null
                applyOutputVolume(bass)
                applyEqualizer(bass)
                playbackStartSeekPosition(request.startPositionSeconds)
                    ?.let { seekCurrentSource(bass, it) }
                bass.play(playbackHandle)
                    .getOrThrow()
                onStateChanged(PlaybackState.Playing)

                var lastProgress = PlaybackProgress.Unknown
                var lastMetadata = PlaybackStreamMetadata()
                var lastActiveState: Int? = null
                while (
                    isCurrentPlayback(currentPlaybackId) &&
                    shouldContinueBassPlaybackPolling(bass.activeState(playbackHandle))
                ) {
                    val snapshot = bass.bassPlaybackSnapshot(playbackHandle, currentSourceStream)
                    val activeState = snapshot.activeState
                    if (activeState != lastActiveState) {
                        lastActiveState = activeState
                        playbackStateForBassActiveState(activeState)?.let(onStateChanged)
                    }
                    val progress = snapshot.progress
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgressChanged(progress)
                    }
                    val metadata = snapshot.metadata
                    if (metadata != lastMetadata) {
                        lastMetadata = metadata
                        onMetadataChanged(metadata)
                    }
                    delay(PlaybackStatusPollIntervalMillis)
                }

                if (isCurrentPlayback(currentPlaybackId)) {
                    onStateChanged(PlaybackState.Finished)
                }
            } catch (exception: Throwable) {
                createdPlayback?.let { freeCreatedPlayback(bass, it) }
                if (isCurrentPlayback(currentPlaybackId) && job?.isCancelled != true) {
                    val message = exception.message ?: "BASS playback failed."
                    lastError = message
                    onStateChanged(PlaybackState.Error(message))
                }
            } finally {
                if (isCurrentPlayback(currentPlaybackId)) {
                    freeAllStreams(bass)
                    val reset = clearPlaybackStreamState()
                    stream = reset.stream
                    currentSourceStream = reset.currentSourceStream
                    currentReplayGainAdjustment = reset.replayGainAdjustment
                    currentVisualizerFrame = null
                    onProgressChanged(PlaybackProgress.Unknown)
                }
            }
        }
    }

    override fun pause() {
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            bass.pause(handle)
                .onSuccess { onStateChanged?.invoke(PlaybackState.Paused) }
                .onFailure { lastError = it.message }
        }
    }

    override fun resume() {
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            bass.play(handle)
                .onSuccess { onStateChanged?.invoke(PlaybackState.Playing) }
                .onFailure { lastError = it.message }
        }
    }

    override fun seek(positionSeconds: Double) {
        val handle = playbackSourceHandle(stream, currentSourceStream)
        val bass = backend ?: return
        if (handle != 0) {
            freePreparedStream()
            seekCurrentSource(bass, positionSeconds)
        }
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        val handle = stream
        val bass = backend ?: return
        if (handle != 0) {
            applyOutputVolume(bass)
        }
    }

    override fun stop() {
        freePreparedStream()
        stopActiveStream()
        onStateChanged = null
    }

    override fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSeconds = normalizedCrossfadeDurationSeconds(seconds)
    }

    override fun setEqualizer(settings: EqualizerSettings) {
        equalizerSettings = settings.normalized()
        backend?.let(::applyEqualizer)
    }

    override fun prepareNext(request: PlaybackRequest) {
        val bass = backend ?: return
        if (shouldReusePreparedPlayback(preparedRequest, preparedStream != 0, request)) return
        freePreparedStream()
        runCatching {
            ensureInitialized(bass)
            if (
                canPrepareBassMixerSource(
                    playbackHandle = stream,
                    currentSourceHandle = currentSourceStream,
                    supportsMixer = bass.supportsMixer,
                )
            ) {
                val adjustment = replayGainAdjustment(request)
                val localFile = localFileFromUrl(request.url)
                val prepared = bass.prepareNextBassMixerSource(
                    localPath = localFile?.absolutePath,
                    url = request.url,
                    mixer = stream,
                    currentSource = currentSourceStream,
                    currentSourceVolumeFactor = currentReplayGainAdjustment.volumeFactor,
                    crossfadeDurationSeconds = crossfadeDurationSeconds,
                    replayGainFactor = adjustment.volumeFactor,
                    playbackDecode = true,
                ).getOrThrow()
                crossfadeActive = prepared.crossfadeActive
                attachEndSync(bass, prepared.sourceHandle, playbackId)
                preparedReplayGainAdjustment = adjustment
                prepared.sourceHandle
            } else {
                createPlayback(bass, request, useMixer = false).getOrThrow().playbackHandle.also {
                    preparedReplayGainAdjustment = replayGainAdjustment(request)
                }
            }
        }.onSuccess { handle ->
            preparedStream = handle
            preparedRequest = request
            preparedError = null
        }.onFailure { error ->
            val reset = failedPreparedPlaybackMetadata(error)
            preparedError = reset.error
            lastError = preparedError
            preparedStream = 0
            preparedRequest = reset.request
            preparedReplayGainAdjustment = reset.replayGainAdjustment
        }
    }

    private fun stopActiveStream() {
        playbackId += 1
        job?.cancel()
        job = null
        val handle = stream
        val bass = backend
        if (bass != null && handle != 0) {
            freeAllStreams(bass)
        }
        val reset = clearPlaybackStreamState()
        stream = reset.stream
        currentSourceStream = reset.currentSourceStream
        crossfadeActive = reset.crossfadeActive
        currentReplayGainAdjustment = reset.replayGainAdjustment
        currentVisualizerFrame = null
        endSyncCallbacks.clear()
    }

    override fun visualizerFrame(): PlaybackVisualizerFrame? {
        val bass = backend ?: return null
        val handle = stream.takeIf { it != 0 } ?: return null
        return visualizerFrameFor(bass, handle)
            .also { currentVisualizerFrame = it }
    }

    override fun statsRows(): List<Pair<String, String>> =
        listOf(
            "BASS load state" to if (backend != null) "Loaded" else "Unavailable",
            "BASS version" to (backend?.version?.let(::bassVersionLabel) ?: "Unknown"),
            "BASSmix version" to (backend?.mixerVersion?.let(::bassVersionLabel) ?: "Unavailable"),
            "BASSmix error" to (backend?.mixerError ?: "None"),
            "BASS directory" to (backend?.libraryDirectory ?: "Not resolved"),
            "Loaded plugins" to backend?.pluginDiagnostics.orEmpty()
                .filter { it.loaded }
                .joinToString(", ") { it.stem }
                .ifBlank { "None" },
            "Failed plugins" to backend?.pluginDiagnostics.orEmpty()
                .filterNot { it.loaded }
                .joinToString(", ") { plugin ->
                    "${plugin.stem} (${plugin.errorCode?.let(::bassErrorMessage) ?: "unknown"})"
                }.ifBlank { "None" },
            "Active state" to (backend?.bassStreamActiveStateLabel(stream, "No stream") ?: "No stream"),
            "Active source state" to (backend?.bassStreamActiveStateLabel(currentSourceStream, "No source") ?: "No source"),
            "ReplayGain mode" to currentReplayGainAdjustment.mode.displayName,
            "ReplayGain source" to (currentReplayGainAdjustment.source?.displayName ?: "None"),
            "ReplayGain applied" to currentReplayGainAdjustment.label,
            "ReplayGain clipping guard" to currentReplayGainAdjustment.clippingGuardLabel,
            "Visualizer" to if (currentVisualizerFrame != null) {
                "${currentVisualizerFrame?.bands?.size ?: 0} FFT bands"
            } else {
                "Waiting"
            },
            "Crossfade duration" to if (crossfadeDurationSeconds > 0) "${crossfadeDurationSeconds}s" else "Off",
            "Crossfade active" to crossfadeActive.toString(),
            "Prepared next" to (preparedRequest?.mediaId ?: preparedRequest?.url ?: "None"),
            "Prepared next state" to when {
                preparedStream != 0 -> "Ready"
                preparedError != null -> "Failed: $preparedError"
                else -> "None"
            },
            "Volume" to "$volumePercent%",
            "Last request" to (lastRequestUrl ?: "None"),
            "Last error" to (lastError ?: "None"),
        )

    private fun ensureInitialized(bass: BassAudioBackend) {
        if (!initialized) {
            bass.init().getOrThrow()
            initialized = true
        }
        if (!internetStreamsConfigured) {
            bass.configureInternetStreams().getOrThrow()
            internetStreamsConfigured = true
        }
    }

    private fun adoptQueuedPreparedStream(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ): Boolean {
        val bass = backend ?: return false
        val queuedSource = preparedStream
        val plan = planPreparedPlaybackAdoption(
            hasActiveStream = stream != 0,
            preparedRequest = preparedRequest,
            hasPreparedStream = queuedSource != 0,
            supportsMixer = bass.supportsMixer,
            request = request,
        )
        if (!plan.shouldAdopt) return false
        job?.cancel()
        job = null
        val currentPlaybackId = nextPlaybackId()
        val adjustment = preparedReplayGainAdjustment ?: PlaybackReplayGainAdjustment.off()
        bass.adoptPreparedBassSource(
            playbackHandle = stream,
            currentSourceHandle = currentSourceStream,
            nextSourceHandle = queuedSource,
            userVolumeFactor = outputVolumeFactor(),
            replayGainFactor = adjustment.volumeFactor,
        ).forEach { result -> result.onFailure { lastError = it.message } }
        currentSourceStream = queuedSource
        currentReplayGainAdjustment = adjustment
        applyEqualizer(bass)
        crossfadeActive = false
        attachEndSync(bass, queuedSource, currentPlaybackId, onStateChanged)
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainAdjustment = reset.replayGainAdjustment
        preparedError = reset.error
        onProgressChanged(PlaybackProgress.Unknown)
        onStateChanged(PlaybackState.Playing)
        job = scope.launch(Dispatchers.IO) {
            var lastProgress = PlaybackProgress.Unknown
            var lastMetadata = PlaybackStreamMetadata()
            var lastActiveState: Int? = null
            try {
                while (
                    isCurrentPlayback(currentPlaybackId) &&
                    shouldContinueBassPlaybackPolling(bass.activeState(stream))
                ) {
                    val snapshot = bass.bassPlaybackSnapshot(stream, currentSourceStream)
                    val activeState = snapshot.activeState
                    if (activeState != lastActiveState) {
                        lastActiveState = activeState
                        playbackStateForBassActiveState(activeState)?.let(onStateChanged)
                    }
                    val progress = snapshot.progress
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgressChanged(progress)
                    }
                    val metadata = snapshot.metadata
                    if (metadata != lastMetadata) {
                        lastMetadata = metadata
                        onMetadataChanged(metadata)
                    }
                    delay(PlaybackStatusPollIntervalMillis)
                }

                if (isCurrentPlayback(currentPlaybackId)) {
                    onStateChanged(PlaybackState.Finished)
                }
            } catch (exception: Throwable) {
                if (isCurrentPlayback(currentPlaybackId) && job?.isCancelled != true) {
                    val message = exception.message ?: "BASS playback failed."
                    lastError = message
                    onStateChanged(PlaybackState.Error(message))
                }
            } finally {
                if (isCurrentPlayback(currentPlaybackId)) {
                    freeAllStreams(bass)
                    val streamReset = clearPlaybackStreamState()
                    stream = streamReset.stream
                    currentSourceStream = streamReset.currentSourceStream
                    currentReplayGainAdjustment = streamReset.replayGainAdjustment
                    currentVisualizerFrame = null
                    onProgressChanged(PlaybackProgress.Unknown)
                }
            }
        }
        return true
    }

    private fun freePreparedStream() {
        val handle = preparedStream
        val bass = backend
        if (bass != null && handle != 0) {
            bass.releaseBassStream(handle)
                .onFailure { lastError = it.message }
        }
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainAdjustment = reset.replayGainAdjustment
        preparedError = reset.error
    }

    private fun createPlayback(
        bass: BassAudioBackend,
        request: PlaybackRequest,
        useMixer: Boolean,
    ): Result<CreatedPlayback> {
        val adjustment = replayGainAdjustment(request)
        val localFile = localFileFromUrl(request.url)
        return bass.createBassPlayback(
            localPath = localFile?.absolutePath,
            url = request.url,
            useMixer = useMixer,
            crossfadeDurationSeconds = crossfadeDurationSeconds,
            replayGainFactor = adjustment.volumeFactor,
            playbackDecode = useMixer,
        ).map { playback ->
            CreatedPlayback(
                playbackHandle = playback.playbackHandle,
                sourceHandle = playback.sourceHandle,
                replayGainAdjustment = adjustment,
            )
        }
    }

    private fun attachEndSync(
        bass: BassAudioBackend,
        source: Int,
        currentPlaybackId: Int,
        stateCallback: ((PlaybackState) -> Unit)? = null,
    ) {
        bass.setEndSync(source) { channel ->
            if (channel.value == source && isCurrentPlayback(currentPlaybackId)) {
                (stateCallback ?: onStateChanged)?.invoke(PlaybackState.Finished)
            }
        }
            .onSuccess { endSyncCallbacks[source] = it }
            .onFailure { lastError = it.message }
    }

    private fun applyOutputVolume(bass: BassAudioBackend) {
        val handle = stream.takeIf { it != 0 } ?: return
        bass.applyBassPlaybackVolume(
            outputStream = handle,
            sourceStream = currentSourceStream,
            userVolumeFactor = outputVolumeFactor(),
            replayGainFactor = currentReplayGainAdjustment.volumeFactor,
        ).forEach { result -> result.onFailure { lastError = it.message } }
    }

    private fun applyEqualizer(bass: BassAudioBackend) {
        stream.takeIf { it != 0 }
            ?.let { handle -> bass.applyEqualizer(handle, equalizerSettings.bandsForBackend()) }
            ?.onFailure { lastError = it.message }
    }

    private fun replayGainAdjustment(request: PlaybackRequest): PlaybackReplayGainAdjustment =
        playbackReplayGainAdjustment(request)

    private fun outputVolumeFactor(): Float =
        playbackUserVolumeFactor(volumePercent)

    private fun visualizerFrameFor(
        bass: BassAudioBackend,
        sourceHandle: Int,
    ): PlaybackVisualizerFrame? =
        bass.bassPlaybackVisualizerFrame(
            stream = sourceHandle,
            bins = VisualizerBandCount,
            timestampMillis = System.currentTimeMillis(),
        )
            .onFailure { lastError = it.message }
            .getOrNull()

    private fun seekCurrentSource(bass: BassAudioBackend, seconds: Double) {
        bass.seekBassPlaybackSource(stream, currentSourceStream, seconds)
            .onFailure { lastError = it.message }
    }

    private fun freeAllStreams(bass: BassAudioBackend) {
        bass.stopAndReleaseBassPlayback(stream, currentSourceStream, preparedStream)
            .forEach { result -> result.onFailure { lastError = it.message } }
        val streamReset = clearPlaybackStreamState()
        crossfadeActive = streamReset.crossfadeActive
        val preparedReset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = preparedReset.request
        preparedReplayGainAdjustment = preparedReset.replayGainAdjustment
        preparedError = preparedReset.error
        currentVisualizerFrame = null
    }

    private fun freeCreatedPlayback(
        bass: BassAudioBackend,
        created: CreatedPlayback,
    ) {
        bass.releaseBassStreams(created.playbackHandle, created.sourceHandle)
            .forEach { result -> result.onFailure { lastError = it.message } }
    }

    private fun localFileFromUrl(url: String): File? =
        runCatching {
            val uri = URI(url)
            if (uri.scheme == "file") File(uri) else null
        }.getOrNull()

    private fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    private fun isCurrentPlayback(id: Int): Boolean =
        playbackId == id
}

private val PlaybackReplayGainAdjustment.label: String
    get() {
        val gainDb = gainDb ?: return "Off"
        return "${gainDb.formatDb()} dB -> ${volumeFactor.formatFactor()}x"
    }

private val PlaybackReplayGainAdjustment.clippingGuardLabel: String
    get() = when {
        gainDb == null -> "Off"
        clippingPrevented -> "Peak ${peak?.formatPeak() ?: "unknown"} limited boost"
        else -> "No clipping risk detected"
    }

private data class CreatedPlayback(
    val playbackHandle: Int,
    val sourceHandle: Int,
    val replayGainAdjustment: PlaybackReplayGainAdjustment,
)

private fun Double.formatDb(): String =
    "%+.2f".format(this)

private fun Float.formatFactor(): String =
    "%.3f".format(this)

private fun Double.formatPeak(): String =
    "%.6f".format(this)

private fun EqualizerSettings.bandsForBackend(): List<Float> =
    if (enabled) bandsDb else emptyList()

private const val PlaybackStatusPollIntervalMillis = 250L
