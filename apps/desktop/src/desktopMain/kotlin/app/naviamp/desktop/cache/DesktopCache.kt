package app.naviamp.desktop

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.cash.sqldelight.db.QueryResult
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Lyrics
import app.naviamp.domain.ReplayGain
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioByteStore
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.AudioByteWriter
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioWaveformRepository
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.LibraryAlbumYear
import app.naviamp.domain.cache.LibraryIndexStats
import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LyricsSidecarCacheService
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ObjectByteStoreService
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheService
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.cache.StoredAudioBytes
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.cache.TrackMetadataRepository
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.settings.DefaultWaveformBucketCount
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.domain.waveform.AudioWaveformAnalysisSource
import app.naviamp.domain.waveform.AudioWaveformAnalyzer as DomainAudioWaveformAnalyzer
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.storage.NaviampStorageDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists

class DesktopCache(
    private val databasePath: Path = defaultCacheDatabasePath(),
    private val maxImageCacheBytes: Long = 500L * 1024L * 1024L,
    private var maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L,
    private val maxAudioWaveformCacheBytes: Long = 32L * 1024L * 1024L,
    private val maxHotImageBytes: Long = 32L * 1024L * 1024L,
    private val audioCacheDirectory: Path = defaultAudioCacheDirectory(),
    private var downloadDirectory: Path = DesktopDownloadDirectories.defaultDirectory(),
) : ImageCacheRepository,
    ProviderResponseCacheRepository,
    AudioCacheRepository<CachedAudioFile, CachedAudioMetadata>,
    AudioWaveformRepository,
    AudioWaveformStorageRepository,
    LyricsSidecarRepository,
    LyricsOffsetRepository,
    SidecarStatusRepository,
    DownloadRepository<DownloadedAudioFile, DownloadedTrack>,
    DownloadReplacementRepository<DownloadedAudioFile>,
    MediaSourceRepository,
    ProviderMediaSourceRepository,
    LocalLibraryIndexRepository,
    PendingProviderActionRepository,
    RadioDjPresetRepository,
    CacheMaintenanceRepository<StorageCacheStats>,
    TrackMetadataRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val database = createDatabase(databasePath)
    private val queries = database.naviampStorageQueries
    private val providerResponseCache = ProviderResponseCacheService(
        store = DesktopProviderResponseStore(queries),
        nowMillis = ::nowMillis,
    )
    private val sidecarStatus = SidecarStatusService(
        store = DesktopSidecarStatusStore(queries),
        nowMillis = ::nowMillis,
    )
    private val mediaSources = DesktopMediaSourceStore(
        queries = queries,
        nowMillis = ::nowMillis,
    )
    private val libraryIndex = DesktopLibraryIndexStore(
        queries = queries,
        mediaSources = mediaSources,
        nowMillis = ::nowMillis,
    )
    private val lyricsSidecar = LyricsSidecarCacheService(
        store = DesktopLyricsSidecarStore(queries),
        nowMillis = ::nowMillis,
        json = json,
    )
    private val lyricsOffsets = DesktopLyricsOffsetStore(queries, ::nowMillis)
    private val maintenance = DesktopStorageMaintenanceStore(queries)
    private val audioWaveforms = DesktopAudioWaveformStore(
        queries = queries,
        json = json,
        nowMillis = ::nowMillis,
        maxAudioWaveformCacheBytes = maxAudioWaveformCacheBytes,
    )
    private val trackMetadata = DesktopTrackMetadataStore(queries, json)
    private val pendingProviderActions = DesktopPendingProviderActionStore(
        queries = queries,
        nowMillis = ::nowMillis,
    )
    private val radioDjPresets = DesktopRadioDjPresetStore(
        queries = queries,
        nowMillis = ::nowMillis,
    )
    private val hotImages = DesktopHotImageCache(maxHotImageBytes)
    private val httpClient = KtorSharedHttpClient()
    private val imageByteStoreService = ObjectByteStoreService(
        store = DesktopObjectByteStore(
            queries = queries,
            nowMillis = ::nowMillis,
            maxImageCacheBytes = maxImageCacheBytes,
        ),
        httpClient = httpClient,
    )
    private val audioCacheByteStoreService = AudioByteStoreService(
        store = DesktopAudioByteStore(audioCacheDirectory),
        httpClient = httpClient,
    )
    private val downloadAudioByteStore = DesktopMutableAudioByteStore(downloadDirectory)
    private val downloadAudioByteStoreService = AudioByteStoreService(
        store = downloadAudioByteStore,
        httpClient = httpClient,
    )
    private val audioStore = DesktopAudioStore(
        queries = queries,
        audioCacheByteStoreService = audioCacheByteStoreService,
        downloadAudioByteStoreService = downloadAudioByteStoreService,
        nowMillis = ::nowMillis,
        maxAudioCacheBytes = maxAudioCacheBytes,
    )
    private val fileTreeCleaner = DesktopFileTreeCleaner()

    override suspend fun imageBytes(url: String): ByteArray {
        hotImages.get(url)?.let { return it }

        return withContext(Dispatchers.IO + NonCancellable) {
            val bytes = imageByteStoreService.remoteBytes(url)
            hotImages.put(url, bytes)
            bytes
        }
    }

    override suspend fun cachedImageBytes(url: String): ByteArray? {
        hotImages.get(url)?.let { return it }

        return withContext(Dispatchers.IO + NonCancellable) {
            imageByteStoreService.cachedBytes(url)?.also { bytes ->
                hotImages.put(url, bytes)
            }
        }
    }

    suspend fun recentlyAddedAlbums(
        provider: MediaProvider,
        limit: Int,
    ): List<Album> =
        cached(
            provider = provider,
            resourceType = "recentlyAddedAlbums",
            resourceId = limit.toString(),
            decode = { json.decodeFromString<List<AlbumDto>>(it).map { dto -> dto.toAlbum() } },
            encode = { json.encodeToString(it.map { album -> AlbumDto.fromAlbum(album) }) },
            fetch = { provider.recentlyAddedAlbums(limit) },
        )

    suspend fun album(
        provider: MediaProvider,
        albumId: AlbumId,
    ): AlbumDetails =
        cached(
            provider = provider,
            resourceType = "album",
            resourceId = albumId.value,
            decode = { json.decodeFromString<AlbumDetailsDto>(it).toAlbumDetails() },
            encode = { json.encodeToString(AlbumDetailsDto.fromAlbumDetails(it)) },
            fetch = { provider.album(albumId) },
        )

    suspend fun artist(
        provider: MediaProvider,
        artistId: ArtistId,
    ): ArtistDetails =
        cached(
            provider = provider,
            resourceType = "artist",
            resourceId = artistId.value,
            decode = { json.decodeFromString<ArtistDetailsDto>(it).toArtistDetails() },
            encode = { json.encodeToString(ArtistDetailsDto.fromArtistDetails(it)) },
            fetch = { provider.artist(artistId) },
        )

    suspend fun search(
        provider: MediaProvider,
        query: String,
        limit: Int,
    ): MediaSearchResults =
        cached(
            provider = provider,
            resourceType = "search",
            resourceId = "${query.trim().lowercase()}:$limit",
            decode = { json.decodeFromString<MediaSearchResultsDto>(it).toMediaSearchResults() },
            encode = { json.encodeToString(MediaSearchResultsDto.fromMediaSearchResults(it)) },
            fetch = { provider.search(query, limit) },
        )

    override fun latestMediaSource(): SavedMediaSource? =
        mediaSources.latestMediaSource()

    override fun mediaSources(): List<SavedMediaSource> =
        mediaSources.mediaSources()

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        mediaSources.mediaSource(sourceId)

    override fun deleteMediaSource(sourceId: String) {
        mediaSources.deleteMediaSource(sourceId)
    }

    override fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioCacheBytes = maxBytes.coerceAtLeast(0)
        audioStore.updateAudioCacheLimit(maxBytes)
    }

    fun updateDownloadDirectory(directory: Path) {
        val normalizedDirectory = DesktopDownloadDirectories.prepare(directory)
        downloadDirectory = normalizedDirectory
        downloadAudioByteStore.updateDirectory(normalizedDirectory)
    }

    fun downloadDirectory(): Path =
        downloadDirectory

    override fun cachedAudioMetadata(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): CachedAudioMetadata? =
        audioStore.cachedAudioMetadata(sourceId, trackId, quality)

    override suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): CachedAudioFile? =
        audioStore.cachedAudioFile(sourceId, trackId, quality)

    override suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): CachedAudioFile? =
        audioStore.cachedAudioFile(sourceId, trackId)

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): DownloadedAudioFile? =
        audioStore.downloadedAudioFile(sourceId, trackId, quality)

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): DownloadedAudioFile? =
        audioStore.downloadedAudioFile(sourceId, trackId)

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? =
        audioWaveforms.cachedAudioWaveform(sourceId, trackId, quality, bucketCount)

    override suspend fun ensureAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AudioWaveform? =
        ensureAudioWaveform(sourceId, trackId, quality, DesktopAudioWaveformAnalyzer())

    suspend fun ensureAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        analyzer: DomainAudioWaveformAnalyzer = DesktopAudioWaveformAnalyzer(),
        bucketCount: Int = DefaultWaveformBucketCount,
    ): AudioWaveform? =
        withContext(Dispatchers.IO) {
            cachedAudioWaveform(sourceId, trackId, quality, bucketCount)?.let { return@withContext it }
            val audioPath = downloadedAudioFile(sourceId, trackId, quality)?.path
                ?: cachedAudioFile(sourceId, trackId, quality)?.path
                ?: return@withContext null
            val waveform = analyzer.analyze(
                AudioWaveformAnalysisSource(
                    cacheKey = trackId.value,
                    streamUrl = audioPath.toUri().toString(),
                    bucketCount = bucketCount,
                ),
            ) ?: return@withContext null
            storeAudioWaveform(
                sourceId = sourceId,
                trackId = trackId,
                quality = quality,
                audioFilePath = audioPath.toAbsolutePath().toString(),
                waveform = waveform,
            )
        }

    override suspend fun storeAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFilePath: String?,
        waveform: AudioWaveform,
    ): AudioWaveform =
        audioWaveforms.storeAudioWaveform(sourceId, trackId, quality, audioFilePath, waveform)

    override suspend fun providerLyrics(
        sourceId: String,
        provider: MediaProvider,
        trackId: TrackId,
    ): Lyrics? =
        lyricsSidecar.providerLyrics(sourceId, provider, trackId)

    override suspend fun cacheEmbeddedLyrics(
        sourceId: String,
        trackId: TrackId,
        lyrics: Lyrics,
    ): Lyrics =
        lyricsSidecar.cacheEmbeddedLyrics(sourceId, trackId, lyrics)

    override suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
    ): Lyrics? =
        lrclibLyrics(sourceId, track, DesktopLrclibLyricsClient())

    suspend fun lrclibLyrics(
        sourceId: String,
        track: Track,
        client: DesktopLrclibLyricsClient = DesktopLrclibLyricsClient(),
    ): Lyrics? =
        lyricsSidecar.lrclibLyrics(sourceId, track, client)

    override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int =
        lyricsOffsets.lyricsOffsetMillis(sourceId, trackId)

    override fun saveLyricsOffsetMillis(sourceId: String, trackId: TrackId, offsetMillis: Int) {
        lyricsOffsets.saveLyricsOffsetMillis(sourceId, trackId, offsetMillis)
    }

    override fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
        errorMessage: String?,
    ) {
        sidecarStatus.recordSidecarStatus(sourceId, trackId, quality, sidecarType, success, errorMessage)
    }

    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) {
        pendingProviderActions.enqueuePendingProviderAction(
            sourceId = sourceId,
            actionType = actionType,
            entityId = entityId,
            boolValue = boolValue,
            longValue = longValue,
            replaceMatchingEntityAction = replaceMatchingEntityAction,
        )
    }

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> =
        pendingProviderActions.pendingProviderActions(sourceId, limit)

    override fun deletePendingProviderAction(id: Long) {
        pendingProviderActions.deletePendingProviderAction(id)
    }

    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) {
        pendingProviderActions.markPendingProviderActionFailed(id, errorMessage)
    }

    override fun radioDjPresets(): List<RadioDjPreset> =
        radioDjPresets.radioDjPresets()

    override fun replaceRadioDjPresets(presets: List<RadioDjPreset>) {
        radioDjPresets.replaceRadioDjPresets(presets)
    }

    override fun upsertRadioDjPreset(preset: RadioDjPreset) {
        radioDjPresets.upsertRadioDjPreset(preset)
    }

    override fun deleteRadioDjPreset(id: String) {
        radioDjPresets.deleteRadioDjPreset(id)
    }

    fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
    ) {
        recordSidecarStatus(sourceId, trackId, quality, sidecarType, success, null)
    }

    suspend fun cachedLyrics(
        sourceId: String,
        trackId: TrackId,
    ): Lyrics? =
        lyricsSidecar.cachedLyrics(sourceId, trackId)

    override suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): CachedAudioFile =
        audioStore.cacheAudioTrack(sourceId, provider, track, quality)

    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedAudioFile =
        audioStore.downloadAudioTrack(sourceId, provider, track, quality, maxDownloadBytes)

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): DownloadedAudioFile =
        audioStore.replaceDownloadedAudioTrack(sourceId, provider, track, quality, maxDownloadBytes)

    override fun downloadedTracks(sourceId: String): List<DownloadedTrack> =
        audioStore.downloadedTracks(sourceId)

    override fun removeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ) {
        audioStore.removeDownloadedAudio(sourceId, trackId, quality)
    }

    override fun removeDownloadedAudio(
        sourceId: String,
        trackId: TrackId,
    ) {
        audioStore.removeDownloadedAudio(sourceId, trackId)
    }

    fun upsertNavidromeSource(
        connection: NavidromeConnection,
        provider: MediaProvider,
    ): SavedMediaSource {
        val identity = upsertProviderMediaSource(
            connection = ProviderMediaSourceConnection(
                displayName = connection.resolvedDisplayName(),
                baseUrl = connection.baseUrl,
                username = connection.username,
                token = connection.token,
                salt = connection.salt,
                nativeToken = connection.nativeToken,
                tlsSettings = connection.tlsSettings,
            ),
            cacheNamespace = provider.cacheNamespace,
            providerId = provider.id.value,
        )
        return mediaSource(identity.id)
            ?: throw IllegalStateException("Media source ${identity.id} was not persisted.")
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity =
        mediaSources.upsertProviderMediaSource(connection, cacheNamespace, providerId)

    override fun markLibrarySyncStarted(sourceId: String) {
        libraryIndex.markLibrarySyncStarted(sourceId)
    }

    override fun markLibrarySyncCompleted(sourceId: String) {
        libraryIndex.markLibrarySyncCompleted(sourceId)
    }

    override fun markLibraryScanChecked(sourceId: String, signature: String) {
        libraryIndex.markLibraryScanChecked(sourceId, signature)
    }

    override fun upsertLibraryArtists(sourceId: String, artists: List<Artist>) {
        libraryIndex.upsertLibraryArtists(sourceId, artists)
    }

    override fun upsertLibraryAlbums(sourceId: String, albums: List<Album>) {
        libraryIndex.upsertLibraryAlbums(sourceId, albums)
    }

    override fun upsertLibraryTracks(sourceId: String, tracks: List<Track>) {
        libraryIndex.upsertLibraryTracks(sourceId, tracks)
    }

    override fun librarySnapshot(sourceId: String, limit: Long, offset: Long): LibrarySnapshot =
        libraryIndex.librarySnapshot(sourceId, limit, offset)

    override fun searchLibrary(sourceId: String, query: String, limit: Long, offset: Long): LibrarySnapshot =
        libraryIndex.searchLibrary(sourceId, query, limit, offset)

    override fun randomLibraryTrackForAlbum(sourceId: String, albumId: AlbumId): Track? =
        libraryIndex.randomLibraryTrackForAlbum(sourceId, albumId)

    override fun libraryTracksForAlbum(sourceId: String, albumId: AlbumId, limit: Long): List<Track> =
        libraryIndex.libraryTracksForAlbum(sourceId, albumId, limit)

    override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
        libraryIndex.randomLibraryTrackForArtist(sourceId, artistId)

    override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
        libraryIndex.libraryTracksForArtist(sourceId, artistId, limit)

    override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
        libraryIndex.libraryTracksForArtistName(sourceId, artistName, limit)

    override fun artistPopularTracks(sourceId: String, artistId: ArtistId, source: String): List<ArtistPopularTrackMatch> =
        libraryIndex.artistPopularTracks(sourceId, artistId, source)

    override fun replaceArtistPopularTracks(
        sourceId: String,
        artistId: ArtistId,
        source: String,
        candidates: List<ArtistPopularTrackCandidate>,
        matchedTracksBySourceTrackId: Map<String, Track>,
        fetchedAtEpochMillis: Long,
    ) {
        libraryIndex.replaceArtistPopularTracks(
            sourceId = sourceId,
            artistId = artistId,
            source = source,
            candidates = candidates,
            matchedTracksBySourceTrackId = matchedTracksBySourceTrackId,
            fetchedAtEpochMillis = fetchedAtEpochMillis,
        )
    }

    override fun relatedLibraryTracks(sourceId: String, track: Track, limit: Long): List<Track> =
        libraryIndex.relatedLibraryTracks(sourceId, track, limit)

    override fun libraryIndexStats(sourceId: String): LibraryIndexStats =
        libraryIndex.libraryIndexStats(sourceId)

    override fun libraryAlbumYears(sourceId: String): List<LibraryAlbumYear> =
        libraryIndex.libraryAlbumYears(sourceId)

    fun libraryOffsetForLetter(sourceId: String, tab: DesktopLibraryTab, letter: Char): Long {
        return libraryIndex.libraryOffsetForLetter(sourceId, tab, letter)
    }

    override fun updateTrack(updatedTrack: Track) {
        trackMetadata.updateTrack(updatedTrack)
    }

    override fun clearProviderData() {
        maintenance.clearProviderData()
    }

    override fun clearCacheData() {
        hotImages.clear()
        maintenance.clearCacheDataRows()
        clearAudioFiles()
    }

    override fun clearDownloadData() {
        clearDownloadFiles()
        maintenance.clearDownloadDataRows()
    }

    override fun clearLibraryData(sourceId: String?) {
        libraryIndex.clearLibraryData(sourceId)
    }

    override fun clearAll() {
        clearCacheData()
        clearDownloadData()
        clearLibraryData(null)
        maintenance.clearAllRows()
    }

    override fun stats(): StorageCacheStats =
        maintenance.stats(
            databaseLabel = databasePath.toAbsolutePath().toString(),
            databaseBytes = databasePath.sizeOrZero(),
            hotImageCount = hotImages.count(),
            hotImageBytes = hotImages.sizeBytes(),
            maxImageBytes = maxImageCacheBytes,
            maxAudioBytes = maxAudioCacheBytes,
            maxAudioWaveformBytes = maxAudioWaveformCacheBytes,
            maxHotImageBytes = maxHotImageBytes,
        )

    override suspend fun <T> cachedProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T =
        providerResponseCache.cachedProviderResponse(provider, resourceType, resourceId, decode, encode, fetch)

    override fun invalidateProviderResponses(
        provider: MediaProvider,
        resourceType: String,
    ) {
        providerResponseCache.invalidateProviderResponses(provider, resourceType)
    }

    override fun invalidateProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
    ) {
        providerResponseCache.invalidateProviderResponse(provider, resourceType, resourceId)
    }

    private suspend fun <T> cached(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T =
        cachedProviderResponse(provider, resourceType, resourceId, decode, encode, fetch)

    private fun clearAudioFiles() {
        fileTreeCleaner.clearDirectoryContents(audioCacheDirectory)
    }

    private fun clearDownloadFiles() {
        queries.selectAllDownloadedAudio().executeAsList().forEach { row ->
            fileTreeCleaner.deleteFile(Path.of(row.file_path))
        }
    }
}

object DesktopCaches {
    val session = DesktopCache()
}

object DesktopDownloadDirectories {
    fun defaultDirectory(): Path =
        defaultDownloadDirectory()

    fun fromSetting(path: String?): Path =
        path?.takeIf { it.isNotBlank() }?.let { Path.of(it) } ?: defaultDirectory()

    fun prepare(directory: Path): Path {
        Files.createDirectories(directory)
        require(Files.isDirectory(directory)) { "Download path is not a directory." }
        require(Files.isWritable(directory)) { "Download path is not writable." }
        return directory.toAbsolutePath().normalize()
    }
}

data class CachedAudioFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
)

data class CachedAudioMetadata(
    val path: Path,
    val exists: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

data class DownloadedAudioFile(
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
)

data class DownloadedTrack(
    val track: Track,
    val path: Path,
    val sizeBytes: Long,
    val contentType: String?,
    val downloadedAtEpochMillis: Long,
)

private fun createDatabase(path: Path): NaviampStorageDatabase {
    Files.createDirectories(path.parent)
    val exists = path.exists()
    registerSqliteDriver()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${path.toAbsolutePath()}")
    configureSqliteLockHandling(driver)
    if (!exists) {
        NaviampStorageDatabase.Schema.create(driver)
    } else {
        val oldVersion = driver.databaseVersion()
        val newVersion = NaviampStorageDatabase.Schema.version
        if (oldVersion in 1 until newVersion) {
            NaviampStorageDatabase.Schema.migrate(driver, oldVersion, newVersion)
        }
    }
    ensureMediaSourceLibraryScanSchema(driver)
    ensureArtistPopularTracksSchema(driver)
    ensureCachedSidecarStatusSchema(driver)
    ensureTrackLyricsOffsetSchema(driver)
    ensurePendingProviderActionSchema(driver)
    ensureLibraryTrackPlayMetadataSchema(driver)
    ensureRadioDjPresetSchema(driver)
    driver.execute(null, "PRAGMA foreign_keys=ON", 0)
    return NaviampStorageDatabase(driver)
}

private fun configureSqliteLockHandling(driver: JdbcSqliteDriver) {
    driver.execute(null, "PRAGMA busy_timeout=$SqliteBusyTimeoutMillis", 0)
    driver.execute(null, "PRAGMA journal_mode=WAL", 0)
}

private fun registerSqliteDriver() {
    Class.forName("org.sqlite.JDBC")
}

private fun JdbcSqliteDriver.databaseVersion(): Long =
    executeQuery(null, "PRAGMA user_version", { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
    }, 0).value

private fun ensureMediaSourceLibraryScanSchema(driver: JdbcSqliteDriver) {
    if (!driver.tableHasColumn("media_source", "native_token")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN native_token TEXT", 0)
    }
    if (!driver.tableHasColumn("media_source", "last_library_scan_signature")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN last_library_scan_signature TEXT", 0)
    }
    if (!driver.tableHasColumn("media_source", "last_library_scan_checked_at_epoch_millis")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN last_library_scan_checked_at_epoch_millis INTEGER", 0)
    }
    if (!driver.tableHasColumn("media_source", "secondary_urls_json")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN secondary_urls_json TEXT", 0)
    }
    if (!driver.tableHasColumn("media_source", "custom_headers_json")) {
        driver.execute(null, "ALTER TABLE media_source ADD COLUMN custom_headers_json TEXT", 0)
    }
}

private fun JdbcSqliteDriver.tableHasColumn(tableName: String, columnName: String): Boolean =
    executeQuery(null, "PRAGMA table_info($tableName)", { cursor ->
        var found = false
        while (cursor.next().value) {
            if (cursor.getString(1) == columnName) {
                found = true
                break
            }
        }
        QueryResult.Value(found)
    }, 0).value

private fun ensureArtistPopularTracksSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS artist_popular_track (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_artist_id TEXT NOT NULL,
          popular_source TEXT NOT NULL,
          source_track_id TEXT NOT NULL,
          rank INTEGER NOT NULL,
          title TEXT NOT NULL,
          album_title TEXT,
          duration_seconds INTEGER,
          matched_remote_track_id TEXT,
          fetched_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_artist_id, popular_source, source_track_id)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS artist_popular_track_artist
        ON artist_popular_track(source_id, remote_artist_id, popular_source, rank)
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS artist_popular_track_match
        ON artist_popular_track(source_id, matched_remote_track_id)
        """.trimIndent(),
        0,
    )
}

private fun ensureCachedSidecarStatusSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS cached_sidecar_status (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          quality_key TEXT NOT NULL,
          sidecar_type TEXT NOT NULL,
          status TEXT NOT NULL,
          attempts INTEGER NOT NULL,
          last_error TEXT,
          updated_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id, quality_key, sidecar_type)
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS cached_sidecar_status_track
        ON cached_sidecar_status(source_id, remote_track_id, quality_key)
        """.trimIndent(),
        0,
    )
}

private fun ensureTrackLyricsOffsetSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS track_lyrics_offset (
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          remote_track_id TEXT NOT NULL,
          offset_millis INTEGER NOT NULL,
          updated_at_epoch_millis INTEGER NOT NULL,
          PRIMARY KEY(source_id, remote_track_id)
        )
        """.trimIndent(),
        0,
    )
}

private fun ensurePendingProviderActionSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS pending_provider_action (
          id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
          source_id TEXT NOT NULL REFERENCES media_source(id) ON DELETE CASCADE,
          action_type TEXT NOT NULL,
          entity_id TEXT NOT NULL,
          bool_value INTEGER,
          long_value INTEGER,
          created_at_epoch_millis INTEGER NOT NULL,
          last_attempt_at_epoch_millis INTEGER,
          attempt_count INTEGER NOT NULL DEFAULT 0,
          last_error TEXT
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS pending_provider_action_source_created
        ON pending_provider_action(source_id, created_at_epoch_millis)
        """.trimIndent(),
        0,
    )
}

private fun ensureLibraryTrackPlayMetadataSchema(driver: JdbcSqliteDriver) {
    if (!driver.tableHasColumn("library_track", "play_count")) {
        driver.execute(null, "ALTER TABLE library_track ADD COLUMN play_count INTEGER", 0)
    }
    if (!driver.tableHasColumn("library_track", "last_played_at_iso8601")) {
        driver.execute(null, "ALTER TABLE library_track ADD COLUMN last_played_at_iso8601 TEXT", 0)
    }
}

private fun ensureRadioDjPresetSchema(driver: JdbcSqliteDriver) {
    driver.execute(
        null,
        """
        CREATE TABLE IF NOT EXISTS radio_dj_preset (
          id TEXT NOT NULL PRIMARY KEY,
          name TEXT NOT NULL,
          familiarity TEXT NOT NULL,
          artist_spread TEXT NOT NULL,
          same_decade_only INTEGER NOT NULL,
          artist_run_mode TEXT NOT NULL,
          same_artist_run_length INTEGER NOT NULL,
          other_artist_run_length INTEGER NOT NULL,
          sort_order INTEGER NOT NULL,
          created_at_epoch_millis INTEGER NOT NULL,
          updated_at_epoch_millis INTEGER NOT NULL
        )
        """.trimIndent(),
        0,
    )
    driver.execute(
        null,
        """
        CREATE INDEX IF NOT EXISTS radio_dj_preset_sort
        ON radio_dj_preset(sort_order, name)
        """.trimIndent(),
        0,
    )
}

private const val SqliteBusyTimeoutMillis = 10_000

private fun defaultCacheDatabasePath(): Path =
    defaultAppDataDirectory().resolve("storage.db")

private fun defaultAudioCacheDirectory(): Path =
    defaultAppDataDirectory().resolve("audio-cache")

private fun defaultDownloadDirectory(): Path =
    defaultAppDataDirectory().resolve("downloads")

private fun defaultAppDataDirectory(): Path {
    val os = System.getProperty("os.name").lowercase()
    val home = Path.of(System.getProperty("user.home"))

    return when {
        os.contains("mac") -> home.resolve("Library").resolve("Application Support").resolve("Naviamp")
        os.contains("win") -> Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString())
            .resolve("Naviamp")
        else -> Path.of(System.getenv("XDG_CACHE_HOME") ?: home.resolve(".cache").toString())
            .resolve("naviamp")
    }
}

private fun nowMillis(): Long =
    System.currentTimeMillis()

private fun StreamQuality.cacheKey(): String =
    when (this) {
        StreamQuality.Original -> "original"
        is StreamQuality.Transcoded -> "transcoded:${codec.name.lowercase()}:$bitrateKbps"
    }

private fun moveDownloadedAudio(temp: Path, target: Path) {
    runCatching {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }.getOrElse {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

private class DesktopAudioByteStore(
    private val directory: Path,
) : AudioByteStore {
    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes {
        Files.createDirectories(directory)
        val target = directory.resolve(fileName)
        val temp = directory.resolve("${target.fileName}.tmp")
        return try {
            Files.newOutputStream(temp).use { output ->
                val writer = AudioByteWriter { bytes, count -> output.write(bytes, 0, count) }
                if (!writeBytes(writer)) throw IllegalStateException(errorMessage)
            }
            moveDownloadedAudio(temp, target)
            StoredAudioBytes(
                filePath = target.toAbsolutePath().toString(),
                sizeBytes = Files.size(target),
            )
        } catch (exception: Exception) {
            Files.deleteIfExists(temp)
            throw exception
        }
    }

    override fun deleteAudioBytes(filePath: String) {
        Files.deleteIfExists(Path.of(filePath))
    }
}

private class DesktopMutableAudioByteStore(
    initialDirectory: Path,
) : AudioByteStore {
    @Volatile
    private var store = DesktopAudioByteStore(initialDirectory)

    fun updateDirectory(directory: Path) {
        store = DesktopAudioByteStore(directory)
    }

    override suspend fun writeAudioBytes(
        fileName: String,
        errorMessage: String,
        writeBytes: suspend (AudioByteWriter) -> Boolean,
    ): StoredAudioBytes =
        store.writeAudioBytes(fileName, errorMessage, writeBytes)

    override fun deleteAudioBytes(filePath: String) {
        store.deleteAudioBytes(filePath)
    }
}

private fun Path.sizeOrZero(): Long =
    runCatching {
        if (exists()) Files.size(this) else 0L
    }.getOrDefault(0L)

@Serializable
data class MediaSearchResultsDto(
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
) {
    fun toMediaSearchResults(): MediaSearchResults =
        MediaSearchResults(
            artists = artists.map { it.toArtist() },
            albums = albums.map { it.toAlbum() },
            tracks = tracks.map { it.toTrack() },
        )

    companion object {
        fun fromMediaSearchResults(results: MediaSearchResults): MediaSearchResultsDto =
            MediaSearchResultsDto(
                artists = results.artists.map { ArtistDto.fromArtist(it) },
                albums = results.albums.map { AlbumDto.fromAlbum(it) },
                tracks = results.tracks.map { TrackDto.fromTrack(it) },
            )
    }
}

@Serializable
data class ArtistDetailsDto(
    val artist: ArtistDto,
    val albums: List<AlbumDto>,
    val info: ArtistInfoDto? = null,
) {
    fun toArtistDetails(): ArtistDetails =
        ArtistDetails(
            artist = artist.toArtist(),
            albums = albums.map { it.toAlbum() },
            info = info?.toArtistInfo(),
        )

    companion object {
        fun fromArtistDetails(details: ArtistDetails): ArtistDetailsDto =
            ArtistDetailsDto(
                artist = ArtistDto.fromArtist(details.artist),
                albums = details.albums.map { AlbumDto.fromAlbum(it) },
                info = details.info?.let { ArtistInfoDto.fromArtistInfo(it) },
            )
    }
}

@Serializable
data class AlbumDetailsDto(
    val album: AlbumDto,
    val tracks: List<TrackDto>,
) {
    fun toAlbumDetails(): AlbumDetails =
        AlbumDetails(
            album = album.toAlbum(),
            tracks = tracks.map { it.toTrack() },
        )

    companion object {
        fun fromAlbumDetails(details: AlbumDetails): AlbumDetailsDto =
            AlbumDetailsDto(
                album = AlbumDto.fromAlbum(details.album),
                tracks = details.tracks.map { TrackDto.fromTrack(it) },
            )
    }
}

@Serializable
data class ArtistDto(
    val id: String,
    val name: String,
    val favoritedAtIso8601: String? = null,
) {
    fun toArtist(): Artist =
        Artist(
            id = ArtistId(id),
            name = name,
            favoritedAtIso8601 = favoritedAtIso8601,
        )

    companion object {
        fun fromArtist(artist: Artist): ArtistDto =
            ArtistDto(
                id = artist.id.value,
                name = artist.name,
                favoritedAtIso8601 = artist.favoritedAtIso8601,
            )
    }
}

@Serializable
data class ArtistInfoDto(
    val biography: String? = null,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
    val largeImageUrl: String? = null,
) {
    fun toArtistInfo(): ArtistInfo =
        ArtistInfo(
            biography = biography,
            smallImageUrl = smallImageUrl,
            mediumImageUrl = mediumImageUrl,
            largeImageUrl = largeImageUrl,
        )

    companion object {
        fun fromArtistInfo(info: ArtistInfo): ArtistInfoDto =
            ArtistInfoDto(
                biography = info.biography,
                smallImageUrl = info.smallImageUrl,
                mediumImageUrl = info.mediumImageUrl,
                largeImageUrl = info.largeImageUrl,
            )
    }
}

@Serializable
data class AlbumDto(
    val id: String,
    val title: String,
    val artistName: String,
    val coverArtId: String? = null,
    val recentlyAddedAtIso8601: String? = null,
    val releaseYear: Int? = null,
    val favoritedAtIso8601: String? = null,
) {
    fun toAlbum(): Album =
        Album(
            id = AlbumId(id),
            title = title,
            artistName = artistName,
            coverArtId = coverArtId,
            recentlyAddedAtIso8601 = recentlyAddedAtIso8601,
            releaseYear = releaseYear,
            favoritedAtIso8601 = favoritedAtIso8601,
        )

    companion object {
        fun fromAlbum(album: Album): AlbumDto =
            AlbumDto(
                id = album.id.value,
                title = album.title,
                artistName = album.artistName,
                coverArtId = album.coverArtId,
                recentlyAddedAtIso8601 = album.recentlyAddedAtIso8601,
                releaseYear = album.releaseYear,
                favoritedAtIso8601 = album.favoritedAtIso8601,
            )
    }
}

@Serializable
data class TrackDto(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumReleaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
    val audioInfo: AudioInfoDto? = null,
    val replayGain: ReplayGainDto? = null,
    val favoritedAtIso8601: String? = null,
    val userRating: Int? = null,
    val bpm: Int? = null,
    val moods: List<String> = emptyList(),
    val playCount: Int? = null,
    val lastPlayedAtIso8601: String? = null,
) {
    fun toTrack(): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistId = artistId?.let { ArtistId(it) },
            artistName = artistName,
            albumId = albumId?.let { AlbumId(it) },
            albumTitle = albumTitle,
            albumReleaseYear = albumReleaseYear,
            durationSeconds = durationSeconds,
            coverArtId = coverArtId,
            audioInfo = audioInfo?.toAudioInfo(),
            replayGain = replayGain?.toReplayGain(),
            favoritedAtIso8601 = favoritedAtIso8601,
            userRating = userRating,
            bpm = bpm,
            moods = moods,
            playCount = playCount,
            lastPlayedAtIso8601 = lastPlayedAtIso8601,
        )

    companion object {
        fun fromTrack(track: Track): TrackDto =
            TrackDto(
                id = track.id.value,
                title = track.title,
                artistId = track.artistId?.value,
                artistName = track.artistName,
                albumId = track.albumId?.value,
                albumTitle = track.albumTitle,
                albumReleaseYear = track.albumReleaseYear,
                durationSeconds = track.durationSeconds,
                coverArtId = track.coverArtId,
                audioInfo = track.audioInfo?.let { AudioInfoDto.fromAudioInfo(it) },
                replayGain = track.replayGain?.let { ReplayGainDto.fromReplayGain(it) },
                favoritedAtIso8601 = track.favoritedAtIso8601,
                userRating = track.userRating,
                bpm = track.bpm,
                moods = track.moods,
                playCount = track.playCount,
                lastPlayedAtIso8601 = track.lastPlayedAtIso8601,
            )
    }
}

@Serializable
data class AudioInfoDto(
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val contentType: String? = null,
    val bitDepth: Int? = null,
    val samplingRateHz: Int? = null,
) {
    fun toAudioInfo(): AudioInfo =
        AudioInfo(
            codec = codec,
            bitrateKbps = bitrateKbps,
            contentType = contentType,
            bitDepth = bitDepth,
            samplingRateHz = samplingRateHz,
        )

    companion object {
        fun fromAudioInfo(audioInfo: AudioInfo): AudioInfoDto =
            AudioInfoDto(
                codec = audioInfo.codec,
                bitrateKbps = audioInfo.bitrateKbps,
                contentType = audioInfo.contentType,
                bitDepth = audioInfo.bitDepth,
                samplingRateHz = audioInfo.samplingRateHz,
            )
    }
}

@Serializable
data class ReplayGainDto(
    val trackGainDb: Double? = null,
    val albumGainDb: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
) {
    fun toReplayGain(): ReplayGain =
        ReplayGain(
            trackGainDb = trackGainDb,
            albumGainDb = albumGainDb,
            trackPeak = trackPeak,
            albumPeak = albumPeak,
        )

    companion object {
        fun fromReplayGain(replayGain: ReplayGain): ReplayGainDto =
            ReplayGainDto(
                trackGainDb = replayGain.trackGainDb,
                albumGainDb = replayGain.albumGainDb,
                trackPeak = replayGain.trackPeak,
                albumPeak = replayGain.albumPeak,
            )
    }
}
