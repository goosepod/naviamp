package app.naviamp.domain.playback

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job

/** A revocable, bounded provider byte source. Native decoders supply only offsets and byte counts. */
interface PlaybackRandomAccessSource {
    val lengthBytes: Long
    val failure: Throwable? get() = null
    suspend fun read(offset: Long, count: Int): ByteArray
}

/** Shared file cursor; blocking is required by the synchronous BASS FILEPROCS ABI. */
class BassFileCallbacks(private val source: PlaybackRandomAccessSource) {
    private var position = 0L
    private val lifetime = Job()
    fun length(): Long = source.lengthBytes
    fun seek(offset: Long): Boolean {
        if (!lifetime.isActive || offset !in 0..source.lengthBytes) return false
        position = offset
        return true
    }
    fun read(count: Int): ByteArray = if (!lifetime.isActive || count <= 0) byteArrayOf() else runCatching {
        runBlocking(lifetime) { source.read(position, minOf(count, 65_536)) }.also { bytes ->
            check(bytes.size <= count && bytes.size.toLong() <= source.lengthBytes - position)
            position += bytes.size
        }
    }.getOrDefault(byteArrayOf())
    fun close() { lifetime.cancel() }
}
