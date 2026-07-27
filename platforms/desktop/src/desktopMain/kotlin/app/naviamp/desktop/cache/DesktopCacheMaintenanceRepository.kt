package app.naviamp.desktop

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.domain.cache.StorageCacheStats
import app.naviamp.storage.StorageMaintenanceStore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Desktop SQL/filesystem effects for Core's cache-maintenance policy.
 *
 * Core decides when maintenance runs and how outcomes are presented. This adapter performs only
 * the requested SQLDelight and native filesystem mutations.
 */
class DesktopCacheMaintenanceRepository(
    private val storage: DesktopMediaSourceStorage,
    private val databasePath: Path,
    private val audioCacheDirectory: () -> Path,
    private val downloadDirectory: () -> Path,
    private val hotImages: DesktopHotImageCache = DesktopHotImageCache(DefaultHotImageCacheBytes),
    private val maxImageBytes: Long = MaximumPersistentArtworkCacheBytes,
    private var maxAudioBytes: Long = DefaultAudioCacheBytes,
    private val maxAudioWaveformBytes: Long = DefaultAudioWaveformCacheBytes,
    private val maxHotImageBytes: Long = DefaultHotImageCacheBytes,
    private val legacyDatabaseFilesOnReset: List<Path> = emptyList(),
    private val knownFileDeleter: DesktopKnownFileDeleter = DesktopKnownFileDeleter(),
) : CacheMaintenanceRepository<StorageCacheStats> {
    private val queries = storage.database.naviampStorageQueries
    private val rows = StorageMaintenanceStore(queries)

    override fun clearProviderData() {
        rows.clearProviderData()
    }

    fun updateAudioCacheLimit(maxBytes: Long) {
        maxAudioBytes = maxBytes.coerceAtLeast(0)
    }

    override fun clearCacheData() {
        hotImages.clear()
        rows.clearCacheData { filePath -> knownFileDeleter.deleteFile(Path.of(filePath)) }
    }

    override fun clearDownloadData() {
        rows.clearDownloadData { filePath -> knownFileDeleter.deleteFile(Path.of(filePath)) }
    }

    override fun clearAll() {
        clearCacheData()
        clearDownloadData()
        clearLibraryRows()
        rows.clearAllRows()
        legacyDatabaseFilesOnReset.forEach(knownFileDeleter::deleteFile)
    }

    override fun pruneUnusedSourceScopes(
        activeSourceIds: Set<String>,
        lastConnectedBeforeEpochMillis: Long,
        limit: Long,
    ): Int = storage.pruneUnusedSourceScopes(
        activeSourceIds = activeSourceIds,
        lastConnectedBeforeEpochMillis = lastConnectedBeforeEpochMillis,
        limit = limit,
        deleteKnownAudioCacheFile = { filePath -> knownFileDeleter.deleteFile(Path.of(filePath)) },
        deleteKnownDownloadFile = { filePath -> knownFileDeleter.deleteFile(Path.of(filePath)) },
    )

    override fun stats(): StorageCacheStats = rows.stats(
        databaseLabel = databasePath.toAbsolutePath().toString(),
        databaseBytes = databasePath.sizeOrZero(),
        hotImageCount = hotImages.count(),
        hotImageBytes = hotImages.sizeBytes(),
        maxImageBytes = maxImageBytes,
        maxAudioBytes = maxAudioBytes,
        maxAudioWaveformBytes = maxAudioWaveformBytes,
        maxHotImageBytes = maxHotImageBytes,
    ).copy(
        audioCacheDirectory = audioCacheDirectory().toAbsolutePath().toString(),
        downloadDirectory = downloadDirectory().toAbsolutePath().toString(),
    )

    private fun clearLibraryRows() {
        queries.transaction {
            queries.clearArtistPopularTracks()
            queries.clearLibraryTracks()
            queries.clearLibraryAlbums()
            queries.clearLibraryArtists()
        }
    }
}

private fun Path.sizeOrZero(): Long = if (exists()) Files.size(this) else 0L

private const val DefaultAudioCacheBytes = 2L * 1024L * 1024L * 1024L
private const val DefaultAudioWaveformCacheBytes = 32L * 1024L * 1024L
private const val DefaultHotImageCacheBytes = 32L * 1024L * 1024L
