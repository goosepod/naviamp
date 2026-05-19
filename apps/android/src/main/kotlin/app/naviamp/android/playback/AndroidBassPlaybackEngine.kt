package app.naviamp.android.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.pow

class AndroidBassPlaybackEngine(
    context: Context,
    private val bass: AndroidBassJni,
) : AndroidPlaybackEngine, VisualizerPlaybackEngine {
    private val appContext = context.applicationContext
    private var stream: Int = 0
    private var progressJob: Job? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var onProgressChanged: ((PlaybackProgress) -> Unit)? = null
    private var onMetadataChanged: ((PlaybackStreamMetadata) -> Unit)? = null
    private var notificationMetadata = AndroidPlaybackNotificationMetadata()
    private var volumePercent: Int = 100
    private var replayGainFactor: Float = 1f
    private var tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings()
    @Volatile
    private var currentVisualizerFrame: PlaybackVisualizerFrame? = null

    override val name: String = "BASS Android"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = false
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true
    override val supportsVisualizer: Boolean = true

    override fun applyTlsSettings(tlsSettings: NavidromeTlsSettings) {
        this.tlsSettings = tlsSettings
        AndroidPlaybackTls.applyDefaults(tlsSettings)
    }

    override fun updateNotificationMetadata(
        title: String?,
        subtitle: String?,
        coverArtUrl: String?,
    ) {
        notificationMetadata = AndroidPlaybackNotificationMetadata(
            title = title,
            subtitle = subtitle,
            coverArtUrl = coverArtUrl,
        )
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        stopStreamOnly()
        this.onStateChanged = onStateChanged
        this.onProgressChanged = onProgressChanged
        this.onMetadataChanged = onMetadataChanged
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)
        AndroidPlaybackNotificationControls.isPlaying = true
        AndroidPlaybackForegroundService.start(appContext, notificationMetadata)

        scope.launch(Dispatchers.IO) {
            try {
                check(bass.init()) { errorMessage("BASS_Init failed") }
                val verifyNet = !tlsSettings.insecureSkipTlsVerification
                Log.i(Tag, "Opening BASS stream verifyNet=$verifyNet url=${request.url.sanitizedForLog()}")
                bass.setVerifyNet(verifyNet)
                replayGainFactor = request.replayGainFactor()
                val handle = createStream(request.url)
                Log.i(Tag, "BASS stream handle=$handle error=${bass.lastErrorCode}")
                check(handle != 0) { errorMessage("BASS stream creation failed") }
                stream = handle
                applyVolume()
                request.startPositionSeconds?.takeIf { it > 0.0 }?.let { bass.seek(handle, it) }
                check(bass.play(handle)) { errorMessage("BASS_ChannelPlay failed") }
                Log.i(Tag, "BASS playback started handle=$handle")
                onStateChanged(PlaybackState.Playing)
                startProgressPolling(scope, handle)
            } catch (error: Throwable) {
                val message = error.message ?: "BASS playback failed."
                Log.w(Tag, message, error)
                onStateChanged(PlaybackState.Error(message))
                AndroidPlaybackNotificationControls.isPlaying = false
                AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            }
        }
    }

    override fun pause() {
        val handle = stream
        if (handle != 0 && bass.pause(handle)) {
            AndroidPlaybackNotificationControls.isPlaying = false
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    override fun resume() {
        val handle = stream
        if (handle != 0 && bass.play(handle)) {
            AndroidPlaybackNotificationControls.isPlaying = true
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    override fun seek(positionSeconds: Double) {
        stream.takeIf { it != 0 }?.let { bass.seek(it, positionSeconds) }
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        applyVolume()
    }

    override fun stop() {
        stopStreamOnly()
        AndroidPlaybackNotificationControls.isPlaying = false
        AndroidPlaybackForegroundService.stop(appContext)
        onProgressChanged?.invoke(PlaybackProgress.Unknown)
        onStateChanged?.invoke(PlaybackState.Stopped)
    }

    override fun release() {
        stopStreamOnly()
        AndroidPlaybackNotificationControls.clear()
        AndroidPlaybackForegroundService.stop(appContext)
        bass.free()
        onStateChanged = null
        onProgressChanged = null
        onMetadataChanged = null
    }

    override fun visualizerFrame(): PlaybackVisualizerFrame? =
        currentVisualizerFrame

    private fun createStream(url: String): Int {
        val file = localFileFromUrl(url)
        return if (file != null) {
            bass.createFileStream(file.absolutePath)
        } else {
            bass.createUrlStream(url)
        }
    }

    private fun startProgressPolling(
        scope: CoroutineScope,
        handle: Int,
    ) {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastMetadata = PlaybackStreamMetadata()
            while (isActive && stream == handle) {
                val active = bass.activeState(handle)
                onProgressChanged?.invoke(
                    PlaybackProgress(
                        positionSeconds = bass.positionSeconds(handle),
                        durationSeconds = bass.durationSeconds(handle),
                    ),
                )
                val metadata = PlaybackStreamMetadata.fromProperties(bass.streamTags(handle).toStreamProperties())
                if (metadata != lastMetadata) {
                    lastMetadata = metadata
                    onMetadataChanged?.invoke(metadata)
                }
                currentVisualizerFrame = bass.fft(handle, VisualizerBandCount).toVisualizerFrame()
                when (active) {
                    BassActiveStopped -> {
                        onStateChanged?.invoke(PlaybackState.Finished)
                        return@launch
                    }
                    BassActivePlaying -> onStateChanged?.invoke(PlaybackState.Playing)
                    BassActiveStalled -> onStateChanged?.invoke(PlaybackState.Loading)
                    BassActivePaused -> onStateChanged?.invoke(PlaybackState.Paused)
                }
                delay(100)
            }
        }
    }

    private fun stopStreamOnly() {
        progressJob?.cancel()
        progressJob = null
        val handle = stream
        if (handle != 0) {
            bass.stop(handle)
            bass.freeStream(handle)
        }
        stream = 0
        currentVisualizerFrame = null
    }

    private fun applyVolume() {
        stream.takeIf { it != 0 }?.let { handle ->
            bass.setVolume(handle, (volumePercent / 100f) * replayGainFactor)
        }
    }

    private fun PlaybackRequest.replayGainFactor(): Float {
        val replayGain = replayGain?.replayGain ?: return 1f
        val gainDb = when (replayGainMode) {
            ReplayGainMode.Off -> null
            ReplayGainMode.Track -> replayGain.trackGainDb
            ReplayGainMode.Album -> replayGain.albumGainDb ?: replayGain.trackGainDb
        } ?: return 1f
        val peak = when (replayGainMode) {
            ReplayGainMode.Off -> null
            ReplayGainMode.Track -> replayGain.trackPeak
            ReplayGainMode.Album -> replayGain.albumPeak ?: replayGain.trackPeak
        }
        val raw = 10.0.pow(gainDb / 20.0)
        val limited = if (peak != null && peak > 0.0 && raw * peak > 1.0) 1.0 / peak else raw
        return limited.coerceIn(0.0, 4.0).toFloat()
    }

    private fun errorMessage(prefix: String): String =
        "$prefix: BASS error ${bass.lastErrorCode}"
}

private fun localFileFromUrl(url: String): File? =
    runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") File(requireNotNull(uri.path)) else null
    }.getOrNull()

private fun String.sanitizedForLog(): String =
    replace(Regex("""([?&](?:t|s|p)=)[^&]+"""), "$1***")

private fun Array<String>.toStreamProperties(): Map<String, String> =
    buildMap {
        this@toStreamProperties.forEach { tag ->
            val equalsIndex = tag.indexOf('=').takeIf { it > 0 }
            val colonIndex = tag.indexOf(':').takeIf { it > 0 }
            val separator = equalsIndex ?: colonIndex ?: return@forEach
            if (separator > 0) {
                val key = tag.take(separator).trim().trim('\'', '"')
                val value = tag.drop(separator + 1).trim().trim('\'', '"').icyStreamTitleValue()
                if (key.isNotBlank() && value.isNotBlank()) {
                    put(key, value)
                }
            }
        }
    }

private fun String.icyStreamTitleValue(): String {
    val key = "StreamTitle='"
    val start = indexOf(key)
    if (start < 0) return this
    val titleStart = start + key.length
    val titleEnd = indexOf("';", titleStart).takeIf { it >= 0 } ?: indexOf("'", titleStart)
    return if (titleEnd > titleStart) substring(titleStart, titleEnd).trim() else this
}

private fun FloatArray.toVisualizerFrame(): PlaybackVisualizerFrame? {
    if (isEmpty()) return null
    val usable = drop(1)
    if (usable.isEmpty()) return null
    val bucketSize = (usable.size / VisualizerBandCount).coerceAtLeast(1)
    return PlaybackVisualizerFrame(
        bands = (0 until VisualizerBandCount).map { bucket ->
            val start = bucket * bucketSize
            if (start >= usable.size) {
                0f
            } else {
                val end = minOf(start + bucketSize, usable.size)
                val peak = usable.subList(start, end).maxOrNull() ?: 0f
                (peak * VisualizerGain).coerceIn(0f, 1f)
            }
        },
        timestampMillis = System.currentTimeMillis(),
    )
}

private const val BassActiveStopped = 0
private const val BassActivePlaying = 1
private const val BassActiveStalled = 2
private const val BassActivePaused = 3
private const val VisualizerBandCount = 32
private const val VisualizerGain = 12f
private const val Tag = "NaviampBass"
