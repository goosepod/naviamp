package app.naviamp.desktop

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.storage.NaviampStorageQueries

class DesktopStorageMaintenanceStore(
    private val queries: NaviampStorageQueries,
) {
    fun clearProviderData() {
        queries.clearResponses()
    }

    fun clearCacheDataRows() {
        queries.transaction {
            queries.clearResponses()
            queries.clearImages()
            queries.clearAudioWaveforms()
            queries.clearAudio()
            queries.clearLyrics()
            queries.clearLrclibLyrics()
            queries.clearSidecarStatuses()
        }
    }

    fun clearDownloadDataRows() {
        queries.clearDownloads()
    }

    fun clearAllRows() {
        queries.clearMediaSources()
    }

    fun stats(
        databaseLabel: String,
        databaseBytes: Long,
        hotImageCount: Int,
        hotImageBytes: Long,
        maxImageBytes: Long,
        maxAudioBytes: Long,
        maxAudioWaveformBytes: Long,
        maxHotImageBytes: Long,
    ): StorageCacheStats {
        val row = queries.storageStats().executeAsOne()
        return StorageCacheStats(
            databaseLabel = databaseLabel,
            databaseBytes = databaseBytes,
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
            mediaSourceCount = row.media_source_count,
            libraryArtistCount = row.library_artist_count,
            libraryAlbumCount = row.library_album_count,
            libraryTrackCount = row.library_track_count,
            pendingProviderActionCount = row.pending_provider_action_count,
            failedPendingProviderActionCount = row.failed_pending_provider_action_count,
            hotImageCount = hotImageCount,
            hotImageBytes = hotImageBytes,
            maxImageBytes = maxImageBytes,
            maxAudioBytes = maxAudioBytes,
            maxAudioWaveformBytes = maxAudioWaveformBytes,
            maxHotImageBytes = maxHotImageBytes,
        )
    }
}
