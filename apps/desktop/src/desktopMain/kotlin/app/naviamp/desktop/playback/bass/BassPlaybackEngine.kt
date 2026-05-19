package app.naviamp.desktop.playback.bass

import app.naviamp.desktop.playback.PlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class BassPlaybackEngine(
    private val nativeResult: Result<BassNative> = BassNative.load(),
) : QueueAwarePlaybackEngine, PlaybackEngineDiagnostics {
    private val native: BassNative? = nativeResult.getOrNull()
    private val loadError: Throwable? = nativeResult.exceptionOrNull()

    override val name: String = "BASS"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = native?.supportsMixer == true
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = false
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
    private var preparedError: String? = null
    private var endSyncCallbacks: MutableMap<Int, BassSyncCallback> = mutableMapOf()

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
                bass.setVolume(playbackHandle, volumePercent / 100f)
                    .onFailure { lastError = it.message }
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
            bass.setVolume(handle, volumePercent / 100f)
                .onFailure { lastError = it.message }
        }
    }

    override fun stop() {
        freePreparedStream()
        stopActiveStream()
        onStateChanged = null
    }

    override fun setCrossfadeDuration(seconds: Int) {
        // BASS gapless preloading does not use crossfade yet. Phase 7 will wire BASSmix fades here.
    }

    override fun prepareNext(request: PlaybackRequest) {
        val bass = native ?: return
        if (preparedRequest == request && preparedStream != 0) return
        freePreparedStream()
        runCatching {
            ensureInitialized(bass)
            if (stream != 0 && bass.supportsMixer) {
                createQueuedSource(bass, request).getOrThrow().also { source ->
                    bass.addMixerChannel(stream, source).getOrThrow()
                    attachEndSync(bass, source, playbackId)
                }
            } else {
                createStream(bass, request, decode = false).getOrThrow()
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
        endSyncCallbacks.clear()
    }

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
        preparedStream = 0
        preparedRequest = null
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
            ).getOrThrow()
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
        preparedStream = 0
        preparedRequest = null
        preparedError = null
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
