package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackAudioAssetRepository
import java.nio.file.Path

class DesktopPlaybackAudioAssets(
    private val cache: DesktopCache,
) : PlaybackAudioAssetRepository<Path> {
    override suspend fun downloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): Path? =
        cache.downloadedAudioFile(sourceId, trackId, quality)?.path

    override suspend fun cachedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): Path? =
        cache.cachedAudioFile(sourceId, trackId, quality)?.path
}
