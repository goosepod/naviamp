package app.naviamp.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import kotlinx.coroutines.CoroutineScope

/** Android audio-focus and wake-lock lifetime around Core's complete BASS playback engine. */
class AndroidFocusedBassPlaybackEngine(
    context: Context,
    bass: BassAudioBackend,
) : CoreBassPlaybackEngine(
    backendResult = Result.success(bass),
    runtime = AndroidBassPlaybackEngineRuntime(),
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackActive = false
    private var pausedForTransientFocusLoss = false
    private var duckedForFocusLoss = false
    private val wakeLock: PowerManager.WakeLock by lazy {
        appContext.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Naviamp:Playback",
        ).apply { setReferenceCounted(false) }
    }
    private var wakeLockAcquiredAtMillis = 0L

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (duckedForFocusLoss) setTransientOutputVolumeFactor(1f)
                duckedForFocusLoss = false
                if (pausedForTransientFocusLoss) super.resume()
                pausedForTransientFocusLoss = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedForTransientFocusLoss = playbackActive
                if (playbackActive) super.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckedForFocusLoss = true
                setTransientOutputVolumeFactor(FocusDuckVolumeFactor)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedForTransientFocusLoss = false
                duckedForFocusLoss = false
                setTransientOutputVolumeFactor(1f)
                super.pause()
                abandonAudioFocus()
            }
        }
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        if (!requestAudioFocus()) {
            onStateChanged(PlaybackState.Error("Audio focus is currently held by another app."))
            return
        }
        super.play(
            scope = scope,
            request = request,
            onStateChanged = { state ->
                applyNativePlaybackLifetime(state)
                onStateChanged(state)
            },
            onProgressChanged = { progress ->
                renewWakeLockIfNeeded()
                onProgressChanged(progress)
            },
            onMetadataChanged = onMetadataChanged,
        )
    }

    override fun pause() {
        super.pause()
        clearTransientFocusState()
        abandonAudioFocus()
        releaseWakeLock()
    }

    override fun resume() {
        if (!requestAudioFocus()) return
        clearTransientFocusState()
        super.resume()
    }

    override fun stop() {
        super.stop()
        clearTransientFocusState()
        abandonAudioFocus()
        releaseWakeLock()
    }

    override fun release() {
        super.release()
        clearTransientFocusState()
        abandonAudioFocus()
        releaseWakeLock()
    }

    private fun applyNativePlaybackLifetime(state: PlaybackState) {
        playbackActive = state == PlaybackState.Playing
        when (state) {
            PlaybackState.Playing -> acquireWakeLock()
            PlaybackState.Paused,
            PlaybackState.Stopped,
            PlaybackState.Finished,
            is PlaybackState.Error,
            -> releaseWakeLock()
            PlaybackState.Idle,
            PlaybackState.Loading,
            -> Unit
        }
    }

    private fun requestAudioFocus(): Boolean {
        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .build()
            .also { audioFocusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
    }

    private fun clearTransientFocusState() {
        pausedForTransientFocusLoss = false
        if (duckedForFocusLoss) setTransientOutputVolumeFactor(1f)
        duckedForFocusLoss = false
    }

    private fun acquireWakeLock() {
        runCatching {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(WakeLockTimeoutMillis)
                wakeLockAcquiredAtMillis = SystemClock.elapsedRealtime()
            }
        }.onFailure { error -> Log.w(Tag, "Could not acquire playback wake lock", error) }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock.isHeld) wakeLock.release()
            wakeLockAcquiredAtMillis = 0L
        }.onFailure { error -> Log.w(Tag, "Could not release playback wake lock", error) }
    }

    private fun renewWakeLockIfNeeded() {
        if (!wakeLock.isHeld) return
        if (SystemClock.elapsedRealtime() - wakeLockAcquiredAtMillis < WakeLockRenewalMillis) return
        releaseWakeLock()
        acquireWakeLock()
    }
}

private const val FocusDuckVolumeFactor = 0.25f
private const val WakeLockTimeoutMillis = 15 * 60 * 1_000L
private const val WakeLockRenewalMillis = 5 * 60 * 1_000L
private const val Tag = "NaviampBass"
