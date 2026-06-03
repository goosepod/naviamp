package app.naviamp.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import app.naviamp.domain.bass.BassActiveState
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import app.naviamp.domain.bass.activeState
import app.naviamp.domain.bass.applyBassPlaybackVolume
import app.naviamp.domain.bass.applyPreparedBassMixerTransition
import app.naviamp.domain.bass.bassActiveStateLabel
import app.naviamp.domain.bass.bassFailureMessage
import app.naviamp.domain.bass.bassPlaybackSnapshot
import app.naviamp.domain.bass.bassPlaybackVisualizerFrame
import app.naviamp.domain.bass.createDirectBassPlayback
import app.naviamp.domain.bass.createMixerBassPlayback
import app.naviamp.domain.bass.createQueuedBassSource
import app.naviamp.domain.bass.pause
import app.naviamp.domain.bass.play
import app.naviamp.domain.bass.releaseBassStream
import app.naviamp.domain.bass.releaseBassStreams
import app.naviamp.domain.bass.seek
import app.naviamp.domain.bass.setBassPlaybackMuted
import app.naviamp.domain.bass.stop
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.VisualizerBandCount
import app.naviamp.domain.playback.VisualizerPlaybackEngine
import app.naviamp.domain.playback.clearPreparedPlaybackMetadata
import app.naviamp.domain.playback.clearPlaybackStreamState
import app.naviamp.domain.playback.failedPreparedPlaybackMetadata
import app.naviamp.domain.playback.normalizedCrossfadeDurationSeconds
import app.naviamp.domain.playback.planPreparedPlaybackAdoption
import app.naviamp.domain.playback.planPreparedMixerTransition
import app.naviamp.domain.playback.playbackReplayGainAdjustment
import app.naviamp.domain.playback.playbackSourceHandle
import app.naviamp.domain.playback.playbackStateForBassActiveState
import app.naviamp.domain.playback.playbackUserVolumeFactor
import app.naviamp.domain.playback.shouldFinishPlaybackForBassState
import app.naviamp.domain.playback.shouldReusePreparedPlayback
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
    override val supportsGapless: Boolean = bass.supportsMixer
    override val supportsCrossfade: Boolean = bass.supportsMixer
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
                bass.setVerifyNet(verifyNet).getOrThrow()
                bass.configureInternetStreams().getOrThrow()
                val handle = if (request.mediaId != null) {
                    replayGainFactor = playbackReplayGainAdjustment(request).volumeFactor
                    createMixerPlayback(request)
                } else {
                    createDirectPlayback(request)
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
                check(bass.play(handle).isSuccess) { errorMessage("BASS_ChannelPlay failed") }
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
        if (handle != 0 && bass.pause(handle).isSuccess) {
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
        if (handle != 0 && requestAudioFocus() && bass.play(handle).isSuccess) {
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
        val handle = playbackSourceHandle(stream, currentSourceStream).takeIf { it != 0 } ?: return false
        val success = bass.seek(handle, positionSeconds).isSuccess
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
        return bass.bassPlaybackVisualizerFrame(
            stream = handle,
            bins = VisualizerBandCount,
            timestampMillis = System.currentTimeMillis(),
        ).getOrNull()
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
            val source = createQueuedSource(request.url)
            check(source != 0) { errorMessage("BASS next stream creation failed") }
            val nextReplayGain = playbackReplayGainAdjustment(request).volumeFactor
            val transition = planPreparedMixerTransition(crossfadeDurationSeconds, nextReplayGain)
            bass.applyPreparedBassMixerTransition(
                mixer = mixer,
                nextSource = source,
                currentSource = currentSourceStream,
                currentSourceVolumeFactor = replayGainFactor,
                transition = transition,
            ).onSuccess { result ->
                result.fallbackErrors.forEach {
                    Log.w(Tag, it.message ?: "BASS crossfade envelope failed")
                }
            }.getOrThrow()
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
        val file = localFileFromUrl(request.url)
        val playback = bass.createMixerBassPlayback(
            localPath = file?.absolutePath,
            url = request.url,
            crossfadeDurationSeconds = crossfadeDurationSeconds,
            replayGainFactor = replayGainFactor,
        ).getOrThrow()
        currentSourceStream = playback.sourceHandle
        replayGainFactor = playback.replayGainFactor
        stream = playback.playbackHandle
        return playback.playbackHandle
    }

    private fun createDirectPlayback(request: PlaybackRequest): Int {
        val file = localFileFromUrl(request.url)
        val adjustment = playbackReplayGainAdjustment(request)
        val playback = bass.createDirectBassPlayback(
            localPath = file?.absolutePath,
            url = request.url,
            replayGainFactor = adjustment.volumeFactor,
        ).getOrThrow()
        currentSourceStream = playback.sourceHandle
        replayGainFactor = playback.replayGainFactor
        return playback.playbackHandle
    }

    private fun createQueuedSource(url: String): Int {
        val file = localFileFromUrl(url)
        return bass.createQueuedBassSource(
            localPath = file?.absolutePath,
            url = url,
        ).getOrNull() ?: 0
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
                val snapshot = bass.bassPlaybackSnapshot(handle, currentSourceStream)
                val active = snapshot.activeState
                val progress = snapshot.progress
                if (active != lastActiveState) {
                    lastActiveState = active
                    Log.i(
                        Tag,
                        "BASS active=${bassActiveStateLabel(active)} handle=$handle source=${playbackSourceHandle(handle, currentSourceStream)} " +
                            "position=${progress.positionSeconds} duration=${progress.durationSeconds}",
                    )
                }
                onProgressChanged?.invoke(progress)
                val metadata = snapshot.metadata
                if (metadata != lastMetadata) {
                    lastMetadata = metadata
                    onMetadataChanged?.invoke(metadata)
                }
                if (shouldFinishPlaybackForBassState(active, progress, snapshot.sourceActiveState)) {
                    Log.i(Tag, "BASS source reached end position=${progress.positionSeconds} duration=${progress.durationSeconds}")
                    handlePlaybackFinished()
                    onStateChanged?.invoke(PlaybackState.Finished)
                    return@launch
                }
                when (active) {
                    BassActiveState.Stopped -> Unit
                    else -> playbackStateForBassActiveState(active)?.let { onStateChanged?.invoke(it) }
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
        val streamReset = clearPlaybackStreamState()
        stream = streamReset.stream
        currentSourceStream = streamReset.currentSourceStream
        currentVisualizerFrame = null
        val preparedReset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = preparedReset.request
        preparedReplayGainFactor = preparedReset.replayGainFactor
        replayGainFactor = streamReset.replayGainFactor
    }

    private fun handlePlaybackFinished() {
        releasePlaybackWakeLock()
        AndroidPlaybackNotificationControls.isPlaying = false
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    private fun pauseForFocusLoss(reason: String) {
        val handle = stream
        if (handle != 0 && bass.pause(handle).isSuccess) {
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
        if (handle != 0 && bass.play(handle).isSuccess) {
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

    private fun applyVolume() {
        val userVolume = playbackUserVolumeFactor(
            volumePercent = volumePercent,
            transientDuckFactor = if (duckedForFocusLoss) FocusDuckVolumeFactor else 1f,
        )
        stream.takeIf { it != 0 }?.let { handle ->
            bass.applyBassPlaybackVolume(
                outputStream = handle,
                sourceStream = currentSourceStream,
                userVolumeFactor = userVolume,
                replayGainFactor = replayGainFactor,
            )
        }
    }

    private fun setPlaybackMuted(muted: Boolean) {
        bass.setBassPlaybackMuted(
            outputStream = stream,
            sourceStream = currentSourceStream,
            muted = muted,
            userVolumeFactor = playbackUserVolumeFactor(
                volumePercent = volumePercent,
                transientDuckFactor = if (duckedForFocusLoss) FocusDuckVolumeFactor else 1f,
            ),
            replayGainFactor = replayGainFactor,
        )
    }

    private fun adoptPreparedStream(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ): Boolean {
        val source = preparedStream
        val plan = planPreparedPlaybackAdoption(
            hasActiveStream = stream != 0,
            preparedRequest = preparedRequest,
            hasPreparedStream = source != 0,
            supportsMixer = bass.supportsMixer,
            request = request,
        )
        if (!plan.shouldAdopt) return false
        currentSourceStream.takeIf { it != 0 && it != source }?.let {
            bass.releaseBassStream(it)
        }
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
        preparedStream.takeIf { it != 0 }?.let {
            bass.releaseBassStream(it)
        }
        val reset = clearPreparedPlaybackMetadata()
        preparedStream = 0
        preparedRequest = reset.request
        preparedReplayGainFactor = reset.replayGainFactor
    }

    private fun freeHandles(vararg handles: Int) {
        bass.releaseBassStreams(*handles)
    }

    private fun errorMessage(prefix: String): String =
        bass.bassFailureMessage(prefix)
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

private const val FocusDuckVolumeFactor = 0.25f
private const val StartSeekRetryCount = 80
private const val StartSeekRetryDelayMillis = 100L
private const val Tag = "NaviampBass"
