package app.naviamp.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.clearPreparedPlaybackMetadata
import app.naviamp.domain.playback.crossfadeDurationMillis
import app.naviamp.domain.playback.failedPreparedPlaybackMetadata
import app.naviamp.domain.playback.normalizedCrossfadeDurationSeconds
import app.naviamp.domain.playback.playbackReplayGainAdjustment
import app.naviamp.domain.playback.shouldReusePreparedPlayback
import app.naviamp.domain.playback.shouldQueueMixerSources
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AndroidBassPlaybackEngine(
    context: Context,
    private val bass: BassAudioBackend,
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
    private var duckedForFocusLoss = false
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
        Log.i(Tag, "Audio focus changed=${focusChange.audioFocusChangeName()}")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (duckedForFocusLoss) {
                    duckedForFocusLoss = false
                    applyVolume()
                    Log.i(Tag, "Restored playback volume after ducking")
                }
                if (pausedForTransientFocusLoss) {
                    pausedForTransientFocusLoss = false
                    resumeAfterFocusGain()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedForTransientFocusLoss = AndroidPlaybackNotificationControls.isPlaying
                pauseForFocusLoss("transient audio focus loss")
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (AndroidPlaybackNotificationControls.isPlaying) {
                    duckedForFocusLoss = true
                    applyVolume()
                    Log.i(Tag, "Ducked playback for transient audio focus loss")
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedForTransientFocusLoss = false
                duckedForFocusLoss = false
                pauseForFocusLoss("audio focus loss")
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
                bass.init().getOrThrow()
                val verifyNet = !tlsSettings.insecureSkipTlsVerification
                Log.i(Tag, "Opening BASS stream verifyNet=$verifyNet url=${request.url.sanitizedForLog()}")
                bass.setVerifyNet(verifyNet)
                replayGainFactor = playbackReplayGainAdjustment(request).volumeFactor
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
                val startPositionSeconds = request.startPositionSeconds?.takeIf { it > 0.0 }
                val seekedBeforePlay = startPositionSeconds?.let { seekStreamPosition(it) } ?: false
                if (startPositionSeconds != null && !seekedBeforePlay) {
                    setPlaybackMuted(true)
                }
                check(bass.play(handle)) { errorMessage("BASS_ChannelPlay failed") }
                if (startPositionSeconds != null && !seekedBeforePlay) {
                    val seekedAfterPlay = retryStartSeek(handle, startPositionSeconds)
                    setPlaybackMuted(false)
                    if (!seekedAfterPlay) {
                        error("BASS start seek did not apply seconds=$startPositionSeconds")
                    }
                }
                Log.i(Tag, "BASS playback started handle=$handle")
                acquirePlaybackWakeLock()
                onStateChanged(PlaybackState.Playing)
                startProgressPolling(scope, handle)
            } catch (error: Throwable) {
                val message = error.message ?: "BASS playback failed."
                Log.w(Tag, message, error)
                stopStreamOnly()
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
            duckedForFocusLoss = false
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
            duckedForFocusLoss = false
            applyVolume()
            acquirePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = true
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    override fun seek(positionSeconds: Double) {
        freePreparedStream()
        seekStreamPosition(positionSeconds)
    }

    private fun seekStreamPosition(positionSeconds: Double): Boolean {
        val handle = currentSourceStream.takeIf { it != 0 } ?: stream.takeIf { it != 0 } ?: return false
        val success = bass.seek(handle, positionSeconds)
        Log.i(
            Tag,
            "BASS seek requested handle=$handle seconds=$positionSeconds success=$success error=${bass.lastErrorCode}",
        )
        return success
    }

    private suspend fun retryStartSeek(handle: Int, positionSeconds: Double): Boolean {
        repeat(StartSeekRetryCount) { attempt ->
            if (stream != handle) return false
            delay(StartSeekRetryDelayMillis)
            Log.i(Tag, "Retrying BASS start seek attempt=${attempt + 1} seconds=$positionSeconds")
            if (seekStreamPosition(positionSeconds)) {
                return true
            }
        }
        return false
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        applyVolume()
    }

    override fun stop() {
        freePreparedStream()
        stopStreamOnly()
        pausedForTransientFocusLoss = false
        duckedForFocusLoss = false
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
        duckedForFocusLoss = false
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
        crossfadeDurationSeconds = normalizedCrossfadeDurationSeconds(seconds)
    }

    override fun prepareNext(request: PlaybackRequest) {
        if (shouldReusePreparedPlayback(preparedRequest, preparedStream != 0, request)) return
        freePreparedStream()
        runCatching {
            bass.init().getOrThrow()
            val mixer = stream.takeIf { it != 0 } ?: return
            currentSourceStream.takeIf { it != 0 } ?: return
            val source = createStream(request.url, decode = true)
            check(source != 0) { errorMessage("BASS next stream creation failed") }
            val nextReplayGain = playbackReplayGainAdjustment(request).volumeFactor
            bass.setVolume(source, if (crossfadeDurationSeconds > 0) 0f else nextReplayGain)
            check(bass.addMixerChannel(mixer, source)) { errorMessage("BASS_Mixer_StreamAddChannel failed") }
            if (crossfadeDurationSeconds > 0) {
                val durationMillis = crossfadeDurationMillis(crossfadeDurationSeconds)
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
            val reset = failedPreparedPlaybackMetadata(error)
            preparedStream = 0
            preparedRequest = reset.request
            preparedReplayGainFactor = reset.replayGainFactor
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
            queueSources = shouldQueueMixerSources(crossfadeDurationSeconds),
        ).getOrNull()?.value ?: 0
        check(mixer != 0) { errorMessage("BASS_Mixer_StreamCreate failed") }
        check(bass.addMixerChannel(mixer, source)) { errorMessage("BASS_Mixer_StreamAddChannel failed") }
        stream = mixer
        return mixer
    }

    private fun createStream(url: String, decode: Boolean): Int {
        val file = localFileFromUrl(url)
        return if (file != null) {
            if (decode) {
                bass.createFileDecodeStream(file.absolutePath)
            } else {
                bass.createFileStream(file.absolutePath)
            }.getOrNull()?.value ?: 0
        } else {
            if (decode) {
                bass.createUrlDecodeStream(url)
            } else {
                bass.createUrlStream(url)
            }.getOrNull()?.value ?: 0
        }
    }

    private fun startProgressPolling(
        scope: CoroutineScope,
        handle: Int,
    ) {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastMetadata = PlaybackStreamMetadata()
            var lastActiveState: Int? = null
            while (isActive && stream == handle) {
                val active = bass.activeState(handle)
                val progressHandle = currentSourceStream.takeIf { it != 0 } ?: handle
                val progress = PlaybackProgress(
                    positionSeconds = bass.positionSeconds(progressHandle),
                    durationSeconds = bass.durationSeconds(progressHandle),
                )
                if (active != lastActiveState) {
                    lastActiveState = active
                    Log.i(
                        Tag,
                        "BASS active=${active.bassActiveStateName()} handle=$handle source=$progressHandle " +
                            "position=${progress.positionSeconds} duration=${progress.durationSeconds}",
                    )
                }
                onProgressChanged?.invoke(progress)
                val metadata = PlaybackStreamMetadata.fromProperties(bass.streamMetadata(progressHandle))
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

    private fun pauseForFocusLoss(reason: String) {
        val handle = stream
        if (handle != 0 && bass.pause(handle)) {
            duckedForFocusLoss = false
            applyVolume()
            Log.i(Tag, "Paused playback for $reason")
            releasePlaybackWakeLock()
            AndroidPlaybackNotificationControls.isPlaying = false
            AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    private fun resumeAfterFocusGain() {
        val handle = stream
        if (handle != 0 && bass.play(handle)) {
            duckedForFocusLoss = false
            applyVolume()
            Log.i(Tag, "Resumed playback after audio focus gain")
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
                .setWillPauseWhenDucked(false)
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
        val userVolume = (volumePercent / 100f) * if (duckedForFocusLoss) FocusDuckVolumeFactor else 1f
        stream.takeIf { it != 0 }?.let { handle ->
            if (currentSourceStream != 0 && handle != currentSourceStream) {
                bass.setVolume(handle, userVolume)
                bass.setVolume(currentSourceStream, replayGainFactor)
            } else {
                bass.setVolume(handle, userVolume * replayGainFactor)
            }
        }
    }

    private fun setPlaybackMuted(muted: Boolean) {
        if (!muted) {
            applyVolume()
            return
        }
        stream.takeIf { it != 0 }?.let { handle ->
            bass.setVolume(handle, 0f)
        }
        currentSourceStream.takeIf { it != 0 }?.let { source ->
            bass.setVolume(source, 0f)
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
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainFactor = reset.replayGainFactor
        onProgressChanged(PlaybackProgress.Unknown)
        onStateChanged(PlaybackState.Playing)
        startProgressPolling(scope, stream)
        return true
    }

    private fun freePreparedStream() {
        preparedStream.takeIf { it != 0 }?.let { bass.freeStream(it) }
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainFactor = reset.replayGainFactor
    }

    private fun freeHandles(vararg handles: Int) {
        handles.filter { it != 0 }.toSet().forEach { bass.freeStream(it) }
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

private fun Int.audioFocusChangeName(): String =
    when (this) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> toString()
    }

private fun Int.bassActiveStateName(): String =
    when (this) {
        BassActiveStopped -> "STOPPED"
        BassActivePlaying -> "PLAYING"
        BassActiveStalled -> "STALLED"
        BassActivePaused -> "PAUSED"
        else -> toString()
    }

private fun BassAudioBackend.addMixerChannel(mixer: Int, stream: Int): Boolean =
    addMixerChannel(BassStreamHandle(mixer), BassStreamHandle(stream)).isSuccess

private fun BassAudioBackend.play(stream: Int): Boolean =
    play(BassStreamHandle(stream)).isSuccess

private fun BassAudioBackend.pause(stream: Int): Boolean =
    pause(BassStreamHandle(stream)).isSuccess

private fun BassAudioBackend.stop(stream: Int): Boolean =
    stop(BassStreamHandle(stream)).isSuccess

private fun BassAudioBackend.freeStream(stream: Int): Boolean =
    freeStream(BassStreamHandle(stream)).isSuccess

private fun BassAudioBackend.activeState(stream: Int): Int =
    activeState(BassStreamHandle(stream)) ?: BassActiveStopped

private fun BassAudioBackend.setVolume(stream: Int, volume: Float): Boolean =
    setVolume(BassStreamHandle(stream), volume).isSuccess

private fun BassAudioBackend.slideVolume(stream: Int, volume: Float, millis: Int): Boolean =
    slideVolume(BassStreamHandle(stream), volume, millis).isSuccess

private fun BassAudioBackend.seek(stream: Int, seconds: Double): Boolean =
    seek(BassStreamHandle(stream), seconds).isSuccess

private fun BassAudioBackend.positionSeconds(stream: Int): Double? =
    positionSeconds(BassStreamHandle(stream))

private fun BassAudioBackend.durationSeconds(stream: Int): Double? =
    durationSeconds(BassStreamHandle(stream))

private fun BassAudioBackend.fft(stream: Int, bins: Int): FloatArray =
    fft(BassStreamHandle(stream), bins).getOrNull() ?: FloatArray(0)

private fun BassAudioBackend.streamMetadata(stream: Int): Map<String, String> =
    streamMetadata(BassStreamHandle(stream))

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
private const val FocusDuckVolumeFactor = 0.25f
private const val DefaultMixerFrequency = 44_100
private const val DefaultMixerChannels = 2
private const val FinishedPositionToleranceSeconds = 0.75
private const val StartSeekRetryCount = 80
private const val StartSeekRetryDelayMillis = 100L
private const val Tag = "NaviampBass"
