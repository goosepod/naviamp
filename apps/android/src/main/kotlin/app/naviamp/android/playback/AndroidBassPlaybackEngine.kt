package app.naviamp.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
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
) : AndroidPlaybackEngine, QueueAwarePlaybackEngine, VisualizerPlaybackEngine {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var stream: Int = 0
    private var currentSourceStream: Int = 0
    private var preparedStream: Int = 0
    private var preparedRequest: PlaybackRequest? = null
    private var preparedReplayGainFactor: Float = 1f
    private var crossfadeDurationSeconds: Int = 0
    private var progressJob: Job? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var onProgressChanged: ((PlaybackProgress) -> Unit)? = null
    private var onMetadataChanged: ((PlaybackStreamMetadata) -> Unit)? = null
    private var notificationMetadata = AndroidPlaybackNotificationMetadata()
    private var volumePercent: Int = 100
    private var replayGainFactor: Float = 1f
    private var tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedForTransientFocusLoss = false
    private val playbackWakeLock: PowerManager.WakeLock by lazy {
        appContext.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Naviamp:Playback",
        ).apply {
            setReferenceCounted(false)
        }
    }
    @Volatile
    private var currentVisualizerFrame: PlaybackVisualizerFrame? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedForTransientFocusLoss) {
                    pausedForTransientFocusLoss = false
                    resumeAfterFocusGain()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                pausedForTransientFocusLoss = AndroidPlaybackNotificationControls.isPlaying
                pauseForFocusLoss()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedForTransientFocusLoss = false
                pauseForFocusLoss()
                abandonAudioFocus()
            }
        }
    }

    override val name: String = "BASS Android"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = true
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
        this.onStateChanged = onStateChanged
        this.onProgressChanged = onProgressChanged
        this.onMetadataChanged = onMetadataChanged
        if (!requestAudioFocus()) {
            Log.w(Tag, "Audio focus request denied before playback")
            onStateChanged(PlaybackState.Error("Audio focus is currently held by another app."))
            AndroidPlaybackNotificationControls.isPlaying = false
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            return
        }
        if (adoptPreparedStream(scope, request, onStateChanged, onProgressChanged)) {
            acquirePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = true
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            return
        }
        stopStreamOnly()
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
                val handle = if (request.mediaId != null) {
                    createMixerPlayback(request)
                } else {
                    createStream(request.url, decode = false)
                }
                Log.i(Tag, "BASS stream handle=$handle source=$currentSourceStream error=${bass.lastErrorCode}")
                check(handle != 0) { errorMessage("BASS stream creation failed") }
                if (stream == 0) {
                    stream = handle
                }
                applyVolume()
                request.startPositionSeconds?.takeIf { it > 0.0 }?.let { seek(it) }
                check(bass.play(handle)) { errorMessage("BASS_ChannelPlay failed") }
                Log.i(Tag, "BASS playback started handle=$handle")
                acquirePlaybackWakeLock()
                onStateChanged(PlaybackState.Playing)
                startProgressPolling(scope, handle)
            } catch (error: Throwable) {
                val message = error.message ?: "BASS playback failed."
                Log.w(Tag, message, error)
                releasePlaybackWakeLock()
                onStateChanged(PlaybackState.Error(message))
                AndroidPlaybackNotificationControls.isPlaying = false
                AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            }
        }
    }

    override fun pause() {
        val handle = stream
        if (handle != 0 && bass.pause(handle)) {
            pausedForTransientFocusLoss = false
            abandonAudioFocus()
            releasePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = false
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    override fun resume() {
        val handle = stream
        if (handle != 0 && requestAudioFocus() && bass.play(handle)) {
            acquirePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = true
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    override fun seek(positionSeconds: Double) {
        freePreparedStream()
        val handle = currentSourceStream.takeIf { it != 0 } ?: stream.takeIf { it != 0 } ?: return
        val success = bass.seek(handle, positionSeconds)
        Log.i(
            Tag,
            "BASS seek requested handle=$handle seconds=$positionSeconds success=$success error=${bass.lastErrorCode}",
        )
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        applyVolume()
    }

    override fun stop() {
        freePreparedStream()
        stopStreamOnly()
        pausedForTransientFocusLoss = false
        abandonAudioFocus()
        releasePlaybackWakeLock()
        AndroidPlaybackNotificationControls.isPlaying = false
        AndroidPlaybackForegroundService.stop(appContext)
        onProgressChanged?.invoke(PlaybackProgress.Unknown)
        onStateChanged?.invoke(PlaybackState.Stopped)
    }

    override fun release() {
        stopStreamOnly()
        pausedForTransientFocusLoss = false
        abandonAudioFocus()
        releasePlaybackWakeLock()
        AndroidPlaybackNotificationControls.clear()
        AndroidPlaybackForegroundService.stop(appContext)
        bass.free()
        onStateChanged = null
        onProgressChanged = null
        onMetadataChanged = null
    }

    override fun visualizerFrame(): PlaybackVisualizerFrame? {
        val handle = stream.takeIf { it != 0 } ?: return null
        return bass.fft(handle, VisualizerBandCount).toVisualizerFrame()
            .also { currentVisualizerFrame = it }
    }

    override fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSeconds = seconds.coerceIn(0, 12)
    }

    override fun prepareNext(request: PlaybackRequest) {
        if (preparedRequest == request && preparedStream != 0) return
        freePreparedStream()
        runCatching {
            check(bass.init()) { errorMessage("BASS_Init failed") }
            val mixer = stream.takeIf { it != 0 } ?: return
            currentSourceStream.takeIf { it != 0 } ?: return
            val source = createStream(request.url, decode = true)
            check(source != 0) { errorMessage("BASS next stream creation failed") }
            val nextReplayGain = request.replayGainFactor()
            bass.setVolume(source, if (crossfadeDurationSeconds > 0) 0f else nextReplayGain)
            check(bass.addMixerChannel(mixer, source)) { errorMessage("BASS_Mixer_StreamAddChannel failed") }
            if (crossfadeDurationSeconds > 0) {
                val durationMillis = crossfadeDurationSeconds * 1_000
                bass.slideVolume(source, nextReplayGain, durationMillis)
                currentSourceStream.takeIf { it != 0 }?.let { bass.slideVolume(it, 0f, durationMillis) }
            }
            preparedReplayGainFactor = nextReplayGain
            source
        }.onSuccess { handle ->
            preparedStream = handle
            preparedRequest = request
        }.onFailure { error ->
            Log.w(Tag, error.message ?: "Could not prepare next BASS stream.", error)
            preparedStream = 0
            preparedRequest = null
            preparedReplayGainFactor = 1f
        }
    }

    private fun createMixerPlayback(request: PlaybackRequest): Int {
        val source = createStream(request.url, decode = true)
        check(source != 0) { errorMessage("BASS decode stream creation failed") }
        currentSourceStream = source
        bass.setVolume(source, replayGainFactor)
        val mixer = bass.createMixer(
            frequency = DefaultMixerFrequency,
            channels = DefaultMixerChannels,
            queueSources = crossfadeDurationSeconds <= 0,
        )
        check(mixer != 0) { errorMessage("BASS_Mixer_StreamCreate failed") }
        check(bass.addMixerChannel(mixer, source)) { errorMessage("BASS_Mixer_StreamAddChannel failed") }
        stream = mixer
        return mixer
    }

    private fun createStream(url: String, decode: Boolean): Int {
        val file = localFileFromUrl(url)
        return if (file != null) {
            if (decode) bass.createFileDecodeStream(file.absolutePath) else bass.createFileStream(file.absolutePath)
        } else {
            if (decode) bass.createUrlDecodeStream(url) else bass.createUrlStream(url)
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
                val progressHandle = currentSourceStream.takeIf { it != 0 } ?: handle
                val progress = PlaybackProgress(
                    positionSeconds = bass.positionSeconds(progressHandle),
                    durationSeconds = bass.durationSeconds(progressHandle),
                )
                onProgressChanged?.invoke(progress)
                val metadata = PlaybackStreamMetadata.fromProperties(bass.streamTags(progressHandle).toStreamProperties())
                if (metadata != lastMetadata) {
                    lastMetadata = metadata
                    onMetadataChanged?.invoke(metadata)
                }
                if (currentSourceStream != 0 && bass.activeState(currentSourceStream) == BassActiveStopped && isAtEnd(progress)) {
                    Log.i(Tag, "BASS source reached end position=${progress.positionSeconds} duration=${progress.durationSeconds}")
                    handlePlaybackFinished()
                    onStateChanged?.invoke(PlaybackState.Finished)
                    return@launch
                }
                when (active) {
                    BassActiveStopped -> {
                        if (isAtEnd(progress)) {
                            Log.i(Tag, "BASS stream stopped position=${progress.positionSeconds} duration=${progress.durationSeconds}")
                            handlePlaybackFinished()
                            onStateChanged?.invoke(PlaybackState.Finished)
                            return@launch
                        }
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
            freeHandles(handle, currentSourceStream, preparedStream)
        }
        stream = 0
        currentSourceStream = 0
        currentVisualizerFrame = null
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainFactor = 1f
    }

    private fun handlePlaybackFinished() {
        releasePlaybackWakeLock()
        AndroidPlaybackNotificationControls.isPlaying = false
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    private fun pauseForFocusLoss() {
        val handle = stream
        if (handle != 0 && bass.pause(handle)) {
            releasePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = false
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    private fun resumeAfterFocusGain() {
        val handle = stream
        if (handle != 0 && bass.play(handle)) {
            acquirePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = true
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(true)
                .build()
                .also { audioFocusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun acquirePlaybackWakeLock() {
        runCatching {
            if (!playbackWakeLock.isHeld) {
                playbackWakeLock.acquire()
                Log.i(Tag, "Playback wake lock acquired")
            }
        }.onFailure { error ->
            Log.w(Tag, "Could not acquire playback wake lock", error)
        }
    }

    private fun releasePlaybackWakeLock() {
        runCatching {
            if (playbackWakeLock.isHeld) {
                playbackWakeLock.release()
                Log.i(Tag, "Playback wake lock released")
            }
        }.onFailure { error ->
            Log.w(Tag, "Could not release playback wake lock", error)
        }
    }

    private fun isAtEnd(progress: PlaybackProgress): Boolean {
        val position = progress.positionSeconds ?: return false
        val duration = progress.durationSeconds ?: return false
        return duration - position <= FinishedPositionToleranceSeconds
    }

    private fun applyVolume() {
        val userVolume = volumePercent / 100f
        stream.takeIf { it != 0 }?.let { handle ->
            if (currentSourceStream != 0 && handle != currentSourceStream) {
                bass.setVolume(handle, userVolume)
                bass.setVolume(currentSourceStream, replayGainFactor)
            } else {
                bass.setVolume(handle, userVolume * replayGainFactor)
            }
        }
    }

    private fun adoptPreparedStream(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ): Boolean {
        val source = preparedStream.takeIf { it != 0 && preparedRequest == request } ?: return false
        if (stream == 0) return false
        currentSourceStream.takeIf { it != 0 && it != source }?.let { bass.freeStream(it) }
        currentSourceStream = source
        replayGainFactor = preparedReplayGainFactor
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainFactor = 1f
        onProgressChanged(PlaybackProgress.Unknown)
        onStateChanged(PlaybackState.Playing)
        startProgressPolling(scope, stream)
        return true
    }

    private fun freePreparedStream() {
        preparedStream.takeIf { it != 0 }?.let { bass.freeStream(it) }
        preparedStream = 0
        preparedRequest = null
        preparedReplayGainFactor = 1f
    }

    private fun freeHandles(vararg handles: Int) {
        handles.filter { it != 0 }.toSet().forEach { bass.freeStream(it) }
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
private const val DefaultMixerFrequency = 44_100
private const val DefaultMixerChannels = 2
private const val FinishedPositionToleranceSeconds = 0.75
private const val Tag = "NaviampBass"
