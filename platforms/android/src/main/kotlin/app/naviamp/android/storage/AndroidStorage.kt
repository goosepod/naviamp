package app.naviamp.android

import android.content.Context
import app.naviamp.android.security.AndroidKeystoreCredentialProtector
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Lyrics
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioWaveformCacheRepository
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.KeepDownloadedCollectionKind
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.KeepDownloadedRepository
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
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheService
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.lyrics.LrclibLyricsProvider
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.source.MediaSourceIdentity
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.provider.navidrome.resolvedDisplayName
import app.naviamp.provider.navidrome.toNavidromeConnection
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageMediaSourceStore
import app.naviamp.storage.StorageMaintenanceStore
import app.naviamp.storage.StorageDatabaseLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class AndroidStorage(
    context: Context,
    private val onlineLyricsProvider: LrclibLyricsProvider = LrclibLyricsProvider(KtorSharedHttpClient()),
) : ImageCacheRepository,
    ProviderResponseCacheRepository,
    AudioCacheRepository<AndroidCachedAudioFile, AndroidCachedAudioMetadata>,
    AudioWaveformCacheRepository,
    AudioWaveformStorageRepository,
    LyricsSidecarRepository,
    LyricsOffsetRepository,
    DownloadRepository<AndroidDownloadedAudioFile, AndroidDownloadedTrack>,
    DownloadReplacementRepository<AndroidDownloadedAudioFile>,
    KeepDownloadedRepository,
    PlaybackHistoryRepository<AndroidPlaybackHistoryItem>,
    MediaSourceRepository,
    ProviderMediaSourceRepository,
    PlaybackSessionRepository,
    LocalLibraryIndexRepository,
    PendingProviderActionRepository,
    RadioDjPresetRepository,
    CacheMaintenanceRepository<StorageCacheStats>,
    SidecarStatusRepository,
    AutoCloseable {
    private val appContext = context.applicationContext
    private val driver = AndroidStorageDatabaseDriverFactory(appContext).create(
        StorageDatabaseLocation(
            directoryPath = requireNotNull(appContext.getDatabasePath(AndroidStorageDatabaseName).parent),
            fileName = AndroidStorageDatabaseName,
        ),
    )
    private val database = NaviampStorageDatabase(driver)
    private val queries = database.naviampStorageQueries
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val providerResponseCache = ProviderResponseCacheService(
        store = AndroidProviderResponseStore(queries),
        nowMillis = ::nowMillis,
    )
    private val sidecarStatus = SidecarStatusService(
        store = AndroidSidecarStatusStore(queries),
        nowMillis = ::nowMillis,
    )
    private val mediaSources = StorageMediaSourceStore(
        queries = queries,
        nowMillis = ::nowMillis,
        credentialProtector = AndroidKeystoreCredentialProtector(),
    )
    private val libraryIndex = AndroidLibraryIndexStore(
        queries = queries,
        mediaSources = mediaSources,
        nowMillis = ::nowMillis,
    )
    private val lyricsSidecar = LyricsSidecarCacheService(
        store = AndroidLyricsSidecarStore(queries),
        nowMillis = ::nowMillis,
        json = json,
    )
    private val lyricsOffsets = AndroidLyricsOffsetStore(queries, ::nowMillis)
    private val playbackStore = AndroidPlaybackStore(
        queries = queries,
        json = json,
        nowMillis = ::nowMillis,
    )
    private val pendingProviderActions = AndroidPendingProviderActionStore(
        queries = queries,
        nowMillis = ::nowMillis,
    )
    private val radioDjPresets = AndroidRadioDjPresetStore(
        queries = queries,
        nowMillis = ::nowMillis,
    )
    private val maintenance = StorageMaintenanceStore(queries)
    private val audioWaveforms = AndroidAudioWaveformStore(
        queries = queries,
        json = json,
        nowMillis = ::nowMillis,
        maxAudioWaveformCacheBytes = MaxAudioWaveformCacheBytes,
    )
    private val httpClient = KtorSharedHttpClient()
    private val imageByteStoreService = ObjectByteStoreService(
        store = AndroidObjectByteStore(
            queries = queries,
            nowMillis = ::nowMillis,
        ),
    )

    private val audioFiles = AndroidAudioFileServices(
        initialAudioCacheDirectory = File(appContext.cacheDir, "audio-cache"),
        initialDownloadDirectory = File(appContext.filesDir, "downloads"),
        httpClient = httpClient,
    )
    val audioCacheDirectory: File
        get() = audioFiles.audioCacheDirectory
    val downloadDirectory: File
        get() = audioFiles.downloadDirectory
    private var maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L
    private val audioStore = AndroidAudioStore(
        queries = queries,
        audioCacheByteStoreService = audioFiles.audioCacheByteStoreService,
        downloadAudioByteStoreService = audioFiles.downloadAudioByteStoreService,
        nowMillis = ::nowMillis,
        maxAudioCacheBytes = maxAudioCacheBytes,
        protectedTrackIds = ::protectedCachedAudioTrackIds,
    )
    override fun close() {
        driver.close()
    }

    fun updateDownloadDirectory(directory: File) {
        audioFiles.updateDownloadDirectory(directory)
    }

    fun updateAudioCacheDirectory(directory: File) {
        audioFiles.updateAudioCacheDirectory(directory)
    }

    override fun latestMediaSource(): SavedMediaSource? =
        mediaSources.latestMediaSource()

    fun latestNavidromeSource(): SavedMediaSource? =
        latestMediaSource()

    override fun mediaSources(): List<SavedMediaSource> =
        mediaSources.mediaSources()

    fun latestNavidromeConnection(): NavidromeConnection? =
        latestNavidromeSource()?.toNavidromeConnection()

    override fun mediaSource(sourceId: String): SavedMediaSource? =
        mediaSources.mediaSource(sourceId)

    override fun deleteMediaSource(sourceId: String) {
        mediaSources.deleteMediaSource(sourceId)
    }

    override fun upsertProviderMediaSource(
        connection: ProviderMediaSourceConnection,
        cacheNamespace: String,
        providerId: String,
    ): MediaSourceIdentity =
        mediaSources.upsertProviderMediaSource(connection, cacheNamespace, providerId)

    fun upsertNavidromeSource(connection: NavidromeConnection, cacheNamespace: String, providerId: String): MediaSourceIdentity =
        upsertProviderMediaSource(
            connection = ProviderMediaSourceConnection(
                displayName = connection.resolvedDisplayName(),
                baseUrl = connection.baseUrl,
                username = connection.username,
                token = connection.token,
                salt = connection.salt,
                nativeToken = connection.nativeToken,
                tlsSettings = connection.tlsSettings,
                secondaryUrls = connection.secondaryUrls,
                customHeaders = connection.customHeaders,
                selectedMusicFolderIds = connection.selectedMusicFolderIds,
            ),
            cacheNamespace = cacheNamespace,
            providerId = providerId,
        )

    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? =
        playbackStore.loadPlaybackSession(sourceId)

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        playbackStore.savePlaybackSession(session, sourceId)
    }

    fun savePlaybackSession(sourceId: String, session: PlaybackSessionSettings?) {
        savePlaybackSession(session = session, sourceId = sourceId)
    }

    private fun protectedCachedAudioTrackIds(): Set<String> {
        val sourceId = latestMediaSource()?.id ?: return emptySet()
        val session = loadPlaybackSession(sourceId) ?: return emptySet()
        if (session.currentIndex !in session.tracks.indices) return emptySet()
        return session.tracks
            .drop(session.currentIndex)
            .take(ProtectedAudioCacheQueueWindow)
            .map { track -> track.id }
            .toSet()
    }

    override suspend fun imageBytes(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            imageByteStoreService.bytes(url) {
                httpClient.getBytes(url) ?: throw IllegalStateException("Could not download image bytes.")
            }
        }

    override suspend fun cachedImageBytes(url: String): ByteArray? =
        withContext(Dispatchers.IO) {
            imageByteStoreService.cachedBytes(url)
        }

    override suspend fun imageBytes(
        url: String,
        fetch: suspend () -> ByteArray,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            imageByteStoreService.bytes(url, fetch)
        }

    override suspend fun <T> cachedProviderResponse(
        provider: MediaProvider,
        resourceType: String,
        resourceId: String,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): T = fetch()

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

    override fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioCacheBytes = maxBytes.coerceAtLeast(0)
        audioStore.updateAudioCacheLimit(maxBytes)
    }

    override fun cachedAudioMetadata(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AndroidCachedAudioMetadata? =
        audioStore.cachedAudioMetadata(sourceId, trackId, quality)

    override suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AndroidCachedAudioFile? =
        audioStore.cachedAudioFile(sourceId, trackId, quality)

    override suspend fun cachedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): AndroidCachedAudioFile? =
        audioStore.cachedAudioFile(sourceId, trackId)

    override suspend fun cacheAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
    ): AndroidCachedAudioFile =
        audioStore.cacheAudioTrack(sourceId, provider, track, quality)

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
    ): AndroidDownloadedAudioFile? =
        audioStore.downloadedAudioFile(sourceId, trackId, quality)

    override suspend fun downloadedAudioFile(
        sourceId: String,
        trackId: TrackId,
    ): AndroidDownloadedAudioFile? =
        audioStore.downloadedAudioFile(sourceId, trackId)

    override suspend fun downloadAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): AndroidDownloadedAudioFile =
        audioStore.downloadAudioTrack(sourceId, provider, track, quality, maxDownloadBytes)

    override suspend fun replaceDownloadedAudioTrack(
        sourceId: String,
        provider: MediaProvider,
        track: Track,
        quality: StreamQuality,
        maxDownloadBytes: Long,
    ): AndroidDownloadedAudioFile =
        audioStore.replaceDownloadedAudioTrack(sourceId, provider, track, quality, maxDownloadBytes)

    override fun downloadedTracks(sourceId: String): List<AndroidDownloadedTrack> =
        audioStore.downloadedTracks(sourceId)

    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId, quality: StreamQuality) {
        audioStore.removeDownloadedAudio(sourceId, trackId, quality)
    }

    override fun removeDownloadedAudio(sourceId: String, trackId: TrackId) {
        audioStore.removeDownloadedAudio(sourceId, trackId)
    }

    fun removeDownloadedAudioForTrack(sourceId: String, trackId: TrackId) {
        removeDownloadedAudio(sourceId, trackId)
    }

    override fun keepDownloadedPolicies(sourceId: String): List<KeepDownloadedCollectionPolicy> =
        queries.selectKeepDownloadedPolicies(sourceId).executeAsList().map { row ->
            KeepDownloadedCollectionPolicy(
                sourceId = row.source_id,
                kind = KeepDownloadedCollectionKind.valueOf(row.collection_kind),
                collectionId = row.collection_id,
                name = row.name,
                removeUnneededFiles = row.remove_unneeded_files != 0L,
            )
        }

    override fun keepDownloadedPolicy(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) =
        queries.selectKeepDownloadedPolicy(sourceId, kind.name, collectionId).executeAsOneOrNull()?.let { row ->
            KeepDownloadedCollectionPolicy(
                sourceId = row.source_id,
                kind = KeepDownloadedCollectionKind.valueOf(row.collection_kind),
                collectionId = row.collection_id,
                name = row.name,
                removeUnneededFiles = row.remove_unneeded_files != 0L,
            )
        }

    override fun upsertKeepDownloadedPolicy(policy: KeepDownloadedCollectionPolicy) {
        queries.upsertKeepDownloadedPolicy(
            policy.sourceId,
            policy.kind.name,
            policy.collectionId,
            policy.name,
            if (policy.removeUnneededFiles) 1L else 0L,
            nowMillis(),
        )
    }

    override fun deleteKeepDownloadedPolicy(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) {
        queries.deleteKeepDownloadedPolicy(sourceId, kind.name, collectionId)
    }

    override fun keepDownloadedTrackIds(sourceId: String, kind: KeepDownloadedCollectionKind, collectionId: String) =
        queries.selectKeepDownloadedTrackIds(sourceId, kind.name, collectionId).executeAsList().toSet()

    override fun replaceKeepDownloadedTrackIds(policy: KeepDownloadedCollectionPolicy, trackIds: Set<String>) {
        queries.transaction {
            upsertKeepDownloadedPolicy(policy)
            queries.deleteKeepDownloadedCollectionTracks(policy.sourceId, policy.kind.name, policy.collectionId)
            trackIds.forEach { queries.insertKeepDownloadedCollectionTrack(policy.sourceId, policy.kind.name, policy.collectionId, it) }
        }
    }

    override fun managedKeepDownloadedTrackIds(sourceId: String) =
        queries.selectManagedKeepDownloadedTrackIds(sourceId).executeAsList().toSet()

    override fun markManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) {
        queries.transaction { trackIds.forEach { queries.insertManagedKeepDownloadedTrack(sourceId, it) } }
    }

    override fun unmarkManagedKeepDownloadedTracks(sourceId: String, trackIds: Set<String>) {
        queries.transaction { trackIds.forEach { queries.deleteManagedKeepDownloadedTrack(sourceId, it) } }
    }

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? =
        audioWaveforms.cachedAudioWaveform(sourceId, trackId, quality, bucketCount)

    fun upsertAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        audioFile: File,
        waveform: AudioWaveform,
    ) {
        audioWaveforms.upsertAudioWaveform(
            sourceId = sourceId,
            trackId = trackId,
            quality = quality,
            audioFilePath = audioFile.absolutePath,
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
        lyricsSidecar.lrclibLyrics(sourceId, track, onlineLyricsProvider)

    override fun lyricsOffsetMillis(sourceId: String, trackId: TrackId): Int? =
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

    fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
    ) {
        recordSidecarStatus(sourceId, trackId, quality, sidecarType, success, null)
    }

    override fun playbackHistory(sourceId: String, limit: Int): List<AndroidPlaybackHistoryItem> =
        playbackStore.playbackHistory(sourceId, limit)

    override fun recordPlaybackHistory(sourceId: String, track: Track, playedAtEpochMillis: Long) {
        playbackStore.recordPlaybackHistory(sourceId, track, playedAtEpochMillis)
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

    fun libraryTrack(sourceId: String, trackId: TrackId): Track? =
        libraryIndex.libraryTrack(sourceId, trackId)

    override fun randomLibraryTrackForArtist(sourceId: String, artistId: ArtistId): Track? =
        libraryIndex.randomLibraryTrackForArtist(sourceId, artistId)

    override fun libraryTracksForArtist(sourceId: String, artistId: ArtistId, limit: Long): List<Track> =
        libraryIndex.libraryTracksForArtist(sourceId, artistId, limit)

    override fun libraryTracksForArtistName(sourceId: String, artistName: String, limit: Long): List<Track> =
        libraryIndex.libraryTracksForArtistName(sourceId, artistName, limit)

    override fun libraryTracksForAlbumTitle(
        sourceId: String,
        albumTitle: String,
        artistName: String?,
        limit: Long,
    ): List<Track> =
        libraryIndex.libraryTracksForAlbumTitle(sourceId, albumTitle, artistName, limit)

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

    override fun clearProviderData() {
        maintenance.clearProviderData()
    }

    override fun clearCacheData() {
        maintenance.clearCacheData(audioFiles::deleteKnownAudioCacheFile)
    }

    override fun clearDownloadData() {
        maintenance.clearDownloadData(audioFiles::deleteKnownDownloadFile)
    }

    override fun clearLibraryData(sourceId: String?) {
        libraryIndex.clearLibraryData(sourceId)
    }

    override fun clearAll() {
        clearCacheData()
        clearDownloadData()
        clearLibraryData(null)
        playbackStore.clearPlaybackHistory()
        maintenance.clearAllRows()
    }

    override fun pruneUnusedSourceScopes(
        activeSourceIds: Set<String>,
        lastConnectedBeforeEpochMillis: Long,
        limit: Long,
    ): Int =
        mediaSources.pruneUnusedSourceScopes(
            activeSourceIds = activeSourceIds,
            lastConnectedBeforeEpochMillis = lastConnectedBeforeEpochMillis,
            limit = limit,
            deleteKnownAudioCacheFile = audioFiles::deleteKnownAudioCacheFile,
            deleteKnownDownloadFile = audioFiles::deleteKnownDownloadFile,
        )

    override fun stats(): StorageCacheStats =
        maintenance.stats(
            databaseLabel = AndroidStorageDatabaseName,
            audioCacheDirectory = audioCacheDirectory.absolutePath,
            downloadDirectory = downloadDirectory.absolutePath,
        )

}

data class AndroidCachedAudioFile(
    val file: File,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
)

data class AndroidCachedAudioMetadata(
    val file: File,
    val exists: Boolean,
    val sizeBytes: Long,
    val contentType: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedEpochMillis: Long,
)

data class AndroidDownloadedAudioFile(
    val file: File,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
)

data class AndroidDownloadedTrack(
    val track: Track,
    val file: File,
    val sizeBytes: Long,
    val contentType: String?,
    val qualityKey: String,
    val downloadedAtEpochMillis: Long,
)

private fun nowMillis(): Long = System.currentTimeMillis()

private const val MaxAudioWaveformCacheBytes = 32L * 1024L * 1024L
private const val ProtectedAudioCacheQueueWindow = 11
