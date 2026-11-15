package app.naviamp.ios.storage

import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.storage.AppleAudioByteStore
import app.naviamp.storage.AppleAudioFileSystem
import app.naviamp.storage.appleStorageAudioDirectory

/** Compatibility delegates for the shared Foundation/POSIX file effects. */
class IosAudioByteStore(directoryPath: String) : AudioByteStore by AppleAudioByteStore(directoryPath)

object IosAudioFileSystem {
    fun isRegularFile(filePath: String) = AppleAudioFileSystem.isRegularFile(filePath)
    fun fileSize(filePath: String) = AppleAudioFileSystem.fileSize(filePath)
    fun deleteKnownRegularFile(directoryPath: String, filePath: String) = AppleAudioFileSystem.deleteKnownRegularFile(directoryPath, filePath)
    fun resolveStoredFilePath(directoryPath: String, filePath: String) = AppleAudioFileSystem.resolveStoredFilePath(directoryPath, filePath)
    fun isStoredRegularFile(directoryPath: String, filePath: String) = AppleAudioFileSystem.isStoredRegularFile(directoryPath, filePath)
}

fun iosStorageAudioDirectory(path: String) = appleStorageAudioDirectory(path)
