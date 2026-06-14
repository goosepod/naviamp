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
    ): StorageCacheStats =
        StorageCacheStats(
            databaseLabel = databaseLabel,
            databaseBytes = databaseBytes,
            imageCount = queries.imageCacheCount().executeAsOne(),
            imageBytes = queries.imageCacheSize().executeAsOne(),
            responseCount = queries.responseCacheCount().executeAsOne(),
            audioCount = queries.audioCacheCount().executeAsOne(),
            audioBytes = queries.audioCacheSize().executeAsOne(),
            downloadCount = queries.downloadedAudioCount().executeAsOne(),
            downloadBytes = queries.downloadedAudioSize().executeAsOne(),
            audioWaveformCount = queries.audioWaveformCacheCount().executeAsOne(),
            audioWaveformBytes = queries.audioWaveformCacheSize().executeAsOne(),
            lyricsCount = queries.lyricsCacheCount().executeAsOne() + queries.lrclibLyricsCacheCount().executeAsOne(),
            lyricsBytes = queries.lyricsCacheSize().executeAsOne() + queries.lrclibLyricsCacheSize().executeAsOne(),
            mediaSourceCount = queries.mediaSourceCount().executeAsOne(),
            libraryArtistCount = queries.libraryArtistCount().executeAsOne(),
            libraryAlbumCount = queries.libraryAlbumCount().executeAsOne(),
            libraryTrackCount = queries.libraryTrackCount().executeAsOne(),
            pendingProviderActionCount = queries.pendingProviderActionCount().executeAsOne(),
            failedPendingProviderActionCount = queries.failedPendingProviderActionCount().executeAsOne(),
            hotImageCount = hotImageCount,
            hotImageBytes = hotImageBytes,
            maxImageBytes = maxImageBytes,
            maxAudioBytes = maxAudioBytes,
            maxAudioWaveformBytes = maxAudioWaveformBytes,
            maxHotImageBytes = maxHotImageBytes,
        )
}
