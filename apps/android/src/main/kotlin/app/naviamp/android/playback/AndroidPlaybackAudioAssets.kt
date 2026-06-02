package app.naviamp.android

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import java.io.File

class AndroidPlaybackAudioAssets(
    private val storage: AndroidStorage,
) : PlaybackAudioAssetRepository<File> {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ): File? =
        storage.downloadedAudioFile(sourceId, trackId)?.file

    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): File? =
        storage.downloadedAudioFile(sourceId, trackId, quality)?.file

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): File? =
        storage.cachedAudioFile(sourceId, trackId, quality)?.file
}
