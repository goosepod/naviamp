package app.naviamp.desktop

import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.domain.cache.AudioByteStoreService
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageAudioWaveformStore
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.StorageLibraryIndexStore
import app.naviamp.storage.StorageLyricsOffsetStore
import app.naviamp.storage.StorageKeepDownloadedStore
import app.naviamp.storage.StorageLyricsSidecarStore
import app.naviamp.storage.StorageObjectByteStore
import app.naviamp.storage.StoragePendingProviderActionStore
import app.naviamp.storage.StoragePlaybackSessionStore
import app.naviamp.storage.StorageProviderResponseStore
import app.naviamp.storage.StorageRadioDjPresetStore
import app.naviamp.storage.StorageSidecarStatusStore
import kotlinx.serialization.json.Json
import java.nio.file.Path

/**
 * One Desktop-owned database/filesystem effect graph for the complete Core host.
 *
 * This class deliberately exposes focused shared repository contracts and native byte stores. It
 * contains no screen state, commands, status wording, or product workflow.
 */
class DesktopStorageRepositories private constructor(
    val mediaSources: DesktopMediaSourceStorage,
    val providerResponses: StorageProviderResponseStore,
    val objectBytes: StorageObjectByteStore,
    val audioCacheBytes: DesktopMutableAudioByteStore,
    val downloadBytes: DesktopMutableAudioByteStore,
    val audioStore: DesktopAudioStore,
    val audioWaveforms: StorageAudioWaveformStore,
    val lyricsSidecars: StorageLyricsSidecarStore,
    val lyricsOffsets: StorageLyricsOffsetStore,
    val sidecarStatuses: StorageSidecarStatusStore,
    val libraryIndex: StorageLibraryIndexStore,
    val keepDownloaded: StorageKeepDownloadedStore,
    val pendingProviderActions: StoragePendingProviderActionStore,
    val playbackSessions: StoragePlaybackSessionStore,
    val radioDjPresets: StorageRadioDjPresetStore,
    val maintenance: DesktopCacheMaintenanceRepository,
) : AutoCloseable {
    override fun close() {
        mediaSources.close()
    }

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
            maxAudioBytes: Long = DefaultDesktopAudioCacheBytes,
            maxAudioWaveformBytes: Long = DefaultDesktopAudioWaveformCacheBytes,
            maxHotImageBytes: Long = DefaultDesktopHotImageCacheBytes,
            legacyDatabaseFilesOnReset: List<Path> = emptyList(),
            json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
        ): DesktopStorageRepositories {
            val mediaSources = DesktopMediaSourceStorage.open(
                location = location,
                nowEpochMillis = nowEpochMillis,
                credentialProtector = credentialProtector,
                driverFactory = driverFactory,
            )
            return try {
                val queries = mediaSources.database.naviampStorageQueries
                val hotImages = DesktopHotImageCache(maxHotImageBytes)
                val audioCacheBytes = DesktopMutableAudioByteStore(audioCacheDirectory)
                val downloadBytes = DesktopMutableAudioByteStore(downloadDirectory)
                val httpClient = KtorSharedHttpClient()
                DesktopStorageRepositories(
                    mediaSources = mediaSources,
                    providerResponses = StorageProviderResponseStore(queries),
                    objectBytes = StorageObjectByteStore(
                        queries,
                        nowEpochMillis,
                        maxImageBytes,
                        DesktopStorageWorkDispatcher,
                    ),
                    audioCacheBytes = audioCacheBytes,
                    downloadBytes = downloadBytes,
                    audioStore = DesktopAudioStore(
                        queries = queries,
                        audioCacheByteStoreService = AudioByteStoreService(audioCacheBytes, httpClient),
                        downloadAudioByteStoreService = AudioByteStoreService(downloadBytes, httpClient),
                        nowMillis = nowEpochMillis,
                        maxAudioCacheBytes = maxAudioBytes,
                    ),
                    audioWaveforms = StorageAudioWaveformStore(
                        queries,
                        json,
                        nowEpochMillis,
                        maxAudioWaveformBytes,
                        DesktopStorageWorkDispatcher,
                    ),
                    lyricsSidecars = StorageLyricsSidecarStore(queries),
                    lyricsOffsets = StorageLyricsOffsetStore(queries, nowEpochMillis),
                    sidecarStatuses = StorageSidecarStatusStore(queries),
                    libraryIndex = StorageLibraryIndexStore(queries, mediaSources.store, nowEpochMillis),
                    keepDownloaded = StorageKeepDownloadedStore(queries, nowEpochMillis),
                    pendingProviderActions = StoragePendingProviderActionStore(queries, nowEpochMillis),
                    playbackSessions = StoragePlaybackSessionStore(queries, nowEpochMillis, json),
                    radioDjPresets = StorageRadioDjPresetStore(queries, nowEpochMillis),
                    maintenance = DesktopCacheMaintenanceRepository(
                        storage = mediaSources,
                        databasePath = Path.of(location.directoryPath).resolve(location.fileName),
                        audioCacheDirectory = { audioCacheDirectory },
                        downloadDirectory = { downloadDirectory },
                        hotImages = hotImages,
                        maxImageBytes = maxImageBytes,
                        maxAudioBytes = maxAudioBytes,
                        maxAudioWaveformBytes = maxAudioWaveformBytes,
                        maxHotImageBytes = maxHotImageBytes,
                        legacyDatabaseFilesOnReset = legacyDatabaseFilesOnReset,
                    ),
                )
            } catch (failure: Throwable) {
                mediaSources.close()
                throw failure
            }
        }
    }
}

private const val DefaultDesktopAudioCacheBytes = 2L * 1024L * 1024L * 1024L
private const val DefaultDesktopAudioWaveformCacheBytes = 32L * 1024L * 1024L
private const val DefaultDesktopHotImageCacheBytes = 32L * 1024L * 1024L
