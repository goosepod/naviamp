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
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.coroutines.CoroutineScope

/** Android lifecycle adapter around Naviamp's shared Core BASS playback engine. */
class AndroidBassPlaybackEngine(
    context: Context,
    bass: BassAudioBackend,
) : CoreBassPlaybackEngine(
    backendResult = Result.success(bass),
    runtime = AndroidBassPlaybackEngineRuntime(),
), AndroidPlaybackEngine {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private var notificationMetadata = AndroidPlaybackNotificationMetadata()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pausedForTransientFocusLoss = false
    private var duckedForFocusLoss = false
    private val playbackWakeLock: PowerManager.WakeLock by lazy {
        appContext.getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Naviamp:Playback",
        ).apply { setReferenceCounted(false) }
    }
    private var playbackWakeLockAcquiredAtMillis: Long = 0L

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.i(Tag, "Audio focus changed=${focusChange.audioFocusChangeName()}")
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (duckedForFocusLoss) {
                    duckedForFocusLoss = false
                    setTransientOutputVolumeFactor(1f)
                }
                if (pausedForTransientFocusLoss) {
                    pausedForTransientFocusLoss = false
                    super.resume()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedForTransientFocusLoss = AndroidPlaybackNotificationControls.isPlaying
                super.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (AndroidPlaybackNotificationControls.isPlaying) {
                    duckedForFocusLoss = true
                    setTransientOutputVolumeFactor(FocusDuckVolumeFactor)
                }
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

    override fun applyTlsSettings(tlsSettings: NavidromeTlsSettings) {
        AndroidPlaybackTls.applyDefaults(tlsSettings)
        setNetworkCertificateVerification(!tlsSettings.insecureSkipTlsVerification)
    }

    override fun updateNotificationMetadata(
        title: String?,
        subtitle: String?,
        coverArtUrl: String?,
    ) {
        notificationMetadata = AndroidPlaybackNotificationMetadata(title, subtitle, coverArtUrl)
        AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        if (!requestAudioFocus()) {
            val state = PlaybackState.Error("Audio focus is currently held by another app.")
            publishHostState(state)
            onStateChanged(state)
            return
        }
        AndroidPlaybackNotificationControls.isPlaying = true
        AndroidPlaybackForegroundService.start(appContext, notificationMetadata)
        super.play(
            scope = scope,
            request = request,
            onStateChanged = { state ->
                publishHostState(state)
                onStateChanged(state)
            },
            onProgressChanged = { progress ->
                renewPlaybackWakeLockIfNeeded()
                onProgressChanged(progress)
            },
            onMetadataChanged = onMetadataChanged,
        )
    }

    override fun pause() {
        super.pause()
        pausedForTransientFocusLoss = false
        duckedForFocusLoss = false
        setTransientOutputVolumeFactor(1f)
        abandonAudioFocus()
        publishHostState(PlaybackState.Paused)
    }

    override fun resume() {
        if (!requestAudioFocus()) return
        duckedForFocusLoss = false
        setTransientOutputVolumeFactor(1f)
        super.resume()
    }

    override fun stop() {
        super.stop()
        pausedForTransientFocusLoss = false
        duckedForFocusLoss = false
        setTransientOutputVolumeFactor(1f)
        abandonAudioFocus()
        publishHostState(PlaybackState.Stopped)
    }

    override fun release() {
        super.release()
        pausedForTransientFocusLoss = false
        duckedForFocusLoss = false
        abandonAudioFocus()
        releasePlaybackWakeLock()
        AndroidPlaybackNotificationControls.clear()
        AndroidPlaybackForegroundService.stop(appContext)
    }

    private fun publishHostState(state: PlaybackState) {
        when (state) {
            PlaybackState.Playing -> {
                acquirePlaybackWakeLock()
                AndroidPlaybackNotificationControls.isPlaying = true
                AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            }
            PlaybackState.Paused -> {
                releasePlaybackWakeLock()
                AndroidPlaybackNotificationControls.isPlaying = false
                AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            }
            PlaybackState.Finished,
            is PlaybackState.Error,
            -> {
                releasePlaybackWakeLock()
                AndroidPlaybackNotificationControls.isPlaying = false
                AndroidPlaybackForegroundService.update(appContext, notificationMetadata)
            }
            PlaybackState.Stopped -> {
                releasePlaybackWakeLock()
                AndroidPlaybackNotificationControls.isPlaying = false
                AndroidPlaybackForegroundService.stop(appContext)
            }
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
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .build()
            .also { audioFocusRequest = it }
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
    }

    private fun acquirePlaybackWakeLock() {
        runCatching {
            if (!playbackWakeLock.isHeld) {
                playbackWakeLock.acquire(PlaybackWakeLockTimeoutMillis)
                playbackWakeLockAcquiredAtMillis = SystemClock.elapsedRealtime()
            }
        }.onFailure { error -> Log.w(Tag, "Could not acquire playback wake lock", error) }
    }

    private fun releasePlaybackWakeLock() {
        runCatching {
            if (playbackWakeLock.isHeld) playbackWakeLock.release()
            playbackWakeLockAcquiredAtMillis = 0L
        }.onFailure { error -> Log.w(Tag, "Could not release playback wake lock", error) }
    }

    private fun renewPlaybackWakeLockIfNeeded() {
        if (!playbackWakeLock.isHeld) return
        if (SystemClock.elapsedRealtime() - playbackWakeLockAcquiredAtMillis < PlaybackWakeLockRenewalMillis) return
        releasePlaybackWakeLock()
        acquirePlaybackWakeLock()
    }
}

private fun Int.audioFocusChangeName(): String =
    when (this) {
        AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
        AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
        else -> toString()
    }

private const val FocusDuckVolumeFactor = 0.25f
private const val PlaybackWakeLockTimeoutMillis = 15 * 60 * 1_000L
private const val PlaybackWakeLockRenewalMillis = 5 * 60 * 1_000L
private const val Tag = "NaviampBass"
