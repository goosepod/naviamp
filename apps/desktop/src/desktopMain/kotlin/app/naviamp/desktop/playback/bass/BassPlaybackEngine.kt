package app.naviamp.desktop.playback.bass

import app.naviamp.desktop.playback.PlaybackEngineDiagnostics
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

class BassPlaybackEngine(
    private val nativeResult: Result<BassNative> = BassNative.load(),
) : PlaybackEngine, PlaybackEngineDiagnostics {
    override val name: String = "BASS"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = false
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = false
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private val native: BassNative? = nativeResult.getOrNull()
    private val loadError: Throwable? = nativeResult.exceptionOrNull()
    private val plugins: List<BassPlugin> = native?.loadAvailablePlugins().orEmpty()
    private var job: Job? = null
    private var stream: Int = 0
    private var playbackId: Int = 0
    private var volumePercent: Int = 100
    private var initialized = false
    private var internetStreamsConfigured = false
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var lastRequestUrl: String? = null
    private var lastError: String? = loadError?.message

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        stop()
        val currentPlaybackId = nextPlaybackId()
        lastRequestUrl = request.url
        this.onStateChanged = onStateChanged
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
                val handle = createStream(bass, request)
                    .getOrThrow()
                stream = handle
                bass.setVolume(handle, volumePercent / 100f)
                    .onFailure { lastError = it.message }
                request.startPositionSeconds
                    ?.takeIf { it > 0.0 }
                    ?.let { bass.seek(handle, it).onFailure { error -> lastError = error.message } }
                bass.play(handle)
                    .getOrThrow()
                onStateChanged(PlaybackState.Playing)

                var lastProgress = PlaybackProgress.Unknown
                var lastMetadata = PlaybackStreamMetadata()
                while (isCurrentPlayback(currentPlaybackId) && bass.activeState(handle) != BassActive.Stopped) {
                    val progress = PlaybackProgress(
                        positionSeconds = bass.positionSeconds(handle),
                        durationSeconds = bass.durationSeconds(handle),
                    )
                    if (progress != lastProgress) {
                        lastProgress = progress
                        onProgressChanged(progress)
                    }
                    val metadata = PlaybackStreamMetadata.fromProperties(bass.streamMetadata(handle))
                    if (metadata != lastMetadata) {
                        lastMetadata = metadata
                        onMetadataChanged(metadata)
                    }
                    delay(500)
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
                    stream.takeIf { it != 0 }?.let { handle ->
                        bass.freeStream(handle)
                    }
                    stream = 0
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
        val handle = stream
        val bass = native ?: return
        if (handle != 0) {
            bass.seek(handle, positionSeconds)
                .onFailure { lastError = it.message }
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
        playbackId += 1
        job?.cancel()
        job = null
        val handle = stream
        val bass = native
        if (bass != null && handle != 0) {
            bass.stop(handle)
            bass.freeStream(handle)
        }
        stream = 0
        onStateChanged = null
    }

    override fun statsRows(): List<Pair<String, String>> =
        listOf(
            "BASS load state" to if (native != null) "Loaded" else "Unavailable",
            "BASS version" to (native?.version?.let(::bassVersionLabel) ?: "Unknown"),
            "BASS directory" to (native?.libraryDirectory?.absolutePath ?: "Not resolved"),
            "Loaded plugins" to plugins.filter { it.loaded }.joinToString(", ") { it.stem }.ifBlank { "None" },
            "Failed plugins" to plugins.filterNot { it.loaded }.joinToString(", ") { plugin ->
                "${plugin.stem} (${plugin.errorCode?.let(::bassErrorMessage) ?: "unknown"})"
            }.ifBlank { "None" },
            "Active state" to native?.let { bass ->
                stream.takeIf { it != 0 }?.let { bassActiveLabel(bass.activeState(it)) }
            }.orEmpty().ifBlank { "No stream" },
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

    private fun createStream(
        bass: BassNative,
        request: PlaybackRequest,
    ): Result<Int> {
        val localFile = localFileFromUrl(request.url)
        return if (localFile != null) {
            bass.createFileStream(localFile)
        } else {
            bass.createUrlStream(request.url)
        }
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
