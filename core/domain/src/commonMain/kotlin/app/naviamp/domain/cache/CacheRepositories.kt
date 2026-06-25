package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.popular.ArtistPopularTracksRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform

interface ImageCacheRepository {
    suspend fun cachedImageBytes(url: String): ByteArray?

    suspend fun imageBytes(url: String): ByteArray
}

interface ProviderResponseCacheRepository {
    suspend fun <T> cachedProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T

    fun invalidateProviderResponses(
        provider: MediaProvider,
        resourceType: String,
    )

    fun invalidateProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
    )
}

interface AudioCacheRepository<CachedFile, CachedMetadata> {
    fun updateAudioCacheLimit(maxBytes: Long)

    fun cachedAudioMetadata(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): CachedMetadata?

    suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): CachedFile?

    suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): CachedFile? = null

    suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): CachedFile
}

interface AudioWaveformCacheRepository {
    suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int = DefaultWaveformBucketCount,
    ): AudioWaveform?
}

interface AudioWaveformStorageRepository : AudioWaveformCacheRepository {
    suspend fun storeAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform
}

interface AudioWaveformRepository : AudioWaveformCacheRepository {
    suspend fun ensureAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform?
}

interface LyricsSidecarRepository {
    suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
    ): Lyrics?

    suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics

    suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
    ): Lyrics?
}

interface LyricsOffsetRepository {
    fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int?

    fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int)
}

interface SidecarStatusRepository {
    fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
        errorMessage: String? = null,
    )
}

data class StoredAudioBytes(
    val filePath: String,
    val sizeBytes: Long,
)

fun interface AudioByteWriter {
    suspend fun write(bytes: ByteArray, count: Int)
}

interface AudioByteStore {
    suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes

    fun deleteAudioBytes(filePath: String)
}

interface DownloadRepository<DownloadedFile, DownloadedTrack> {
    suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): DownloadedFile?

    suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): DownloadedFile?

    suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedFile

    fun downloadedTracks(sourceId: String): List<DownloadedTrack>

    fun removeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    )

    fun removeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
    )
}

interface DownloadReplacementRepository<DownloadedFile> {
    suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedFile
}

interface PlaybackHistoryRepository<HistoryItem> {
    fun playbackHistory(sourceId: String, limit: Int = 50): List<HistoryItem>

    fun recordPlaybackHistory(
        sourceId: String,
        track: Track,
        playedAtEpochMillis: Long,
    )
}

interface TrackMetadataRepository {
    fun updateTrack(updatedTrack: Track)
}

interface MediaSourceRepository {
    fun latestMediaSource(): SavedMediaSource?

    fun mediaSources(): List<SavedMediaSource>

    fun mediaSource(sourceId: String): SavedMediaSource?

    fun deleteMediaSource(sourceId: String)
}

data class ProviderMediaSourceConnection(
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val nativeToken: String? = null,
    val tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
)

interface ProviderMediaSourceRepository {
    fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity
}

interface PlaybackSessionRepository {
    fun loadPlaybackSession(sourceId: String? = null): PlaybackSessionSettings?

    fun savePlaybackSession(
        session: PlaybackSessionSettings?,
        sourceId: String? = null,
    )
}

interface LocalLibraryIndexRepository : ArtistPopularTracksRepository {
    fun mediaSource(sourceId: String): SavedMediaSource?

    fun markLibraryScanChecked(sourceId: String, signature: String)

    fun markLibrarySyncStarted(sourceId: String)

    fun markLibrarySyncCompleted(sourceId: String)

    fun upsertLibraryArtists(sourceId: String, artists: List<Artist>)

    fun upsertLibraryAlbums(sourceId: String, albums: List<Album>)

    fun upsertLibraryTracks(sourceId: String, tracks: List<Track>)

    fun librarySnapshot(sourceId: String, limit: Long = 50, offset: Long = 0): LibrarySnapshot

    fun searchLibrary(sourceId: String, query: String, limit: Long = 50, offset: Long = 0): LibrarySnapshot

    fun recentlyPlayedLibraryTracks(sourceId: String, limit: Long = 50): List<Track> = emptyList()

    fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track?

    fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long = 50): List<Track>

    fun libraryTracksForAlbumTitle(
        sourceId: String,
        albumTitle: String,
        artistName: String?,
        limit: Long = 50,
    ): List<Track> = emptyList()

    fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track?

    fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long = 50): List<Track>

    fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long = 50): List<Track>

    fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long = 40): List<Track>

    fun libraryIndexStats(sourceId: String): LibraryIndexStats

    fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear>

    fun clearLibraryData(sourceId: String? = null)
}

interface CacheMaintenanceRepository<Stats> {
    fun clearProviderData()

    fun clearCacheData()

    fun clearDownloadData()

    fun clearAll()

    fun stats(): Stats
}

data class StorageCacheStats(
    val databaseLabel: String = "",
    val databaseBytes: Long = 0L,
    val mediaSourceCount: Long = 0L,
    val playbackSessionCount: Long = 0L,
    val imageCount: Long = 0L,
    val imageBytes: Long = 0L,
    val responseCount: Long = 0L,
    val audioCount: Long = 0L,
    val audioBytes: Long = 0L,
    val downloadCount: Long = 0L,
    val downloadBytes: Long = 0L,
    val audioWaveformCount: Long = 0L,
    val audioWaveformBytes: Long = 0L,
    val lyricsCount: Long = 0L,
    val lyricsBytes: Long = 0L,
    val libraryArtistCount: Long = 0L,
    val libraryAlbumCount: Long = 0L,
    val libraryTrackCount: Long = 0L,
    val pendingProviderActionCount: Long = 0L,
    val failedPendingProviderActionCount: Long = 0L,
    val hotImageCount: Int = 0,
    val hotImageBytes: Long = 0L,
    val maxImageBytes: Long = 0L,
    val maxAudioBytes: Long = 0L,
    val maxAudioWaveformBytes: Long = 0L,
    val maxHotImageBytes: Long = 0L,
    val audioCacheDirectory: String = "",
    val downloadDirectory: String = "",
)

data class LibrarySnapshot(
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
) {
    val isEmpty: Boolean
        get() = artists.isEmpty() && albums.isEmpty() && tracks.isEmpty()
}

data class LibraryIndexStats(
    val artistCount: Long,
    val albumCount: Long,
    val trackCount: Long,
) {
    val hasUsableIndex: Boolean
        get() = artistCount > 0L || albumCount > 0L || trackCount > 0L
}

data class LibraryAlbumYear(
    val year: Int,
    val albumCount: Long,
)
