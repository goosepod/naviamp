package app.naviamp.desktop

import app.naviamp.domain.cache.MaximumPersistentArtworkCacheBytes
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseDriverFactory
import app.naviamp.storage.StorageDatabaseLocation
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
    val providerResponses: DesktopProviderResponseStore,
    val objectBytes: DesktopObjectByteStore,
    val audioCacheBytes: DesktopMutableAudioByteStore,
    val downloadBytes: DesktopMutableAudioByteStore,
    val audioWaveforms: DesktopAudioWaveformStore,
    val lyricsSidecars: DesktopLyricsSidecarStore,
    val lyricsOffsets: DesktopLyricsOffsetStore,
    val sidecarStatuses: DesktopSidecarStatusStore,
    val libraryIndex: DesktopLibraryIndexStore,
    val pendingProviderActions: DesktopPendingProviderActionStore,
    val radioDjPresets: DesktopRadioDjPresetStore,
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
                DesktopStorageRepositories(
                    mediaSources = mediaSources,
                    providerResponses = DesktopProviderResponseStore(queries),
                    objectBytes = DesktopObjectByteStore(queries, nowEpochMillis, maxImageBytes),
                    audioCacheBytes = DesktopMutableAudioByteStore(audioCacheDirectory),
                    downloadBytes = DesktopMutableAudioByteStore(downloadDirectory),
                    audioWaveforms = DesktopAudioWaveformStore(
                        queries,
                        json,
                        nowEpochMillis,
                        maxAudioWaveformBytes,
                    ),
                    lyricsSidecars = DesktopLyricsSidecarStore(queries),
                    lyricsOffsets = DesktopLyricsOffsetStore(queries, nowEpochMillis),
                    sidecarStatuses = DesktopSidecarStatusStore(queries),
                    libraryIndex = DesktopLibraryIndexStore(queries, mediaSources.store, nowEpochMillis),
                    pendingProviderActions = DesktopPendingProviderActionStore(queries, nowEpochMillis),
                    radioDjPresets = DesktopRadioDjPresetStore(queries, nowEpochMillis),
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
