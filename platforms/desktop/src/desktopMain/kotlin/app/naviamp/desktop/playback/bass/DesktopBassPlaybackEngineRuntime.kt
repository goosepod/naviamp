package app.naviamp.desktop.playback.bass

import app.naviamp.domain.playback.BassPlaybackEngineRuntime
import java.io.File
import java.net.URI

/** JVM URI and synchronization effects for the shared BASS engine. */
class DesktopBassPlaybackEngineRuntime : BassPlaybackEngineRuntime {
    private val preparedPlaybackLock = Any()

    override fun localFilePath(url: String): String? = runCatching {
        val uri = URI(url)
        if (uri.scheme == "file") File(uri).absolutePath else null
    }.getOrNull()

    override fun <T> withPreparedPlaybackLock(block: () -> T): T =
        synchronized(preparedPlaybackLock, block)
}
