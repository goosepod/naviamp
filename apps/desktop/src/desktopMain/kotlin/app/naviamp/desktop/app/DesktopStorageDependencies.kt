package app.naviamp.desktop

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioWaveformRepository
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.domain.home.HomeAlbumYear
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform
import java.nio.file.Path

class DesktopStorageDependencies(
    private val cache: DesktopCache = DesktopCaches.session,
) : ImageCacheRepository by cache,
    ProviderResponseCacheRepository by cache,
    AudioCacheRepository<CachedAudioFile, CachedAudioMetadata> by cache,
    AudioWaveformRepository by cache,
    AudioWaveformStorageRepository by cache,
    LyricsSidecarRepository by cache,
    LyricsOffsetRepository by cache,
    SidecarStatusRepository by cache,
    DownloadRepository<DownloadedAudioFile, DownloadedTrack> by cache,
    DownloadReplacementRepository<DownloadedAudioFile> by cache,
    KeepDownloadedRepository by cache,
    MediaSourceRepository by cache,
    ProviderMediaSourceRepository by cache,
    LocalLibraryIndexRepository by cache,
    PendingProviderActionRepository by cache,
    RadioDjPresetRepository by cache,
    CacheMaintenanceRepository<StorageCacheStats> by cache,
    TrackMetadataRepository by cache {

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? =
        cache.cachedAudioWaveform(sourceId, trackId, quality, bucketCount)

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        cache.mediaSource(sourceId)

    fun libraryOffsetForLetter(sourceId: String, tab: DesktopLibraryTab, letter: Char): Long =
        cache.libraryOffsetForLetter(sourceId, tab, letter)

    fun updateDownloadDirectory(directory: Path) {
        cache.updateDownloadDirectory(directory)
    }

    fun downloadDirectory(): Path =
        cache.downloadDirectory()

    fun updateAudioCacheDirectory(directory: Path) = cache.updateAudioCacheDirectory(directory)

    fun audioCacheDirectory(): Path = cache.audioCacheDirectory()

    fun asHomeLibraryRepository(): HomeLibraryRepository =
        object : HomeLibraryRepository {
            override fun albumYears(sourceId: String) =
                libraryAlbumYears(sourceId).map { year ->
                    HomeAlbumYear(
                        year = year.year,
                        albumCount = year.albumCount,
                    )
                }

            override fun recentlyPlayedTracks(sourceId: String, limit: Long) =
                recentlyPlayedLibraryTracks(sourceId, limit)
        }
}
