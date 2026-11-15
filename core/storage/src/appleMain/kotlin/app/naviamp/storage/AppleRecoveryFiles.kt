@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.storage

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.ferror

/** Bounded POSIX reads and atomic Foundation writes under the host-selected cache directory. */
class AppleRecoveryFiles(private val directoryPath: String) : StorageRecoveryFiles {
    override fun ownedFilePath(fileName: String): String? = ownedPath(fileName)
    override fun read(filePath: String, maximumBytes: Int): ByteArray? {
        if (maximumBytes !in 0 until Int.MAX_VALUE || !AppleAudioFileSystem.isRegularFile(filePath)) return null
        val handle = fopen(filePath, "rb") ?: return null
        return try {
            // One extra byte lets Core distinguish an oversized file without an unbounded read.
            val bytes = ByteArray(maximumBytes + 1)
            val count = bytes.usePinned { fread(it.addressOf(0), 1.convert(), bytes.size.convert(), handle).toInt() }
            if (ferror(handle) == 0) bytes.copyOf(count) else null
        } finally { fclose(handle) }
    }

    override fun write(fileName: String, bytes: ByteArray): String? {
        val path = ownedPath(fileName) ?: return null
        val manager = NSFileManager.defaultManager
        if (!manager.createDirectoryAtPath(directoryPath, true, null, null)) return null
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
        return path.takeIf { data.writeToFile(path, atomically = true) }
    }

    override fun remove(fileName: String): Boolean {
        val path = ownedPath(fileName) ?: return false
        val manager = NSFileManager.defaultManager
        return !manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null)
    }

    private fun ownedPath(fileName: String): String? = recoveryOwnedFilePath(directoryPath, fileName)
}
