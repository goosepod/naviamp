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
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.domain.home.HomeAlbumYear
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform

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
    MediaSourceRepository by cache,
    ProviderMediaSourceRepository by cache,
    LocalLibraryIndexRepository by cache,
    CacheMaintenanceRepository<StorageCacheStats> by cache,
    TrackMetadataRepository by cache {

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform? =
        cache.cachedAudioWaveform(sourceId, trackId, quality)

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        cache.mediaSource(sourceId)

    fun libraryOffsetForLetter(sourceId: String, tab: DesktopLibraryTab, letter: Char): Long =
        cache.libraryOffsetForLetter(sourceId, tab, letter)

    fun asHomeLibraryRepository(): HomeLibraryRepository =
        object : HomeLibraryRepository {
            override fun albumYears(sourceId: String) =
                libraryAlbumYears(sourceId).map { year ->
                    HomeAlbumYear(
                        year = year.year,
                        albumCount = year.albumCount,
                    )
                }
        }
}
