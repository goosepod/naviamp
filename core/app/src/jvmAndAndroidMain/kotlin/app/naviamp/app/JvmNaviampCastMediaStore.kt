package app.naviamp.app

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** JVM filesystem effects only. Shared Core owns preparation, byte budgets and lease lifetime. */
class JvmNaviampCastMediaStore(private val directory: Path) : NaviampCastMediaStore {
    override suspend fun write(writeBytes: suspend (NaviampCastMediaWriter) -> Boolean): NaviampCastStoredMedia {
        val (path, file) = withContext(NonCancellable + Dispatchers.IO) {
            Files.createDirectories(directory)
            val path = Files.createTempFile(directory, "naviamp-cast-", ".mp3")
            try {
                path.toFile().deleteOnExit()
                path to RandomAccessFile(path.toFile(), "rw")
            } catch (failure: Throwable) { Files.deleteIfExists(path); throw failure }
        }
        try {
            currentCoroutineContext().ensureActive()
            check(writeBytes(NaviampCastMediaWriter { bytes, count ->
                withContext(Dispatchers.IO) { synchronized(file) { file.write(bytes, 0, count) } }
            }))
            val size = withContext(Dispatchers.IO) { synchronized(file) { file.length() } }
            return object : NaviampCastStoredMedia {
                override val sizeBytes = size
                override suspend fun read(offset: Long, count: Int): ByteArray = withContext(Dispatchers.IO) {
                    synchronized(file) {
                        file.seek(offset)
                        ByteArray(count).also(file::readFully)
                    }
                }
                override suspend fun delete() = deleteFile(path, file)
            }
        } catch (failure: Throwable) { deleteFile(path, file); throw failure }
    }

    private suspend fun deleteFile(path: Path, file: RandomAccessFile) = withContext(NonCancellable + Dispatchers.IO) {
        synchronized(file) {
            try { file.close() } finally { Files.deleteIfExists(path) }
        }
        Unit
    }
}
