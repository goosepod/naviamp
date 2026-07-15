package app.naviamp.android

import android.content.Context
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioWaveformCacheRepository
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
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.home.HomeAlbumYear
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform

class AndroidStorageDependencies(
    context: Context,
    private val storage: AndroidStorage = AndroidStorage(context),
) : ImageCacheRepository by storage,
    ProviderResponseCacheRepository by storage,
    AudioCacheRepository<AndroidCachedAudioFile, AndroidCachedAudioMetadata> by storage,
    AudioWaveformCacheRepository by storage,
    AudioWaveformStorageRepository by storage,
    LyricsSidecarRepository by storage,
    LyricsOffsetRepository by storage,
    DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack> by storage,
    DownloadReplacementRepository<AndroidDownloadedAudioFile> by storage,
    KeepDownloadedRepository by storage,
    PlaybackHistoryRepository<AndroidPlaybackHistoryItem> by storage,
    MediaSourceRepository by storage,
    ProviderMediaSourceRepository by storage,
    PlaybackSessionRepository by storage,
    LocalLibraryIndexRepository by storage,
    PendingProviderActionRepository by storage,
    RadioDjPresetRepository by storage,
    CacheMaintenanceRepository<StorageCacheStats> by storage,
    SidecarStatusRepository by storage,
    AutoCloseable {

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? =
        storage.cachedAudioWaveform(sourceId, trackId, quality, bucketCount)

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        storage.mediaSource(sourceId)

    fun latestNavidromeSource(): SavedMediaSource? =
        storage.latestNavidromeSource()

    fun updateDownloadDirectory(directory: java.io.File) = storage.updateDownloadDirectory(directory)
    fun updateAudioCacheDirectory(directory: java.io.File) = storage.updateAudioCacheDirectory(directory)

    fun libraryTrack(sourceId: String, trackId: TrackId): Track? =
        storage.libraryTrack(sourceId, trackId)

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

    override fun close() {
        storage.close()
    }
}
