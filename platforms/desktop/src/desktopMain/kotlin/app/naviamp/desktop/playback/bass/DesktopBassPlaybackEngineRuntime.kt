package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.BassPlaybackEngineRuntime
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.net.URI
import kotlin.coroutines.CoroutineContext

/** JVM execution, URI, clock, and synchronization facts for the shared BASS engine. */
class DesktopBassPlaybackEngineRuntime : BassPlaybackEngineRuntime {
    private val preparedPlaybackLock = Any()

    override val workContext: CoroutineContext = Dispatchers.IO

    override fun localFilePath(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme == "file") File(uri).absolutePath else null
    }.getOrNull()

    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun <T> withPreparedPlaybackLock(block: () -> T): T =
        synchronized(preparedPlaybackLock, block)
}
