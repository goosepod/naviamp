package app.naviamp.domain.cache

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.popular.ArtistPopularTracksRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform

interface ImageCacheRepository {
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
    ): AudioWaveform?
}

data class StoredAudioBytes(
    val filePath: String,
    val sizeBytes: Long,
)

interface DownloadAudioByteStore {
    suspend fun writeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
        qualityKey: String,
        contentType: String?,
        provider: MediaProvider,
        streamUrl: String,
    ): StoredAudioBytes

    fun deleteDownloadedAudio(filePath: String)
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

    fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track?

    fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long = 50): List<Track>

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
