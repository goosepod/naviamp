package app.naviamp.domain.playback

import kotlinx.coroutines.runBlocking

/** A revocable, bounded provider byte source. Native decoders supply only offsets and byte counts. */
interface PlaybackRandomAccessSource {
    val lengthBytes: Long
    val failure: Throwable? get() = null
    suspend fun read(offset: Long, count: Int): ByteArray
}

/** Shared file cursor; blocking is required by the synchronous BASS FILEPROCS ABI. */
class BassFileCallbacks(private val source: PlaybackRandomAccessSource) {
    private var position = 0L
    private var closed = false
    fun length(): Long = source.lengthBytes
    fun seek(offset: Long): Boolean {
        if (closed || offset !in 0..source.lengthBytes) return false
        position = offset
        return true
    }
    fun read(count: Int): ByteArray = if (closed || count <= 0) byteArrayOf() else runCatching {
        runBlocking { source.read(position, count) }.also { bytes ->
            check(bytes.size <= count && bytes.size.toLong() <= source.lengthBytes - position)
            position += bytes.size
        }
    }.getOrDefault(byteArrayOf())
    fun close() { closed = true }
}
