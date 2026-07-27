package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.toStoredAudioQuality
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import java.nio.file.Files
import java.nio.file.Path

class DesktopPlaybackAudioAssets(
    private val downloadRepository: DownloadRepository<DownloadedAudioFile, DownloadedTrack>,
    private val audioCacheRepository: AudioCacheRepository<CachedAudioFile, CachedAudioMetadata>,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId)?.let { stored ->
            stored.path.toPlaybackLocalAudio(stored.qualityKey.toStoredAudioQuality())
        }

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId, quality)?.path?.toPlaybackLocalAudio(quality)

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId, quality)?.path?.toPlaybackLocalAudio(quality)

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId)?.let { stored ->
            stored.path.toPlaybackLocalAudio(stored.qualityKey.toStoredAudioQuality())
        }
}

fun Path.toPlaybackLocalAudio(quality: StreamQuality? = null): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = toAbsolutePath().toString(),
        uri = toUri().toString(),
        sizeBytes = if (Files.isRegularFile(this)) runCatching { Files.size(this) }.getOrNull() else null,
        quality = quality,
    )
