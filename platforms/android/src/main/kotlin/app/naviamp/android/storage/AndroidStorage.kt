package app.naviamp.android

import android.content.Context
import app.naviamp.android.security.AndroidKeystoreCredentialProtector
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.AudioCacheRepository
import app.naviamp.domain.cache.AudioWaveformCacheRepository
import app.naviamp.domain.cache.AudioWaveformStorageRepository
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.CachedLyricsSidecarRepository
import app.naviamp.domain.cache.DownloadReplacementRepository
import app.naviamp.domain.cache.DownloadRepository
import app.naviamp.domain.cache.ImageCacheRepository
import app.naviamp.domain.cache.KeepDownloadedRepository
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.LyricsOffsetRepository
import app.naviamp.domain.cache.LyricsSidecarCacheService
import app.naviamp.domain.cache.LyricsSidecarRepository
import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.domain.cache.MediaSourceRepository
import app.naviamp.domain.cache.ObjectByteStoreService
import app.naviamp.domain.cache.PlaybackHistoryRepository
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.cache.ProviderIdentityMigrationRepository
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.SidecarStatusRepository
import app.naviamp.domain.cache.SidecarStatusService
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.network.SharedHttpClient
import app.naviamp.domain.lyrics.naviampOnlineLyricsProviders
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.waveform.AudioWaveform
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.DefaultStorageAudioCacheBytes
import app.naviamp.storage.StorageAudioStore
import app.naviamp.storage.StorageCachedAudioFile
import app.naviamp.storage.StorageCachedAudioMetadata
import app.naviamp.storage.StorageCoreRepositoryCatalog
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.StorageDownloadedAudioFile
import app.naviamp.storage.StorageDownloadedTrack
import app.naviamp.storage.StorageObjectByteStore
import app.naviamp.storage.StoragePlaybackHistoryItem
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android driver/filesystem effect graph mounted on the portable storage owners. */
class AndroidStorage private constructor(
    private val graph: AndroidStorageGraph,
) : ImageCacheRepository,
    ProviderResponseCacheRepository by graph.repositories.providerResponses,
    AudioCacheRepository<StorageCachedAudioFile, StorageCachedAudioMetadata> by graph.audioStore,
    AudioWaveformCacheRepository by graph.repositories.audioWaveforms,
    AudioWaveformStorageRepository by graph.repositories.audioWaveforms,
    LyricsSidecarRepository by graph.lyricsSidecars,
    LyricsOffsetRepository by graph.repositories.lyricsOffsets,
    DownloadRepository<StorageDownloadedAudioFile, StorageDownloadedTrack> by graph.audioStore,
    DownloadReplacementRepository<StorageDownloadedAudioFile> by graph.audioStore,
    KeepDownloadedRepository by graph.repositories.keepDownloaded,
    PlaybackHistoryRepository<StoragePlaybackHistoryItem> by graph.repositories.playbackHistory,
    MediaSourceRepository by graph.repositories.mediaSources,
    ProviderMediaSourceRepository by graph.repositories.mediaSources,
    ProviderIdentityMigrationRepository by graph.repositories.mediaSources,
    PlaybackSessionRepository by graph.repositories.playbackSessions,
    LocalLibraryIndexRepository by graph.repositories.libraryIndex,
    PendingProviderActionRepository by graph.repositories.pendingProviderActions,
    RadioDjPresetRepository by graph.repositories.radioDjPresets,
    CacheMaintenanceRepository<StorageCacheStats> by graph.repositories.maintenance,
    SidecarStatusRepository by graph.sidecarStatuses,
    AutoCloseable {

    constructor(
        context: Context,
        lyricsHttpClient: SharedHttpClient = KtorSharedHttpClient(),
    ) : this(AndroidStorageGraph(context.applicationContext, lyricsHttpClient))

    val audioCacheDirectory: File get() = graph.audioFiles.audioCacheDirectory
    val downloadDirectory: File get() = graph.audioFiles.downloadDirectory

    override fun close() = graph.close()

    fun updateDownloadDirectory(directory: File) = graph.audioFiles.updateDownloadDirectory(directory)

    fun updateAudioCacheDirectory(directory: File) = graph.audioFiles.updateAudioCacheDirectory(directory)

    override fun updateAudioCacheLimit(maxBytes: Long) {
        graph.audioStore.updateAudioCacheLimit(maxBytes)
        graph.repositories.updateAudioCacheLimit(maxBytes)
    }

    fun savePlaybackSession(sourceId: String, session: PlaybackSessionSettings?) {
        savePlaybackSession(session = session, sourceId = sourceId)
    }

    fun removeDownloadedAudioForTrack(sourceId: String, trackId: TrackId) {
        removeDownloadedAudio(sourceId, trackId)
    }

    override suspend fun cachedAudioWaveform(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        bucketCount: Int,
    ): AudioWaveform? = graph.repositories.audioWaveforms.cachedAudioWaveform(sourceId, trackId, quality, bucketCount)

    override fun mediaSource(sourceId: String): SavedMediaSource? = graph.repositories.mediaSources.mediaSource(sourceId)

    fun recordSidecarStatus(
        sourceId: String,
        trackId: TrackId,
        quality: StreamQuality,
        sidecarType: String,
        success: Boolean,
    ) {
        graph.sidecarStatuses.recordSidecarStatus(sourceId, trackId, quality, sidecarType, success, null)
    }

    override suspend fun imageBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        graph.imageBytes.bytes(url) {
            graph.httpClient.getBytes(url) ?: throw IllegalStateException("Could not download image bytes.")
        }
    }

    override suspend fun cachedImageBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        graph.imageBytes.cachedBytes(url)
    }

    override suspend fun imageBytes(url: String, fetch: suspend () -> ByteArray): ByteArray =
        withContext(Dispatchers.IO) { graph.imageBytes.bytes(url, fetch) }
}

private class AndroidStorageGraph(
    private val context: Context,
    lyricsHttpClient: SharedHttpClient,
) : AutoCloseable {
    private val driver = AndroidStorageDatabaseDriverFactory(context).create(
        StorageDatabaseLocation(
            directoryPath = requireNotNull(context.getDatabasePath(AndroidStorageDatabaseName).parent),
            fileName = AndroidStorageDatabaseName,
        ),
    )
    private val database = NaviampStorageDatabase(driver)
    val httpClient = KtorSharedHttpClient()
    val audioFiles = AndroidAudioFileServices(
        initialAudioCacheDirectory = File(context.cacheDir, "audio-cache"),
        initialDownloadDirectory = File(context.filesDir, "downloads"),
        httpClient = httpClient,
    )
    val repositories = StorageCoreRepositoryCatalog(
        database = database,
        credentialProtector = AndroidKeystoreCredentialProtector(),
        nowEpochMillis = ::nowMillis,
        databaseLabel = AndroidStorageDatabaseName,
        databaseBytes = { context.getDatabasePath(AndroidStorageDatabaseName).length() },
        deleteKnownAudioCacheFile = audioFiles::deleteKnownAudioCacheFile,
        deleteKnownDownloadFile = audioFiles::deleteKnownDownloadFile,
        audioCacheDirectory = { audioFiles.audioCacheDirectory.absolutePath },
        downloadDirectory = { audioFiles.downloadDirectory.absolutePath },
    )
    val audioStore = StorageAudioStore(
        queries = database.naviampStorageQueries,
        audioCacheByteStoreService = audioFiles.audioCacheByteStoreService,
        downloadAudioByteStoreService = audioFiles.downloadAudioByteStoreService,
        nowEpochMillis = ::nowMillis,
        cachedAudioFileExists = { File(it).isFile },
        downloadedAudioFileExists = { File(it).isFile },
        deleteKnownAudioCacheFile = audioFiles::deleteKnownAudioCacheFile,
        deleteKnownDownloadFile = audioFiles::deleteKnownDownloadFile,
        workContext = Dispatchers.IO,
        maxAudioCacheBytes = DefaultStorageAudioCacheBytes,
        protectedTrackIds = repositories::protectedCachedAudioTrackIds,
    )
    val imageBytes = ObjectByteStoreService(
        StorageObjectByteStore(
            queries = database.naviampStorageQueries,
            nowMillis = ::nowMillis,
            maxImageCacheBytes = MaximumPersistentArtworkCacheBytes,
            workContext = Dispatchers.IO,
        ),
    )
    val lyricsSidecars = CachedLyricsSidecarRepository(
        cache = LyricsSidecarCacheService(repositories.lyricsSidecars, ::nowMillis),
        onlineProviders = naviampOnlineLyricsProviders(lyricsHttpClient, ::nowMillis),
    )
    val sidecarStatuses = SidecarStatusService(repositories.sidecarStatuses, ::nowMillis)

    override fun close() = driver.close()
}

private fun nowMillis(): Long = System.currentTimeMillis()
