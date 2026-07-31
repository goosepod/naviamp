package app.naviamp.desktop

import app.cash.sqldelight.db.SqlDriver
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageAudioStore
import app.naviamp.storage.DefaultStorageAudioCacheBytes
import app.naviamp.storage.DefaultStorageAudioWaveformCacheBytes
import app.naviamp.storage.DefaultStorageHotImageCacheBytes
import app.naviamp.storage.StorageCoreRepositoryCatalog
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.StorageObjectByteStore
import app.naviamp.storage.initializeNaviampStorageDatabase
import java.nio.file.Files
import java.nio.file.Path

/** Desktop driver/path effects mounted on the same portable repository graph as Android and iOS. */
class DesktopStorageRepositories private constructor(
    private val driver: SqlDriver,
    internal val database: NaviampStorageDatabase,
    private val repositories: StorageCoreRepositoryCatalog,
    val objectBytes: StorageObjectByteStore,
    val audioCacheBytes: DesktopMutableAudioByteStore,
    val downloadBytes: DesktopMutableAudioByteStore,
    val audioStore: StorageAudioStore,
) : AutoCloseable {
    val mediaSources get() = repositories.mediaSources
    val providerResponses get() = repositories.providerResponses
    internal val providerResponseRows get() = repositories.providerResponseRows
    val audioWaveforms get() = repositories.audioWaveforms
    val lyricsSidecars get() = repositories.lyricsSidecars
    val lyricsOffsets get() = repositories.lyricsOffsets
    val sidecarStatuses get() = repositories.sidecarStatuses
    val libraryIndex get() = repositories.libraryIndex
    val keepDownloaded get() = repositories.keepDownloaded
    val pendingProviderActions get() = repositories.pendingProviderActions
    val playbackSessions get() = repositories.playbackSessions
    val playbackHistory get() = repositories.playbackHistory
    val radioDjPresets get() = repositories.radioDjPresets
    val maintenance: CacheMaintenanceRepository<StorageCacheStats> get() = repositories.maintenance

    fun updateAudioCacheLimit(maxBytes: Long) {
        audioStore.updateAudioCacheLimit(maxBytes)
        repositories.updateAudioCacheLimit(maxBytes)
    }

    override fun close() = driver.close()

    companion object {
        fun open(
            location: StorageDatabaseLocation,
            audioCacheDirectory: Path,
            downloadDirectory: Path,
            nowEpochMillis: () -> Long = DesktopSystemClock::nowEpochMillis,
            credentialProtector: StorageCredentialProtector =
                app.naviamp.desktop.security.DesktopCredentialProtector(),
            driverFactory: StorageDatabaseDriverFactory = DesktopStorageDatabaseDriverFactory,
            maxImageBytes: Long = MaximumPersistentArtworkCacheBytes,
            maxAudioBytes: Long = DefaultStorageAudioCacheBytes,
            maxAudioWaveformBytes: Long = DefaultStorageAudioWaveformCacheBytes,
            maxHotImageBytes: Long = DefaultStorageHotImageCacheBytes,
            legacyDatabaseFilesOnReset: List<Path> = emptyList(),
        ): DesktopStorageRepositories {
            val driver = driverFactory.create(location)
            return try {
                val database = initializeNaviampStorageDatabase(driver)
                val databasePath = Path.of(location.directoryPath).resolve(location.fileName)
                val hotImages = DesktopHotImageCache(maxHotImageBytes)
                val audioCacheBytes = DesktopMutableAudioByteStore(audioCacheDirectory)
                val downloadBytes = DesktopMutableAudioByteStore(downloadDirectory)
                val knownFiles = DesktopKnownFileDeleter()
                val deleteCached: (String) -> Boolean = { filePath ->
                    runCatching { knownFiles.deleteOwnedAudioFile(audioCacheDirectory, Path.of(filePath)) }.getOrDefault(false)
                }
                val deleteDownloaded: (String) -> Boolean = { filePath ->
                    runCatching { knownFiles.deleteOwnedAudioFile(downloadDirectory, Path.of(filePath)) }.getOrDefault(false)
                }
                val repositories = StorageCoreRepositoryCatalog(
                    database = database,
                    credentialProtector = credentialProtector,
                    nowEpochMillis = nowEpochMillis,
                    databaseLabel = databasePath.toAbsolutePath().toString(),
                    databaseBytes = { runCatching { Files.size(databasePath) }.getOrDefault(0L) },
                    deleteKnownAudioCacheFile = deleteCached,
                    deleteKnownDownloadFile = deleteDownloaded,
                    maxImageBytes = maxImageBytes,
                    maxAudioBytes = maxAudioBytes,
                    maxAudioWaveformBytes = maxAudioWaveformBytes,
                    maxHotImageBytes = maxHotImageBytes,
                    audioCacheDirectory = { audioCacheDirectory.toAbsolutePath().toString() },
                    downloadDirectory = { downloadDirectory.toAbsolutePath().toString() },
                    hotImageCount = hotImages::count,
                    hotImageBytes = hotImages::sizeBytes,
                    clearHotImages = hotImages::clear,
                    clearAdditionalData = { legacyDatabaseFilesOnReset.forEach(knownFiles::deleteFile) },
                )
                val httpClient = KtorSharedHttpClient()
                DesktopStorageRepositories(
                    driver = driver,
                    database = database,
                    repositories = repositories,
                    objectBytes = StorageObjectByteStore(
                        queries = database.naviampStorageQueries,
                        nowMillis = nowEpochMillis,
                        maxImageCacheBytes = maxImageBytes,
                        workContext = DesktopStorageWorkDispatcher,
                    ),
                    audioCacheBytes = audioCacheBytes,
                    downloadBytes = downloadBytes,
                    audioStore = StorageAudioStore(
                        queries = database.naviampStorageQueries,
                        audioCacheByteStoreService = AudioByteStoreService(audioCacheBytes, httpClient),
                        downloadAudioByteStoreService = AudioByteStoreService(downloadBytes, httpClient),
                        nowEpochMillis = nowEpochMillis,
                        cachedAudioFileExists = { Files.isRegularFile(Path.of(it)) },
                        downloadedAudioFileExists = { Files.isRegularFile(Path.of(it)) },
                        deleteKnownAudioCacheFile = deleteCached,
                        deleteKnownDownloadFile = deleteDownloaded,
                        workContext = DesktopStorageWorkDispatcher,
                        maxAudioCacheBytes = maxAudioBytes,
                        protectedTrackIds = repositories::protectedCachedAudioTrackIds,
                    ),
                )
            } catch (failure: Throwable) {
                driver.close()
                throw failure
            }
        }
    }
}
