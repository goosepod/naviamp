package app.naviamp.android

import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.storage.NaviampStorageQueries

class AndroidStorageMaintenanceStore(
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
        audioCacheDirectory: String,
        downloadDirectory: String,
    ): StorageCacheStats =
        StorageCacheStats(
            databaseLabel = databaseLabel,
            mediaSourceCount = queries.mediaSourceCount().executeAsOne(),
            playbackSessionCount = queries.playbackSessionCount().executeAsOne(),
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
            libraryArtistCount = queries.libraryArtistCount().executeAsOne(),
            libraryAlbumCount = queries.libraryAlbumCount().executeAsOne(),
            libraryTrackCount = queries.libraryTrackCount().executeAsOne(),
            audioCacheDirectory = audioCacheDirectory,
            downloadDirectory = downloadDirectory,
        )
}
