package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.storage.NaviampStorageQueries
import app.naviamp.storage.StorageMaintenanceStore

class AndroidStorageMaintenanceStore(
    private val queries: NaviampStorageQueries,
) {
    private val shared = StorageMaintenanceStore(queries)

    fun clearCacheData(deleteKnownAudioFile: (String) -> Boolean) {
        shared.clearCacheData(deleteKnownAudioFile)
    }

    fun clearDownloadData(deleteKnownDownloadFile: (String) -> Boolean) {
        shared.clearDownloadData(deleteKnownDownloadFile)
    }

    fun clearProviderData() {
        shared.clearProviderData()
    }

    fun clearAllRows() {
        shared.clearAllRows()
    }

    fun stats(
        databaseLabel: String,
        audioCacheDirectory: String,
        downloadDirectory: String,
    ): StorageCacheStats {
        val row = queries.storageStats().executeAsOne()
        return StorageCacheStats(
            databaseLabel = databaseLabel,
            mediaSourceCount = row.media_source_count,
            playbackSessionCount = queries.playbackSessionCount().executeAsOne(),
            imageCount = row.image_count,
            imageBytes = row.image_bytes,
            responseCount = row.response_count,
            audioCount = row.audio_count,
            audioBytes = row.audio_bytes,
            downloadCount = row.download_count,
            downloadBytes = row.download_bytes,
            audioWaveformCount = row.audio_waveform_count,
            audioWaveformBytes = row.audio_waveform_bytes,
            lyricsCount = row.lyrics_count,
            lyricsBytes = row.lyrics_bytes,
            libraryArtistCount = row.library_artist_count,
            libraryAlbumCount = row.library_album_count,
            libraryTrackCount = row.library_track_count,
            pendingProviderActionCount = row.pending_provider_action_count,
            failedPendingProviderActionCount = row.failed_pending_provider_action_count,
            audioCacheDirectory = audioCacheDirectory,
            downloadDirectory = downloadDirectory,
        )
    }
}
