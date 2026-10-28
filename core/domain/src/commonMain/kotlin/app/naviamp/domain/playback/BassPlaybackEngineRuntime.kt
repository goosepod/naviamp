package app.naviamp.domain.playback

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Narrow host facts required by the shared BASS state machine.
 *
 * A platform implementation may resolve its own local URI scheme and provide thread
 * synchronization. Common code selects blocking workers so native reads cannot occupy the CPU pool. It must not decide transport, queue, crossfade, ReplayGain,
 * equalizer, polling transitions, or visualizer behavior.
 */
interface BassPlaybackEngineRuntime {
    val workContext: CoroutineContext get() = Dispatchers.IO

    fun localFilePath(url: String): String?
    fun nowEpochMillis(): Long
    fun <T> withPreparedPlaybackLock(block: () -> T): T
}
