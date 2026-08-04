package app.naviamp.android

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.toStoredAudioQuality
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import app.naviamp.storage.StorageCachedAudioFile
import app.naviamp.storage.StorageCachedAudioMetadata
import app.naviamp.storage.StorageDownloadedAudioFile
import app.naviamp.storage.StorageDownloadedTrack
import java.io.File

class AndroidPlaybackAudioAssets(
    private val downloadRepository: DownloadRepository<StorageDownloadedAudioFile, StorageDownloadedTrack>,
    private val audioCacheRepository: AudioCacheRepository<StorageCachedAudioFile, StorageCachedAudioMetadata>,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId)?.let { stored ->
            File(stored.filePath).toPlaybackLocalAudio(stored.qualityKey.toStoredAudioQuality())
        }

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId, quality)?.filePath?.let(::File)?.toPlaybackLocalAudio(quality)

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId, quality)?.filePath?.let(::File)?.toPlaybackLocalAudio(quality)

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId)?.let { stored ->
            File(stored.filePath).toPlaybackLocalAudio(stored.qualityKey.toStoredAudioQuality())
        }
}

fun File.toPlaybackLocalAudio(quality: StreamQuality? = null): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = absolutePath,
        uri = toURI().toString(),
        sizeBytes = if (isFile) length() else null,
        quality = quality,
    )
