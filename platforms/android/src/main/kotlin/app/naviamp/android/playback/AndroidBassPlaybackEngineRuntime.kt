package app.naviamp.android.playback

import android.net.Uri
import app.naviamp.domain.playback.BassPlaybackEngineRuntime
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers

/** Android threading, URI, clock, and synchronization effects required by the Core BASS engine. */
class AndroidBassPlaybackEngineRuntime : BassPlaybackEngineRuntime {
    private val preparedPlaybackLock = Any()

    override val workContext: CoroutineContext = Dispatchers.IO

    override fun localFilePath(url: String): String? = runCatching {
        val uri = Uri.parse(url)
        if (uri.scheme == "file") File(requireNotNull(uri.path)).absolutePath else null
    }.getOrNull()

    override fun nowEpochMillis(): Long = System.currentTimeMillis()

    override fun <T> withPreparedPlaybackLock(block: () -> T): T =
        synchronized(preparedPlaybackLock, block)
}
