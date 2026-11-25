@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.storage

import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.StoredAudioBytes
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fsync
import platform.posix.fwrite
import platform.posix.rename

/** Atomic Foundation/POSIX byte effect for Core-owned audio storage behavior. */
class AppleAudioByteStore(
    private val directoryPath: String,
) : AudioByteStore {
    override fun audioFilePath(fileName: String): String = childPath(fileName)

    override suspend fun writeAudioBytes(
        temporaryFileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> String,
    ): StoredAudioBytes {
        require('/' !in temporaryFileName) { "Audio file names must not contain path separators." }
        val manager = NSFileManager.defaultManager
        check(manager.createDirectoryAtPath(directoryPath, true, null, null)) {
            "Could not create the audio storage directory."
        }
        val temporaryPath = childPath(temporaryFileName)
        manager.removeItemAtPath(temporaryPath, null)
        check(manager.createFileAtPath(temporaryPath, null, null)) { errorMessage }
        val handle = requireNotNull(fopen(temporaryPath, "wb")) { errorMessage }
        var handleOpen = true
        return try {
            val writer = AudioByteWriter { bytes, count ->
                require(count in 0..bytes.size)
                if (count > 0) {
                    val written = bytes.usePinned { pinned ->
                        fwrite(pinned.addressOf(0), 1.convert(), count.convert(), handle)
                    }
                    check(written.toLong() == count.toLong()) { errorMessage }
                }
            }
            val fileName = writeBytes(writer)
            require('/' !in fileName) { "Audio file names must not contain path separators." }
            check(fflush(handle) == 0) { errorMessage }
            check(fsync(fileno(handle)) == 0) { errorMessage }
            check(fclose(handle) == 0) { errorMessage }
            handleOpen = false
            val targetPath = childPath(fileName)
            if (rename(temporaryPath, targetPath) != 0) throw IllegalStateException(errorMessage)
            StoredAudioBytes(
                filePath = targetPath,
                sizeBytes = requireNotNull(AppleAudioFileSystem.fileSize(targetPath)) { errorMessage },
            )
        } catch (failure: Throwable) {
            if (handleOpen) runCatching { fclose(handle) }
            manager.removeItemAtPath(temporaryPath, null)
            throw failure
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        AppleAudioFileSystem.deleteKnownRegularFile(directoryPath, filePath)
    }

    private fun childPath(fileName: String): String = "${directoryPath.trimEnd('/')}/$fileName"
}

object AppleAudioFileSystem {
    fun isRegularFile(filePath: String): Boolean =
        NSFileManager.defaultManager.attributesOfItemAtPath(filePath, null)
            ?.get(NSFileType) == NSFileTypeRegular

    fun fileSize(filePath: String): Long? =
        (NSFileManager.defaultManager.attributesOfItemAtPath(filePath, null)
            ?.get(NSFileSize) as? NSNumber)?.longLongValue

    fun deleteKnownRegularFile(directoryPath: String, filePath: String): Boolean {
        val resolvedPath = resolveStoredFilePath(directoryPath, filePath)
        if (!isKnownStoredAudioFilePath(directoryPath, resolvedPath)) return false
        if (!NSFileManager.defaultManager.fileExistsAtPath(resolvedPath)) return true
        if (!isRegularFile(resolvedPath)) return false
        return NSFileManager.defaultManager.removeItemAtPath(resolvedPath, null)
    }

    /** Apple may relocate an app's data container during an update, invalidating persisted absolute paths. */
    fun resolveStoredFilePath(directoryPath: String, filePath: String): String =
        resolveStoredAudioFilePath(directoryPath, filePath)

    fun isStoredRegularFile(directoryPath: String, filePath: String): Boolean =
        isRegularFile(resolveStoredFilePath(directoryPath, filePath))

}

/** Foundation file-URL and filesystem effects consumed by shared storage/playback assembly. */
fun appleStorageAudioDirectory(path: String): StorageAudioDirectory = StorageAudioDirectory(
    path = path,
    byteStore = AppleAudioByteStore(path),
    regularFileExists = AppleAudioFileSystem::isRegularFile,
    deleteFile = { AppleAudioFileSystem.deleteKnownRegularFile(path, it) },
    fileUri = { NSURL.fileURLWithPath(it).absoluteString ?: "file://$it" },
)
