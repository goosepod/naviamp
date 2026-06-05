package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
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
        downloadRepository.downloadedAudioFile(sourceId, trackId)?.path?.toPlaybackLocalAudio()

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId, quality)?.path?.toPlaybackLocalAudio()

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId, quality)?.path?.toPlaybackLocalAudio()
}

fun Path.toPlaybackLocalAudio(): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = toAbsolutePath().toString(),
        uri = toUri().toString(),
        sizeBytes = if (Files.isRegularFile(this)) runCatching { Files.size(this) }.getOrNull() else null,
    )
