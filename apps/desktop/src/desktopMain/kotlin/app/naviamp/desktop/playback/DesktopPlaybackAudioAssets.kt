package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import java.nio.file.Path

class DesktopPlaybackAudioAssets(
    private val downloadRepository: DownloadRepository<DownloadedAudioFile, DownloadedTrack>,
    private val audioCacheRepository: AudioCacheRepository<CachedAudioFile, CachedAudioMetadata>,
) : PlaybackAudioAssetRepository<Path> {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): Path? =
        downloadRepository.downloadedAudioFile(sourceId, trackId)?.path

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): Path? =
        downloadRepository.downloadedAudioFile(sourceId, trackId, quality)?.path

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): Path? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId, quality)?.path
}
