package app.naviamp.desktop.playback.bass

import app.naviamp.desktop.playback.PlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.PlaybackReplayGainAdjustment
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerBandCount
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.bass.applyPreparedBassMixerTransition
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.bassActiveStateLabel
import app.naviamp.domain.bass.bassErrorMessage
import app.naviamp.domain.bass.bassVersionLabel
import app.naviamp.domain.bass.releaseBassStream
import app.naviamp.domain.bass.releaseBassStreams
import app.naviamp.domain.playback.clearPreparedPlaybackMetadata
import app.naviamp.domain.playback.clearPlaybackStreamState
import app.naviamp.domain.playback.failedPreparedPlaybackMetadata
import app.naviamp.domain.playback.normalizedCrossfadeDurationSeconds
import app.naviamp.domain.playback.planBassMixerCreation
import app.naviamp.domain.playback.planPreparedPlaybackAdoption
import app.naviamp.domain.playback.planPreparedMixerTransition
import app.naviamp.domain.playback.playbackVisualizerFrameFromFft
import app.naviamp.domain.playback.playbackVolumeApplicationPlan
import app.naviamp.domain.playback.playbackReplayGainAdjustment
import app.naviamp.domain.playback.shouldReusePreparedPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class BassPlaybackEngine(
    private val nativeResult: Result<BassNative> = BassNative.load(),
) : QueueAwarePlaybackEngine, VisualizerPlaybackEngine, PlaybackEngineDiagnostics {
    private val native: BassNative? = nativeResult.getOrNull()
    private val loadError: Throwable? = nativeResult.exceptionOrNull()
    private val backend: BassAudioBackend? = native?.let(::DesktopBassAudioBackend)

    override val name: String = "BASS"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = native?.supportsMixer == true
    override val supportsCrossfade: Boolean = native?.supportsMixer == true
    override val supportsReplayGain: Boolean = true
    override val supportsVisualizer: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private val plugins: List<BassPlugin> = native?.loadAvailablePlugins().orEmpty()
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
        if (adoptQueuedPreparedStream(request, onStateChanged, onProgressChanged)) {
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
                createdPlayback = if (bass.supportsMixer) {
                    createMixerPlayback(bass, request).getOrThrow()
                } else {
                    createDirectPlayback(bass, request).getOrThrow()
                }
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
                request.startPositionSeconds
                    ?.takeIf { it > 0.0 }
                    ?.let { seekCurrentSource(bass, it) }
                bass.play(playbackHandle)
                    .getOrThrow()
                onStateChanged(PlaybackState.Playing)

                var lastProgress = PlaybackProgress.Unknown
                var lastMetadata = PlaybackStreamMetadata()
                while (isCurrentPlayback(currentPlaybackId) && bass.activeState(playbackHandle) != BassActiveState.Stopped) {
                    val sourceHandle = currentSourceStream.takeIf { it != 0 } ?: playbackHandle
                    val progress = PlaybackProgress(
                        positionSeconds = bass.positionSeconds(sourceHandle),
                        durationSeconds = bass.durationSeconds(sourceHandle),
                    )
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgressChanged(progress)
                    }
                    val metadata = PlaybackStreamMetadata.fromProperties(bass.streamMetadata(sourceHandle))
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
        val handle = currentSourceStream.takeIf { it != 0 } ?: stream
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

    override fun prepareNext(request: PlaybackRequest) {
        val bass = backend ?: return
        if (shouldReusePreparedPlayback(preparedRequest, preparedStream != 0, request)) return
        freePreparedStream()
        runCatching {
            ensureInitialized(bass)
            if (stream != 0 && bass.supportsMixer) {
                createQueuedSource(bass, request).getOrThrow().also { source ->
                    val adjustment = replayGainAdjustment(request)
                    val transition = planPreparedMixerTransition(crossfadeDurationSeconds, adjustment.volumeFactor)
                    bass.applyPreparedBassMixerTransition(
                        mixer = BassStreamHandle(stream),
                        nextSource = BassStreamHandle(source),
                        currentSource = currentSourceStream.takeIf { it != 0 }?.let(::BassStreamHandle),
                        currentSourceVolumeFactor = currentReplayGainAdjustment.volumeFactor,
                        transition = transition,
                    ).onSuccess { result ->
                        result.fallbackErrors.lastOrNull()?.let { lastError = it.message }
                    }.getOrThrow()
                    crossfadeActive = transition.shouldCrossfade
                    attachEndSync(bass, source, playbackId)
                    preparedReplayGainAdjustment = adjustment
                }
            } else {
                createStream(bass, request, decode = false).getOrThrow().also {
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
            bass.stop(handle)
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
            "BASS load state" to if (native != null) "Loaded" else "Unavailable",
            "BASS version" to (backend?.version?.let(::bassVersionLabel) ?: "Unknown"),
            "BASSmix version" to (backend?.mixerVersion?.let(::bassVersionLabel) ?: "Unavailable"),
            "BASSmix error" to (backend?.mixerError ?: "None"),
            "BASS directory" to (backend?.libraryDirectory ?: "Not resolved"),
            "Loaded plugins" to plugins.filter { it.loaded }.joinToString(", ") { it.stem }.ifBlank { "None" },
            "Failed plugins" to plugins.filterNot { it.loaded }.joinToString(", ") { plugin ->
                "${plugin.stem} (${plugin.errorCode?.let(::bassErrorMessage) ?: "unknown"})"
            }.ifBlank { "None" },
            "Active state" to native?.let { bass ->
                stream.takeIf { it != 0 }?.let { bassActiveStateLabel(bass.activeState(it)) }
            }.orEmpty().ifBlank { "No stream" },
            "Active source state" to native?.let { bass ->
                currentSourceStream.takeIf { it != 0 }?.let { bassActiveStateLabel(bass.activeState(it)) }
            }.orEmpty().ifBlank { "No source" },
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

    private fun takePreparedStream(request: PlaybackRequest): Int? {
        val handle = preparedStream
            .takeIf { shouldReusePreparedPlayback(preparedRequest, it != 0, request) }
            ?: return null
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainAdjustment = reset.replayGainAdjustment
        preparedError = reset.error
        return handle
    }

    private fun adoptQueuedPreparedStream(
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
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
        currentSourceStream.takeIf { it != 0 && it != queuedSource }?.let { finishedSource ->
            bass.releaseBassStream(BassStreamHandle(finishedSource)).onFailure { lastError = it.message }
        }
        currentSourceStream = queuedSource
        currentReplayGainAdjustment = preparedReplayGainAdjustment ?: PlaybackReplayGainAdjustment.off()
        crossfadeActive = false
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainAdjustment = reset.replayGainAdjustment
        preparedError = reset.error
        onProgressChanged(PlaybackProgress.Unknown)
        onStateChanged(PlaybackState.Playing)
        return true
    }

    private fun freePreparedStream() {
        val handle = preparedStream
        val bass = backend
        if (bass != null && handle != 0) {
            bass.releaseBassStream(BassStreamHandle(handle))
                .onFailure { lastError = it.message }
        }
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainAdjustment = reset.replayGainAdjustment
        preparedError = reset.error
    }

    private fun createStream(
        bass: BassAudioBackend,
        request: PlaybackRequest,
        decode: Boolean,
    ): Result<Int> {
        val localFile = localFileFromUrl(request.url)
        val streamResult = if (localFile != null) {
            if (decode) {
                bass.createFilePlaybackDecodeStream(localFile.absolutePath)
            } else {
                bass.createFileStream(localFile.absolutePath)
            }
        } else {
            if (decode) bass.createUrlDecodeStream(request.url) else bass.createUrlStream(request.url)
        }
        return streamResult.map { it.value }
    }

    private fun createDirectPlayback(
        bass: BassAudioBackend,
        request: PlaybackRequest,
    ): Result<CreatedPlayback> =
        createStream(bass, request, decode = false).map { handle ->
            CreatedPlayback(
                playbackHandle = handle,
                sourceHandle = handle,
                replayGainAdjustment = replayGainAdjustment(request),
            )
        }

    private fun createMixerPlayback(
        bass: BassAudioBackend,
        request: PlaybackRequest,
    ): Result<CreatedPlayback> =
        runCatching {
            val source = createQueuedSource(bass, request).getOrThrow()
            val info = bass.channelInfo(source).getOrThrow()
            val mixerPlan = planBassMixerCreation(info, crossfadeDurationSeconds)
            val mixer = bass.createMixer(
                frequency = mixerPlan.frequency,
                channels = mixerPlan.channels,
                queueSources = mixerPlan.queueSources,
            ).getOrThrow().value
            val adjustment = replayGainAdjustment(request)
            applySourceReplayGain(bass, source, adjustment)
            bass.addMixerChannel(mixer, source).getOrThrow()
            CreatedPlayback(
                playbackHandle = mixer,
                sourceHandle = source,
                replayGainAdjustment = adjustment,
            )
        }

    private fun createQueuedSource(
        bass: BassAudioBackend,
        request: PlaybackRequest,
    ): Result<Int> =
        createStream(bass, request, decode = true)

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
        val plan = playbackVolumeApplicationPlan(
            userVolumeFactor = outputVolumeFactor(),
            replayGainFactor = currentReplayGainAdjustment.volumeFactor,
            hasSeparateSourceStream = currentSourceStream != 0 && handle != currentSourceStream,
        )
        if (currentSourceStream != 0 && handle != currentSourceStream) {
            bass.setVolume(handle, plan.outputVolumeFactor)
                .onFailure { lastError = it.message }
        } else {
            bass.setVolume(handle, plan.directVolumeFactor)
                .onFailure { lastError = it.message }
        }
    }

    private fun applySourceReplayGain(
        bass: BassAudioBackend,
        source: Int,
        adjustment: PlaybackReplayGainAdjustment,
    ) {
        bass.setVolume(source, adjustment.volumeFactor)
            .onFailure { lastError = it.message }
    }

    private fun replayGainAdjustment(request: PlaybackRequest): PlaybackReplayGainAdjustment =
        playbackReplayGainAdjustment(request)

    private fun outputVolumeFactor(): Float =
        volumePercent.coerceIn(0, 100) / 100f

    private fun visualizerFrameFor(
        bass: BassAudioBackend,
        sourceHandle: Int,
    ): PlaybackVisualizerFrame? =
        bass.fft(sourceHandle, VisualizerBandCount)
            .map { fft -> playbackVisualizerFrameFromFft(fft, timestampMillis = System.currentTimeMillis()) }
            .onFailure { lastError = it.message }
            .getOrNull()

    private fun seekCurrentSource(bass: BassAudioBackend, seconds: Double) {
        val sourceHandle = currentSourceStream.takeIf { it != 0 } ?: stream
        if (sourceHandle != 0) {
            bass.seek(sourceHandle, seconds)
                .onFailure { lastError = it.message }
        }
    }

    private fun freeAllStreams(bass: BassAudioBackend) {
        bass.releaseBassStreams(stream, currentSourceStream, preparedStream)
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

private fun BassAudioBackend.play(stream: Int): Result<Unit> =
    play(BassStreamHandle(stream))

private fun BassAudioBackend.pause(stream: Int): Result<Unit> =
    pause(BassStreamHandle(stream))

private fun BassAudioBackend.stop(stream: Int): Result<Unit> =
    stop(BassStreamHandle(stream))

private fun BassAudioBackend.activeState(stream: Int): Int =
    activeState(BassStreamHandle(stream)) ?: BassActiveState.Stopped

private fun BassAudioBackend.addMixerChannel(mixer: Int, stream: Int): Result<Unit> =
    addMixerChannel(BassStreamHandle(mixer), BassStreamHandle(stream))

private fun BassAudioBackend.setEndSync(
    stream: Int,
    callback: (BassStreamHandle) -> Unit,
): Result<Int> =
    setEndSync(BassStreamHandle(stream), callback)

private fun BassAudioBackend.setVolume(stream: Int, volume: Float): Result<Unit> =
    setVolume(BassStreamHandle(stream), volume)

private fun BassAudioBackend.slideVolume(stream: Int, volume: Float, durationMillis: Int): Result<Unit> =
    slideVolume(BassStreamHandle(stream), volume, durationMillis)

private fun BassAudioBackend.seek(stream: Int, seconds: Double): Result<Unit> =
    seek(BassStreamHandle(stream), seconds)

private fun BassAudioBackend.positionSeconds(stream: Int): Double? =
    positionSeconds(BassStreamHandle(stream))

private fun BassAudioBackend.durationSeconds(stream: Int): Double? =
    durationSeconds(BassStreamHandle(stream))

private fun BassAudioBackend.channelInfo(stream: Int) =
    channelInfo(BassStreamHandle(stream))

private fun BassAudioBackend.fft(stream: Int, bins: Int): Result<FloatArray> =
    fft(BassStreamHandle(stream), bins)

private fun BassAudioBackend.streamMetadata(stream: Int): Map<String, String> =
    streamMetadata(BassStreamHandle(stream))

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

private const val PlaybackStatusPollIntervalMillis = 250L
