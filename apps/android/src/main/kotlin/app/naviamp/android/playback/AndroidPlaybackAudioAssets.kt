package app.naviamp.android

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import java.io.File

class AndroidPlaybackAudioAssets(
    private val downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    private val audioCacheRepository: AudioCacheRepository<AndroidCachedAudioFile, AndroidCachedAudioMetadata>,
) : PlaybackAudioAssetRepository<File> {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): File? =
        downloadRepository.downloadedAudioFile(sourceId, trackId)?.file

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): File? =
        downloadRepository.downloadedAudioFile(sourceId, trackId, quality)?.file

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): File? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId, quality)?.file
}
