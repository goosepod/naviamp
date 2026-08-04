package app.naviamp.domain.playback

import kotlin.coroutines.CoroutineContext

/**
 * Narrow host facts required by the shared BASS state machine.
 *
 * A platform implementation may resolve its own local URI scheme, select a worker dispatcher,
 * and provide thread synchronization. It must not decide transport, queue, crossfade, ReplayGain,
 * equalizer, polling transitions, or visualizer behavior.
 */
interface BassPlaybackEngineRuntime {
    val workContext: CoroutineContext

    fun localFilePath(url: String): String?
    fun nowEpochMillis(): Long
    fun <T> withPreparedPlaybackLock(block: () -> T): T
}
