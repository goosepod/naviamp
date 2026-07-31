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
import app.naviamp.storage.StorageCachedAudioFile
import app.naviamp.storage.StorageCachedAudioMetadata
import app.naviamp.storage.StorageDownloadedAudioFile
import app.naviamp.storage.StorageDownloadedTrack
import app.naviamp.storage.StoragePlaybackHistoryItem

class AndroidStorageDependencies(
    context: Context,
    private val storage: AndroidStorage = AndroidStorage(context),
) : ImageCacheRepository by storage,
    ProviderResponseCacheRepository by storage,
    AudioCacheRepository<StorageCachedAudioFile, StorageCachedAudioMetadata> by storage,
    AudioWaveformCacheRepository by storage,
    AudioWaveformStorageRepository by storage,
    LyricsSidecarRepository by storage,
    LyricsOffsetRepository by storage,
    DownloadRepository<StorageDownloadedAudioFile, StorageDownloadedTrack> by storage,
    DownloadReplacementRepository<StorageDownloadedAudioFile> by storage,
    KeepDownloadedRepository by storage,
    PlaybackHistoryRepository<StoragePlaybackHistoryItem> by storage,
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
    val downloadDirectory: java.io.File get() = storage.downloadDirectory
    val audioCacheDirectory: java.io.File get() = storage.audioCacheDirectory

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
