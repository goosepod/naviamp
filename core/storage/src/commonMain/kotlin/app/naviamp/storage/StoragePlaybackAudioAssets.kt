package app.naviamp.storage

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.isNaviampOwnedAudioFileName
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio

/** Native byte/file effects for one host-selected directory; selection and mapping stay in Core. */
class StorageAudioDirectory(
    val path: String,
    val byteStore: AudioByteStore,
    private val regularFileExists: (String) -> Boolean,
    private val deleteFile: (String) -> Boolean,
    private val fileUri: (String) -> String,
) {
    fun exists(storedPath: String): Boolean = regularFileExists(resolveStoredAudioFilePath(path, storedPath))

    fun delete(storedPath: String): Boolean {
        val resolved = resolveStoredAudioFilePath(path, storedPath)
        return isKnownStoredAudioFilePath(path, resolved) && deleteFile(resolved)
    }

    fun playbackAudio(storedPath: String, sizeBytes: Long?, quality: StreamQuality?): PlaybackLocalAudio {
        val resolved = resolveStoredAudioFilePath(path, storedPath)
        return PlaybackLocalAudio(resolved, fileUri(resolved), sizeBytes, quality)
    }
}

/** Portable ownership guard before a native deletion effect. */
fun isKnownStoredAudioFilePath(directoryPath: String, resolvedPath: String): Boolean =
    resolvedPath.substringBeforeLast('/', missingDelimiterValue = "") == directoryPath.trimEnd('/') &&
        isNaviampOwnedAudioFileName(resolvedPath.substringAfterLast('/'))

/** Rebase stored paths after the operating system relocates an app container. */
fun resolveStoredAudioFilePath(directoryPath: String, filePath: String): String {
    val directory = directoryPath.trimEnd('/')
    if (filePath.substringBeforeLast('/', missingDelimiterValue = "") == directory) return filePath
    val fileName = filePath.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return filePath
    return "$directory/$fileName"
}

/** Shared download/cache lookup and metadata mapping, independent of Foundation/JVM file URLs. */
class StoragePlaybackAudioAssets(
    private val downloads: DownloadRepository<StorageDownloadedAudioFile, StorageDownloadedTrack>,
    private val cache: AudioCacheRepository<StorageCachedAudioFile, StorageCachedAudioMetadata>,
    private val downloadDirectory: StorageAudioDirectory,
    private val cacheDirectory: StorageAudioDirectory,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? =
        downloads.downloadedAudioFile(sourceId, trackId)?.let {
            downloadDirectory.playbackAudio(it.filePath, it.sizeBytes, it.streamQuality)
        }

    override suspend fun downloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality): PlaybackLocalAudio? =
        downloads.downloadedAudioFile(sourceId, trackId, quality)?.let {
            downloadDirectory.playbackAudio(it.filePath, it.sizeBytes, it.streamQuality)
        }

    override suspend fun cachedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality): PlaybackLocalAudio? =
        cache.cachedAudioFile(sourceId, trackId, quality)?.let {
            cacheDirectory.playbackAudio(it.filePath, it.sizeBytes, it.streamQuality)
        }

    override suspend fun cachedAudio(sourceId: String, trackId: TrackId): PlaybackLocalAudio? =
        cache.cachedAudioFile(sourceId, trackId)?.let {
            cacheDirectory.playbackAudio(it.filePath, it.sizeBytes, it.streamQuality)
        }
}
