package app.naviamp.storage

import app.naviamp.domain.cache.StorageCacheStats

class StorageMaintenanceStore(
    private val queries: NaviampStorageQueries,
) {
    /** Exact native paths owned by cache rows; maintenance must never infer files from a directory scan. */
    fun clearCacheData(deleteKnownAudioFile: (String) -> Boolean) {
        queries.selectAllCachedAudio().executeAsList().forEach { row ->
            if (deleteKnownAudioFile(row.file_path)) {
                queries.deleteCachedAudio(row.source_id, row.remote_track_id, row.quality_key)
            }
        }
        clearNonFileCacheDataRows()
    }

    fun clearDownloadData(deleteKnownDownloadFile: (String) -> Boolean) {
        queries.selectAllDownloadedAudio().executeAsList().forEach { row ->
            if (deleteKnownDownloadFile(row.file_path)) {
                queries.deleteDownloadedAudio(row.source_id, row.remote_track_id, row.quality_key)
            }
        }
    }

    fun clearProviderData() {
        queries.clearResponses()
    }

    private fun clearNonFileCacheDataRows() {
        queries.transaction {
            queries.clearResponses()
            queries.clearImages()
            queries.clearAudioWaveforms()
            queries.clearLyrics()
            queries.clearOnlineLyrics()
            queries.clearSidecarStatuses()
        }
    }

    fun clearAllRows() {
        queries.clearMediaSources()
    }

    fun stats(
        databaseLabel: String,
        databaseBytes: Long = 0L,
        hotImageCount: Int = 0,
        hotImageBytes: Long = 0L,
        maxImageBytes: Long = 0L,
        maxAudioBytes: Long = 0L,
        maxAudioWaveformBytes: Long = 0L,
        maxHotImageBytes: Long = 0L,
        audioCacheDirectory: String = "",
        downloadDirectory: String = "",
    ): StorageCacheStats {
        val row = queries.storageStats().executeAsOne()
        return StorageCacheStats(
            databaseLabel = databaseLabel,
            databaseBytes = databaseBytes,
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
            audioCacheDirectory = audioCacheDirectory,
            downloadDirectory = downloadDirectory,
        )
    }
}
