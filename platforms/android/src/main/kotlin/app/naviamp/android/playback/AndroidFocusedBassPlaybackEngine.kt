package app.naviamp.android.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.playback.FocusedBassPlaybackEngine
import app.naviamp.domain.playback.PlaybackFocusChange
import app.naviamp.domain.playback.PlaybackFocusEffect
import app.naviamp.domain.playback.PlaybackWakeLockEffect

/** Android AudioManager/PowerManager bindings; all focus and renewal policy is shared. */
class AndroidFocusedBassPlaybackEngine(context: Context, bass: BassAudioBackend) : FocusedBassPlaybackEngine(
    bass = bass,
    runtime = AndroidBassPlaybackEngineRuntime(),
    focus = AndroidPlaybackFocusEffect(context.applicationContext),
    wakeLock = AndroidPlaybackWakeLockEffect(context.applicationContext),
)

private class AndroidPlaybackFocusEffect(context: Context) : PlaybackFocusEffect {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var callback: ((PlaybackFocusChange) -> Unit)? = null
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener { change ->
            val translated = when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> PlaybackFocusChange.Gain
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> PlaybackFocusChange.TransientLoss
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> PlaybackFocusChange.Duck
                AudioManager.AUDIOFOCUS_LOSS -> PlaybackFocusChange.Loss
                else -> null
            }
            translated?.let { callback?.invoke(it) }
        }
        .setAcceptsDelayedFocusGain(false)
        .setWillPauseWhenDucked(false)
        .build()

    override fun request(onChange: (PlaybackFocusChange) -> Unit): Boolean {
        callback = onChange
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    override fun abandon() { manager.abandonAudioFocusRequest(request) }
}

private class AndroidPlaybackWakeLockEffect(context: Context) : PlaybackWakeLockEffect {
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Naviamp:Playback")
        .apply { setReferenceCounted(false) }
    override val isHeld: Boolean get() = lock.isHeld
    override fun nowMillis() = SystemClock.elapsedRealtime()
    override fun acquire(timeoutMillis: Long) {
        runCatching { lock.acquire(timeoutMillis) }
            .onFailure { Log.w("NaviampBass", "Could not acquire playback wake lock", it) }
    }
    override fun release() {
        runCatching { if (lock.isHeld) lock.release() }
            .onFailure { Log.w("NaviampBass", "Could not release playback wake lock", it) }
    }
}
