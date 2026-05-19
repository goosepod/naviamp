package app.naviamp.desktop.playback.bass

import app.naviamp.desktop.playback.PlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.ReplayGainSource
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class BassPlaybackEngine(
    private val nativeResult: Result<BassNative> = BassNative.load(),
) : QueueAwarePlaybackEngine, VisualizerPlaybackEngine, PlaybackEngineDiagnostics {
    private val native: BassNative? = nativeResult.getOrNull()
    private val loadError: Throwable? = nativeResult.exceptionOrNull()

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
    private var preparedReplayGainAdjustment: ReplayGainAdjustment? = null
    private var preparedError: String? = null
    private var endSyncCallbacks: MutableMap<Int, BassSyncCallback> = mutableMapOf()
    private var crossfadeDurationSeconds: Int = 0
    private var crossfadeActive: Boolean = false
    private var currentReplayGainAdjustment: ReplayGainAdjustment = ReplayGainAdjustment.off()
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

        val bass = native
        if (bass == null) {
            val message = loadError?.message ?: "BASS native library is not available."
            lastError = message
            onStateChanged(PlaybackState.Error(message))
            return
        }

        job = scope.launch(Dispatchers.IO) {
            try {
                ensureInitialized(bass)
                val playbackHandle = if (bass.supportsMixer) {
                    createMixerPlayback(bass, request, currentPlaybackId, onStateChanged).getOrThrow()
                } else {
                    createDirectPlayback(bass, request, currentPlaybackId, onStateChanged).getOrThrow()
                }
                stream = playbackHandle
                applyOutputVolume(bass)
                request.startPositionSeconds
                    ?.takeIf { it > 0.0 }
                    ?.let { seekCurrentSource(bass, it) }
                bass.play(playbackHandle)
                    .getOrThrow()
                onStateChanged(PlaybackState.Playing)

                var lastProgress = PlaybackProgress.Unknown
                var lastMetadata = PlaybackStreamMetadata()
                while (isCurrentPlayback(currentPlaybackId) && bass.activeState(playbackHandle) != BassActive.Stopped) {
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
                    currentVisualizerFrame = visualizerFrameFor(bass, sourceHandle)
                    delay(100)
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
                    stream = 0
                    currentSourceStream = 0
                    currentReplayGainAdjustment = ReplayGainAdjustment.off()
                    currentVisualizerFrame = null
                    onProgressChanged(PlaybackProgress.Unknown)
                }
            }
        }
    }

    override fun pause() {
        val handle = stream
        val bass = native ?: return
        if (handle != 0) {
            bass.pause(handle)
                .onSuccess { onStateChanged?.invoke(PlaybackState.Paused) }
                .onFailure { lastError = it.message }
        }
    }

    override fun resume() {
        val handle = stream
        val bass = native ?: return
        if (handle != 0) {
            bass.play(handle)
                .onSuccess { onStateChanged?.invoke(PlaybackState.Playing) }
                .onFailure { lastError = it.message }
        }
    }

    override fun seek(positionSeconds: Double) {
        val handle = currentSourceStream.takeIf { it != 0 } ?: stream
        val bass = native ?: return
        if (handle != 0) {
            freePreparedStream()
            seekCurrentSource(bass, positionSeconds)
        }
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        val handle = stream
        val bass = native ?: return
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
        crossfadeDurationSeconds = seconds.coerceIn(0, 12)
    }

    override fun prepareNext(request: PlaybackRequest) {
        val bass = native ?: return
        if (preparedRequest == request && preparedStream != 0) return
        freePreparedStream()
        runCatching {
            ensureInitialized(bass)
            if (stream != 0 && bass.supportsMixer) {
                createQueuedSource(bass, request).getOrThrow().also { source ->
                    val adjustment = replayGainAdjustment(request)
                    applySourceReplayGain(bass, source, adjustment)
                    if (crossfadeDurationSeconds > 0) {
                        startCrossfade(bass, source, adjustment)
                    } else {
                        bass.addMixerChannel(stream, source).getOrThrow()
                    }
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
            preparedError = error.message ?: "Could not prepare next BASS stream."
            lastError = preparedError
            preparedStream = 0
            preparedRequest = null
            preparedReplayGainAdjustment = null
        }
    }

    private fun stopActiveStream() {
        playbackId += 1
        job?.cancel()
        job = null
        val handle = stream
        val bass = native
        if (bass != null && handle != 0) {
            bass.stop(handle)
            freeAllStreams(bass)
        }
        stream = 0
        currentSourceStream = 0
        crossfadeActive = false
        currentReplayGainAdjustment = ReplayGainAdjustment.off()
        currentVisualizerFrame = null
        endSyncCallbacks.clear()
    }

    override fun visualizerFrame(): PlaybackVisualizerFrame? =
        currentVisualizerFrame

    override fun statsRows(): List<Pair<String, String>> =
        listOf(
            "BASS load state" to if (native != null) "Loaded" else "Unavailable",
            "BASS version" to (native?.version?.let(::bassVersionLabel) ?: "Unknown"),
            "BASSmix version" to (native?.mixerVersion?.let(::bassVersionLabel) ?: "Unavailable"),
            "BASSmix error" to (native?.mixerError ?: "None"),
            "BASS directory" to (native?.libraryDirectory?.absolutePath ?: "Not resolved"),
            "Loaded plugins" to plugins.filter { it.loaded }.joinToString(", ") { it.stem }.ifBlank { "None" },
            "Failed plugins" to plugins.filterNot { it.loaded }.joinToString(", ") { plugin ->
                "${plugin.stem} (${plugin.errorCode?.let(::bassErrorMessage) ?: "unknown"})"
            }.ifBlank { "None" },
            "Active state" to native?.let { bass ->
                stream.takeIf { it != 0 }?.let { bassActiveLabel(bass.activeState(it)) }
            }.orEmpty().ifBlank { "No stream" },
            "Active source state" to native?.let { bass ->
                currentSourceStream.takeIf { it != 0 }?.let { bassActiveLabel(bass.activeState(it)) }
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

    private fun ensureInitialized(bass: BassNative) {
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
        val handle = preparedStream.takeIf { it != 0 && preparedRequest == request } ?: return null
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainAdjustment = null
        preparedError = null
        return handle
    }

    private fun adoptQueuedPreparedStream(
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ): Boolean {
        val bass = native ?: return false
        val queuedSource = preparedStream
        if (stream == 0 || queuedSource == 0 || preparedRequest != request || !bass.supportsMixer) return false
        currentSourceStream.takeIf { it != 0 && it != queuedSource }?.let { finishedSource ->
            bass.freeStream(finishedSource).onFailure { lastError = it.message }
        }
        currentSourceStream = queuedSource
        currentReplayGainAdjustment = preparedReplayGainAdjustment ?: ReplayGainAdjustment.off()
        crossfadeActive = false
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainAdjustment = null
        preparedError = null
        onProgressChanged(PlaybackProgress.Unknown)
        onStateChanged(PlaybackState.Playing)
        return true
    }

    private fun freePreparedStream() {
        val handle = preparedStream
        val bass = native
        if (bass != null && handle != 0) {
            runCatching { bass.removeMixerChannel(handle) }
            bass.freeStream(handle)
        }
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainAdjustment = null
        preparedError = null
    }

    private fun createStream(
        bass: BassNative,
        request: PlaybackRequest,
        decode: Boolean,
    ): Result<Int> {
        val localFile = localFileFromUrl(request.url)
        return if (localFile != null) {
            if (decode) bass.createFilePlaybackDecodeStream(localFile) else bass.createFileStream(localFile)
        } else {
            if (decode) bass.createUrlDecodeStream(request.url) else bass.createUrlStream(request.url)
        }
    }

    private fun createDirectPlayback(
        bass: BassNative,
        request: PlaybackRequest,
        currentPlaybackId: Int,
        onStateChanged: (PlaybackState) -> Unit,
    ): Result<Int> =
        createStream(bass, request, decode = false).onSuccess { handle ->
            currentSourceStream = handle
            currentReplayGainAdjustment = replayGainAdjustment(request)
            attachEndSync(bass, handle, currentPlaybackId, onStateChanged)
        }

    private fun createMixerPlayback(
        bass: BassNative,
        request: PlaybackRequest,
        currentPlaybackId: Int,
        onStateChanged: (PlaybackState) -> Unit,
    ): Result<Int> =
        runCatching {
            val source = createQueuedSource(bass, request).getOrThrow()
            val info = bass.channelInfo(source).getOrThrow()
            val mixer = bass.createMixer(
                freq = info.freq.takeIf { it > 0 } ?: 44_100,
                channels = info.chans.takeIf { it > 0 } ?: 2,
                queueSources = crossfadeDurationSeconds <= 0,
            ).getOrThrow()
            currentReplayGainAdjustment = replayGainAdjustment(request)
            applySourceReplayGain(bass, source, currentReplayGainAdjustment)
            bass.addMixerChannel(mixer, source).getOrThrow()
            currentSourceStream = source
            attachEndSync(bass, source, currentPlaybackId, onStateChanged)
            mixer
        }

    private fun createQueuedSource(
        bass: BassNative,
        request: PlaybackRequest,
    ): Result<Int> =
        createStream(bass, request, decode = true)

    private fun attachEndSync(
        bass: BassNative,
        source: Int,
        currentPlaybackId: Int,
        stateCallback: ((PlaybackState) -> Unit)? = null,
    ) {
        val callback = BassSyncCallback { _, channel, _, _ ->
            if (channel == source && isCurrentPlayback(currentPlaybackId)) {
                (stateCallback ?: onStateChanged)?.invoke(PlaybackState.Finished)
            }
        }
        endSyncCallbacks[source] = callback
        bass.setEndSync(source, callback)
            .onFailure { lastError = it.message }
    }

    private fun startCrossfade(
        bass: BassNative,
        nextSource: Int,
        nextAdjustment: ReplayGainAdjustment,
    ) {
        val currentSource = currentSourceStream
        val nextVolume = nextAdjustment.volumeFactor
        val currentVolume = currentReplayGainAdjustment.volumeFactor
        bass.setVolume(nextSource, nextVolume).getOrThrow()
        bass.addMixerChannel(stream, nextSource).getOrThrow()
        val durationMillis = crossfadeDurationSeconds * 1_000
        val nextFadeBytes = bass.secondsToBytes(nextSource, crossfadeDurationSeconds.toDouble())
        if (nextFadeBytes != null) {
            bass.setMixerVolumeEnvelope(
                nextSource,
                equalPowerEnvelope(startBytes = 0L, durationBytes = nextFadeBytes, fadeIn = true, scale = nextVolume),
            ).onFailure {
                lastError = it.message
                bass.setVolume(nextSource, 0f).onFailure { error -> lastError = error.message }
                bass.slideVolume(nextSource, nextVolume, durationMillis).onFailure { error -> lastError = error.message }
            }
        } else {
            bass.setVolume(nextSource, 0f).onFailure { lastError = it.message }
            bass.slideVolume(nextSource, nextVolume, durationMillis).onFailure { lastError = it.message }
        }
        if (currentSource != 0) {
            val currentStartBytes = bass.positionBytes(currentSource)
            val currentFadeBytes = bass.secondsToBytes(currentSource, crossfadeDurationSeconds.toDouble())
            if (currentStartBytes != null && currentFadeBytes != null) {
                bass.setMixerVolumeEnvelope(
                    currentSource,
                    equalPowerEnvelope(
                        startBytes = currentStartBytes,
                        durationBytes = currentFadeBytes,
                        fadeIn = false,
                        scale = currentVolume,
                    ),
                ).onFailure {
                    lastError = it.message
                    bass.slideVolume(currentSource, 0f, durationMillis).onFailure { error -> lastError = error.message }
                }
            } else {
                bass.slideVolume(currentSource, 0f, durationMillis).onFailure { lastError = it.message }
            }
        }
        crossfadeActive = true
    }

    private fun equalPowerEnvelope(
        startBytes: Long,
        durationBytes: Long,
        fadeIn: Boolean,
        scale: Float = 1f,
    ): List<Pair<Long, Float>> {
        if (durationBytes <= 0L) {
            return listOf(startBytes to if (fadeIn) scale else 0f)
        }
        return (0..EqualPowerEnvelopeSteps).map { step ->
            val t = step.toDouble() / EqualPowerEnvelopeSteps.toDouble()
            val value = if (fadeIn) {
                sin(t * PI / 2.0)
            } else {
                cos(t * PI / 2.0)
            }.toFloat()
            val position = startBytes + (durationBytes * t).toLong()
            position to value * scale
        }
    }

    private fun applyOutputVolume(bass: BassNative) {
        val handle = stream.takeIf { it != 0 } ?: return
        val volume = outputVolumeFactor()
        if (currentSourceStream != 0 && handle != currentSourceStream) {
            bass.setVolume(handle, volume)
                .onFailure { lastError = it.message }
        } else {
            bass.setVolume(handle, volume * currentReplayGainAdjustment.volumeFactor)
                .onFailure { lastError = it.message }
        }
    }

    private fun applySourceReplayGain(
        bass: BassNative,
        source: Int,
        adjustment: ReplayGainAdjustment,
    ) {
        bass.setVolume(source, adjustment.volumeFactor)
            .onFailure { lastError = it.message }
    }

    private fun replayGainAdjustment(request: PlaybackRequest): ReplayGainAdjustment {
        val mode = request.replayGainMode
        val replayGain = request.replayGain?.replayGain
        if (mode == ReplayGainMode.Off || replayGain == null) {
            return ReplayGainAdjustment.off(mode)
        }
        val gainDb = when (mode) {
            ReplayGainMode.Off -> null
            ReplayGainMode.Track -> replayGain.trackGainDb
            ReplayGainMode.Album -> replayGain.albumGainDb ?: replayGain.trackGainDb
        } ?: return ReplayGainAdjustment.off(mode)
        val peak = when (mode) {
            ReplayGainMode.Off -> null
            ReplayGainMode.Track -> replayGain.trackPeak
            ReplayGainMode.Album -> replayGain.albumPeak ?: replayGain.trackPeak
        }
        val rawFactor = 10.0.pow(gainDb / 20.0)
        val clippedFactor = if (peak != null && peak > 0.0 && rawFactor * peak > 1.0) {
            1.0 / peak
        } else {
            rawFactor
        }
        return ReplayGainAdjustment(
            mode = mode,
            source = request.replayGain?.source,
            gainDb = gainDb,
            peak = peak,
            volumeFactor = clippedFactor.coerceIn(0.0, MaxReplayGainFactor.toDouble()).toFloat(),
            clippingPrevented = clippedFactor < rawFactor,
        )
    }

    private fun outputVolumeFactor(): Float =
        volumePercent.coerceIn(0, 100) / 100f

    private fun visualizerFrameFor(
        bass: BassNative,
        sourceHandle: Int,
    ): PlaybackVisualizerFrame? =
        bass.fft(sourceHandle, VisualizerBandCount)
            .map { fft ->
                PlaybackVisualizerFrame(
                    bands = fft.toVisualizerBands(),
                    timestampMillis = System.currentTimeMillis(),
                )
            }
            .onFailure { lastError = it.message }
            .getOrNull()

    private fun seekCurrentSource(bass: BassNative, seconds: Double) {
        val sourceHandle = currentSourceStream.takeIf { it != 0 } ?: stream
        if (sourceHandle != 0) {
            bass.seek(sourceHandle, seconds)
                .onFailure { lastError = it.message }
        }
    }

    private fun freeAllStreams(bass: BassNative) {
        val handles = listOf(stream, currentSourceStream, preparedStream)
            .filter { it != 0 }
            .distinct()
        handles.forEach { handle ->
            runCatching { bass.removeMixerChannel(handle) }
            bass.freeStream(handle).onFailure { lastError = it.message }
        }
        crossfadeActive = false
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainAdjustment = null
        preparedError = null
        currentVisualizerFrame = null
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

private fun bassVersionLabel(version: Int): String {
    val major = version ushr 24 and 0xff
    val minor = version ushr 16 and 0xff
    val revision = version ushr 8 and 0xff
    val build = version and 0xff
    return "$major.$minor.$revision.$build"
}

private fun bassActiveLabel(active: Int): String =
    when (active) {
        BassActive.Stopped -> "Stopped"
        BassActive.Playing -> "Playing"
        BassActive.Stalled -> "Stalled"
        BassActive.Paused -> "Paused"
        else -> "Unknown ($active)"
    }

private data class ReplayGainAdjustment(
    val mode: ReplayGainMode,
    val source: ReplayGainSource?,
    val gainDb: Double?,
    val peak: Double?,
    val volumeFactor: Float,
    val clippingPrevented: Boolean,
) {
    val label: String
        get() = if (gainDb == null) {
            "Off"
        } else {
            "${gainDb.formatDb()} dB -> ${volumeFactor.formatFactor()}x"
        }

    val clippingGuardLabel: String
        get() = when {
            gainDb == null -> "Off"
            clippingPrevented -> "Peak ${peak?.formatPeak() ?: "unknown"} limited boost"
            else -> "No clipping risk detected"
        }

    companion object {
        fun off(mode: ReplayGainMode = ReplayGainMode.Off): ReplayGainAdjustment =
            ReplayGainAdjustment(
                mode = mode,
                source = null,
                gainDb = null,
                peak = null,
                volumeFactor = 1f,
                clippingPrevented = false,
            )
    }
}

private fun Double.formatDb(): String =
    "%+.2f".format(this)

private fun Float.formatFactor(): String =
    "%.3f".format(this)

private fun Double.formatPeak(): String =
    "%.6f".format(this)

private fun FloatArray.toVisualizerBands(): List<Float> {
    if (isEmpty()) return emptyList()
    val usable = drop(1)
    if (usable.isEmpty()) return emptyList()
    val bucketSize = (usable.size / VisualizerBandCount).coerceAtLeast(1)
    return (0 until VisualizerBandCount).map { bucket ->
        val start = bucket * bucketSize
        if (start >= usable.size) {
            0f
        } else {
            val end = minOf(start + bucketSize, usable.size)
            val peak = usable.subList(start, end).maxOrNull() ?: 0f
            (peak * VisualizerGain).coerceIn(0f, 1f)
        }
    }
}

private const val EqualPowerEnvelopeSteps = 8
private const val MaxReplayGainFactor = 4f
private const val VisualizerBandCount = 32
private const val VisualizerGain = 12f
