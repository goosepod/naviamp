package app.naviamp.storage

import app.naviamp.domain.cache.CacheMaintenanceRepository
import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.domain.cache.ProviderResponseCacheService
import app.naviamp.domain.cache.StorageCacheStats
import kotlinx.serialization.json.Json

/**
 * Portable SQLDelight repository graph shared by every thin host.
 *
 * Hosts select and own the driver, credential protection, native file cleanup, and database-size
 * lookup. Repository assembly and SQL maintenance behavior remain identical on every platform.
 */
class StorageCoreRepositoryCatalog(
    database: NaviampStorageDatabase,
    credentialProtector: StorageCredentialProtector,
    nowEpochMillis: () -> Long,
    databaseLabel: String,
    databaseBytes: () -> Long = { 0L },
    deleteKnownAudioCacheFile: (String) -> Boolean,
    deleteKnownDownloadFile: (String) -> Boolean,
    maxImageBytes: Long = MaximumPersistentArtworkCacheBytes,
    maxAudioBytes: Long = DefaultStorageAudioCacheBytes,
    maxAudioWaveformBytes: Long = DefaultStorageAudioWaveformCacheBytes,
    maxHotImageBytes: Long = DefaultStorageHotImageCacheBytes,
    audioCacheDirectory: () -> String = { "" },
    downloadDirectory: () -> String = { "" },
    hotImageCount: () -> Int = { 0 },
    hotImageBytes: () -> Long = { 0L },
    clearHotImages: () -> Unit = {},
    clearAdditionalData: () -> Unit = {},
) {
    private val queries = database.naviampStorageQueries
    private val rows = StorageMaintenanceStore(queries)
    private var currentMaxAudioBytes = maxAudioBytes.coerceAtLeast(0L)

    fun updateAudioCacheLimit(maxBytes: Long) {
        currentMaxAudioBytes = maxBytes.coerceAtLeast(0L)
    }

    /** Protects the current track and bounded upcoming queue from cache eviction on every host. */
    fun protectedCachedAudioTrackIds(): Set<String> {
        val sourceId = mediaSources.latestMediaSource()?.id ?: return emptySet()
        val session = playbackSessions.loadPlaybackSession(sourceId) ?: return emptySet()
        if (session.currentIndex !in session.tracks.indices) return emptySet()
        return session.tracks
            .drop(session.currentIndex)
            .take(ProtectedAudioCacheQueueWindow)
            .mapTo(mutableSetOf()) { track -> track.id }
    }

    val mediaSources = StorageMediaSourceStore(queries, nowEpochMillis, credentialProtector)
    val libraryIndex = StorageLibraryIndexStore(queries, mediaSources, nowEpochMillis)
    val providerResponseRows = StorageProviderResponseStore(queries)
    val providerResponses = ProviderResponseCacheService(
        providerResponseRows,
        nowEpochMillis,
    )
    val keepDownloaded = StorageKeepDownloadedStore(queries, nowEpochMillis)
    val radioDjPresets = StorageRadioDjPresetStore(queries, nowEpochMillis)
    val playbackSessions = StoragePlaybackSessionStore(queries, nowEpochMillis)
    val playbackHistory = StoragePlaybackHistoryStore(queries)
    val pendingProviderActions = StoragePendingProviderActionStore(queries, nowEpochMillis)
    val audioWaveforms = StorageAudioWaveformStore(
        queries = queries,
        json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
        nowMillis = nowEpochMillis,
        maxAudioWaveformCacheBytes = maxAudioWaveformBytes,
    )
    val lyricsOffsets = StorageLyricsOffsetStore(queries, nowEpochMillis)
    val lyricsSidecars = StorageLyricsSidecarStore(queries)
    val sidecarStatuses = StorageSidecarStatusStore(queries)

    val maintenance: CacheMaintenanceRepository<StorageCacheStats> =
        object : CacheMaintenanceRepository<StorageCacheStats> {
            override fun clearProviderData() = rows.clearProviderData()

            override fun clearCacheData() {
                clearHotImages()
                rows.clearCacheData(deleteKnownAudioCacheFile)
            }

            override fun clearDownloadData() = rows.clearDownloadData(deleteKnownDownloadFile)

            override fun clearAll() {
                clearCacheData()
                clearDownloadData()
                libraryIndex.clearLibraryData(null)
                playbackHistory.clear()
                rows.clearAllRows()
                clearAdditionalData()
            }

            override fun pruneUnusedSourceScopes(
                activeSourceIds: Set<String>,
                lastConnectedBeforeEpochMillis: Long,
                limit: Long,
            ): Int = mediaSources.pruneUnusedSourceScopes(
                activeSourceIds = activeSourceIds,
                lastConnectedBeforeEpochMillis = lastConnectedBeforeEpochMillis,
                limit = limit,
                deleteKnownAudioCacheFile = deleteKnownAudioCacheFile,
                deleteKnownDownloadFile = deleteKnownDownloadFile,
            )

            override fun stats(): StorageCacheStats = rows.stats(
                databaseLabel = databaseLabel,
                databaseBytes = databaseBytes(),
                hotImageCount = hotImageCount(),
                hotImageBytes = hotImageBytes(),
                maxImageBytes = maxImageBytes,
                maxAudioBytes = currentMaxAudioBytes,
                maxAudioWaveformBytes = maxAudioWaveformBytes,
                maxHotImageBytes = maxHotImageBytes,
                audioCacheDirectory = audioCacheDirectory(),
                downloadDirectory = downloadDirectory(),
            )
        }
}

const val DefaultStorageAudioCacheBytes = 2L * 1024L * 1024L * 1024L
const val DefaultStorageAudioWaveformCacheBytes = 32L * 1024L * 1024L
const val DefaultStorageHotImageCacheBytes = 32L * 1024L * 1024L
private const val ProtectedAudioCacheQueueWindow = 11
