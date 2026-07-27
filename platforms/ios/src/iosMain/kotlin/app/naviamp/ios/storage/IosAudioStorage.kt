@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.ios.storage

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.storage.StorageCachedAudioFile
import app.naviamp.storage.StorageCachedAudioMetadata
import app.naviamp.storage.StorageDownloadedAudioFile
import app.naviamp.storage.StorageDownloadedTrack
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
class IosAudioByteStore(
    private val directoryPath: String,
) : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes {
        require('/' !in fileName) { "Audio file names must not contain path separators." }
        val manager = NSFileManager.defaultManager
        check(manager.createDirectoryAtPath(directoryPath, true, null, null)) {
            "Could not create the audio storage directory."
        }
        val targetPath = childPath(fileName)
        val temporaryPath = "$targetPath.tmp"
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
            if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            check(fflush(handle) == 0) { errorMessage }
            check(fsync(fileno(handle)) == 0) { errorMessage }
            check(fclose(handle) == 0) { errorMessage }
            handleOpen = false
            if (rename(temporaryPath, targetPath) != 0) throw IllegalStateException(errorMessage)
            StoredAudioBytes(
                filePath = targetPath,
                sizeBytes = requireNotNull(IosAudioFileSystem.fileSize(targetPath)) { errorMessage },
            )
        } catch (failure: Throwable) {
            if (handleOpen) runCatching { fclose(handle) }
            manager.removeItemAtPath(temporaryPath, null)
            throw failure
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        IosAudioFileSystem.deleteKnownRegularFile(directoryPath, filePath)
    }

    private fun childPath(fileName: String): String = "${directoryPath.trimEnd('/')}/$fileName"
}

object IosAudioFileSystem {
    fun isRegularFile(filePath: String): Boolean =
        NSFileManager.defaultManager.attributesOfItemAtPath(filePath, null)
            ?.get(NSFileType) == NSFileTypeRegular

    fun fileSize(filePath: String): Long? =
        (NSFileManager.defaultManager.attributesOfItemAtPath(filePath, null)
            ?.get(NSFileSize) as? NSNumber)?.longLongValue

    fun deleteKnownRegularFile(directoryPath: String, filePath: String): Boolean {
        val resolvedPath = resolveStoredFilePath(directoryPath, filePath)
        if (!isDirectChild(directoryPath, resolvedPath) || !isRegularFile(resolvedPath)) return false
        return NSFileManager.defaultManager.removeItemAtPath(resolvedPath, null)
    }

    /** iOS may relocate an app's data container during an update, invalidating persisted absolute paths. */
    fun resolveStoredFilePath(directoryPath: String, filePath: String): String {
        val normalizedDirectory = directoryPath.trimEnd('/')
        if (isDirectChild(normalizedDirectory, filePath)) return filePath
        val fileName = filePath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return filePath
        return "$normalizedDirectory/$fileName"
    }

    fun isStoredRegularFile(directoryPath: String, filePath: String): Boolean =
        isRegularFile(resolveStoredFilePath(directoryPath, filePath))

    private fun isDirectChild(directoryPath: String, filePath: String): Boolean =
        filePath.substringBeforeLast('/', missingDelimiterValue = "") == directoryPath.trimEnd('/')
}

class IosPlaybackAudioAssets(
    private val downloads: DownloadRepository<StorageDownloadedAudioFile, StorageDownloadedTrack>,
    private val cache: AudioCacheRepository<StorageCachedAudioFile, StorageCachedAudioMetadata>,
    private val downloadDirectoryPath: String,
    private val cacheDirectoryPath: String,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? =
        downloads.downloadedAudioFile(sourceId, trackId)?.toPlaybackLocalAudio(downloadDirectoryPath)

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = downloads.downloadedAudioFile(sourceId, trackId, quality)?.toPlaybackLocalAudio(downloadDirectoryPath)

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? = cache.cachedAudioFile(sourceId, trackId, quality)?.toPlaybackLocalAudio(cacheDirectoryPath)

    override suspend fun cachedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? =
        cache.cachedAudioFile(sourceId, trackId)?.toPlaybackLocalAudio(cacheDirectoryPath)
}

fun StorageCachedAudioFile.toPlaybackLocalAudio(directoryPath: String): PlaybackLocalAudio =
    IosAudioFileSystem.resolveStoredFilePath(directoryPath, filePath)
        .toPlaybackLocalAudio(sizeBytes, streamQuality)

fun StorageDownloadedAudioFile.toPlaybackLocalAudio(directoryPath: String): PlaybackLocalAudio =
    IosAudioFileSystem.resolveStoredFilePath(directoryPath, filePath)
        .toPlaybackLocalAudio(sizeBytes, streamQuality)

private fun String.toPlaybackLocalAudio(sizeBytes: Long?, quality: StreamQuality?): PlaybackLocalAudio = PlaybackLocalAudio(
    path = this,
    uri = NSURL.fileURLWithPath(this).absoluteString ?: "file://$this",
    sizeBytes = sizeBytes,
    quality = quality,
)
