package app.naviamp.android

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import app.naviamp.domain.playback.PlaybackLocalAudio
import java.io.File

class AndroidPlaybackAudioAssets(
    private val downloadRepository: DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    private val audioCacheRepository: AudioCacheRepository<AndroidCachedAudioFile, AndroidCachedAudioMetadata>,
) : PlaybackAudioAssetRepository {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId)?.file?.toPlaybackLocalAudio()

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        downloadRepository.downloadedAudioFile(sourceId, trackId, quality)?.file?.toPlaybackLocalAudio()

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId, quality)?.file?.toPlaybackLocalAudio()

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
    ): PlaybackLocalAudio? =
        audioCacheRepository.cachedAudioFile(sourceId, trackId)?.file?.toPlaybackLocalAudio()
}

fun File.toPlaybackLocalAudio(): PlaybackLocalAudio =
    PlaybackLocalAudio(
        path = absolutePath,
        uri = toURI().toString(),
        sizeBytes = if (isFile) length() else null,
    )
