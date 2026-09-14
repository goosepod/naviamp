package app.naviamp.domain.playback

/** Native focus notifications, translated without playback policy by each host. */
enum class PlaybackFocusChange { Gain, TransientLoss, Duck, Loss }

interface PlaybackFocusEffect {
    fun request(onChange: (PlaybackFocusChange) -> Unit): Boolean
    fun abandon()
}

/** Timed OS execution lease and monotonic clock; renewal policy belongs to Core. */
interface PlaybackWakeLockEffect {
    val isHeld: Boolean
    fun nowMillis(): Long
    fun acquire(timeoutMillis: Long)
    fun release()
}

class PlaybackFocusController(
    private val focus: PlaybackFocusEffect,
    private val wakeLock: PlaybackWakeLockEffect,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
    private val outputVolumeFactor: (Float) -> Unit,
) {
    private var playing = false
    private val interruption = PlaybackInterruptionPolicy()
    private var ducked = false
    private var acquiredAtMillis = 0L

    fun requestPlayback(): Boolean {
        if (!focus.request(::onFocusChanged)) return false
        clearTransientState()
        return true
    }

    fun userPausedOrStopped() {
        playing = false
        clearTransientState()
        focus.abandon()
        wakeLock.release()
    }

    fun onPlaybackState(state: PlaybackState) {
        playing = state == PlaybackState.Playing
        when (state) {
            PlaybackState.Playing -> if (!wakeLock.isHeld) acquireWakeLock()
            PlaybackState.Paused -> wakeLock.release()
            PlaybackState.Stopped, PlaybackState.Finished, is PlaybackState.Error -> userPausedOrStopped()
            PlaybackState.Idle, PlaybackState.Loading -> Unit
        }
    }

    fun onProgress() {
        if (!playing) return
        // Native leases may expire while progress delivery is delayed. Recover without requiring
        // another Playing publication, but never let late progress undo an explicit pause/stop.
        if (!wakeLock.isHeld) {
            acquireWakeLock()
        } else if (wakeLock.nowMillis() - acquiredAtMillis >= WakeLockRenewalMillis) {
            wakeLock.release()
            acquireWakeLock()
        }
    }

    private fun onFocusChanged(change: PlaybackFocusChange) {
        when (change) {
            PlaybackFocusChange.Gain -> {
                val shouldResume = interruption.ended(shouldResume = true)
                clearTransientState()
                if (shouldResume) resume()
            }
            PlaybackFocusChange.TransientLoss -> {
                if (interruption.began(playing)) pause()
            }
            PlaybackFocusChange.Duck -> if (playing) {
                ducked = true
                outputVolumeFactor(0.25f)
            }
            PlaybackFocusChange.Loss -> {
                clearTransientState()
                pause()
                focus.abandon()
                wakeLock.release()
            }
        }
    }

    private fun clearTransientState() {
        interruption.cancel()
        if (ducked) outputVolumeFactor(1f)
        ducked = false
    }

    private fun acquireWakeLock() {
        wakeLock.acquire(WakeLockTimeoutMillis)
        acquiredAtMillis = wakeLock.nowMillis()
    }
}

private const val WakeLockTimeoutMillis = 15 * 60 * 1_000L
private const val WakeLockRenewalMillis = 5 * 60 * 1_000L
