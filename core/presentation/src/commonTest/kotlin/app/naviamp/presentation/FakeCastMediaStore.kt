package app.naviamp.presentation

import app.naviamp.app.NaviampCastMediaStore
import app.naviamp.app.NaviampCastMediaWriter
import app.naviamp.app.NaviampCastStoredMedia

internal class FakeCastMediaStore : NaviampCastMediaStore {
    var writes = 0
    var deleted = 0
    override suspend fun write(writeBytes: suspend (NaviampCastMediaWriter) -> Boolean): NaviampCastStoredMedia {
        writes++
        val bytes = mutableListOf<Byte>()
        try {
            check(writeBytes(NaviampCastMediaWriter { chunk, count -> bytes += chunk.take(count) }))
        } catch (failure: Throwable) { deleted++; throw failure }
        return object : NaviampCastStoredMedia {
            override val sizeBytes = bytes.size.toLong()
            override suspend fun read(offset: Long, count: Int) = bytes.subList(offset.toInt(), offset.toInt() + count).toByteArray()
            override suspend fun delete() { deleted++ }
        }
    }
}
