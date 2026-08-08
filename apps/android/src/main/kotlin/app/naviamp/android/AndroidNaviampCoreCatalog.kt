package app.naviamp.android

import android.content.Context
import app.naviamp.android.playback.AndroidAudioTagReader
import app.naviamp.app.NaviampClock
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.waveform.AudioWaveformAnalyzer
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreDownloadedTrack
import app.naviamp.presentation.NaviampCoreDownloadStorageSnapshot
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCoreMobileNetworkPort
import app.naviamp.presentation.NaviampCoreStoredRepositories
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.presentation.naviampCoreStoredServiceCatalog
import app.naviamp.presentation.naviampCorePlaybackServiceCatalog
import app.naviamp.presentation.repositoryNaviampCoreDownloadServices
import app.naviamp.presentation.withStorageBackedSettings
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.resetAndroidPlatformCoverArtByteLoader
import app.naviamp.ui.setAndroidPlatformCoverArtByteLoader
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Android resource owner for the shared Core catalog.
 *
 * This class selects Context, ContentResolver, SharedPreferences, Keystore, Android SQLite, and
 * Android TLS effects. All service-family assembly and product state remain in Core.
 */
class AndroidNaviampCoreCatalog private constructor(
    val environment: NaviampCoreEnvironment,
    private val storage: AndroidStorageDependencies,
) : AutoCloseable {
    override fun close() {
        resetAndroidPlatformCoverArtByteLoader()
        storage.close()
    }

    companion object {
        fun create(
            context: Context,
            scope: CoroutineScope,
            playbackEngine: PlaybackEngine,
            waveformAnalyzer: AudioWaveformAnalyzer,
            directoryPicker: AndroidCoreUriPicker,
            documentPicker: AndroidCoreUriPicker,
            isMobileData: () -> Boolean = { false },
            prepareWaveformAnalysis: suspend () -> Unit = {},
            waveformWorkContext: CoroutineContext = EmptyCoroutineContext,
        ): AndroidNaviampCoreCatalog {
            val appContext = context.applicationContext
            val clock = NaviampClock(System::currentTimeMillis)
            val platformSettings = AndroidSettingsStore(appContext)
            val settingsCatalog = naviampCoreSettingsValueCatalog(AndroidCoreSettingsValueStore(appContext))
            var cacheSettings = settingsCatalog.storedSettings.loadCache()
            val storage = AndroidStorageDependencies(appContext)
            cacheSettings.customDownloadDirectory?.let(::File)?.let(storage::updateDownloadDirectory)
            cacheSettings.customAudioCacheDirectory?.let(::File)?.let(storage::updateAudioCacheDirectory)
            storage.updateAudioCacheLimit(cacheSettings.maxAudioCacheBytes)
            val sessions = androidCoreProviderSessionPort(storage, clock)
            setAndroidPlatformCoverArtByteLoader { url ->
                runCatching {
                    storage.imageBytes(url) {
                        sessions.currentProvider()?.bytesForOwnedUrl(url)
                            ?: throw IllegalStateException("Could not load provider artwork.")
                    }
                }.getOrNull()
            }
            val playback = naviampCorePlaybackServiceCatalog(
                scope = scope,
                engine = playbackEngine,
                providerSource = sessions.providerSource,
                initialPlaybackSettings = settingsCatalog.storedSettings.loadPlayback(),
                persistPlaybackSettings = settingsCatalog.savePlayback,
                cacheSettings = { cacheSettings },
                isMobileData = isMobileData,
                activeSourceId = sessions::currentSourceId,
                audioAssets = AndroidPlaybackAudioAssets(storage, storage),
                cacheAudio = { sourceId, provider, track, quality ->
                    storage.cacheAudioTrack(sourceId, provider, track, quality)
                        .filePath
                        .let(::File)
                        .toPlaybackLocalAudio()
                },
                waveformRepository = storage,
                waveformAnalyzer = waveformAnalyzer,
                audioTagReader = AndroidAudioTagReader(),
                lyricsRepository = storage,
                lyricsOffsetRepository = storage,
                sidecarStatusRepository = storage,
                playbackSessionRepository = storage,
                saveVisualizerSettings = settingsCatalog.storedSettings.saveVisualizer,
                prepareWaveformAnalysis = prepareWaveformAnalysis,
                waveformWorkContext = waveformWorkContext,
            )
            val syncPort = AndroidCoreSettingsSyncPort(
                context = appContext,
                settingsStore = platformSettings,
                directoryPicker = directoryPicker,
                documentPicker = documentPicker,
            )
            val downloadLocations = androidDownloadStorageLocations(appContext)
            val audioCacheLocations = androidAudioCacheStorageLocations(appContext)
            val downloads = repositoryNaviampCoreDownloadServices(
                downloadRepository = storage,
                replacementRepository = storage,
                keepDownloadedRepository = storage,
                artworkCacheRepository = storage,
                toCoreDownload = { stored ->
                    NaviampCoreDownloadedTrack(
                        storageId = stored.filePath,
                        track = stored.track,
                        sizeBytes = stored.sizeBytes,
                        qualityLabel = stored.qualityKey,
                    )
                },
                isStoredDownloadAvailable = { stored -> File(stored.filePath).isFile },
                storageStats = {
                    storage.stats().let { stats ->
                        NaviampCoreDownloadStorageSnapshot(
                            audioCacheCount = stats.audioCount,
                            audioCacheBytes = stats.audioBytes,
                            pendingProviderActionCount = stats.pendingProviderActionCount,
                        )
                    }
                },
                network = NaviampCoreMobileNetworkPort(isMobileData),
            )
            val storedCatalog = naviampCoreStoredServiceCatalog(
                providerSessions = sessions,
                providerSource = sessions.providerSource,
                playback = playback,
                downloads = downloads,
                playbackEngine = playbackEngine,
                settingsSyncPort = syncPort,
                settings = settingsCatalog.storedSettings.withStorageBackedSettings(
                    radioDjPresetRepository = storage,
                    onCacheSettingsSaved = { effective ->
                        cacheSettings = effective
                        storage.updateDownloadDirectory(
                            effective.customDownloadDirectory?.let(::File)
                                ?: downloadLocations.first().directory,
                        )
                        storage.updateAudioCacheDirectory(
                            effective.customAudioCacheDirectory?.let(::File)
                                ?: audioCacheLocations.first().directory,
                        )
                    },
                ),
                repositories = NaviampCoreStoredRepositories(
                    mediaSources = storage,
                    providerMediaSources = storage,
                    libraryIndex = storage,
                    providerResponses = storage,
                    keepDownloaded = storage,
                    radioDjPresets = storage,
                    maintenance = storage,
                    pendingProviderActions = storage,
                    updateAudioCacheLimit = storage::updateAudioCacheLimit,
                ),
                externalUri = AndroidCoreExternalUriPort(appContext),
                homeDate = NaviampCoreHomeDateSource {
                    LocalDateTime.now().let { dateTime ->
                        HomeDate(dateTime.year, dateTime.dayOfYear, dateTime.hour)
                    }
                },
                shellCapabilities = AndroidCapabilityPresentation.toShellCapabilitiesUi(playbackEngine),
                settingsSyncDeviceId = AndroidSettingsSyncDeviceId,
                downloadLocations = downloadLocations.map(AndroidStorageLocation::toCoreUi),
                audioCacheLocations = audioCacheLocations.map(AndroidStorageLocation::toCoreUi),
                selectedDownloadLocationId = downloadLocations.idFor(storage.downloadDirectory),
                selectedAudioCacheLocationId = audioCacheLocations.idFor(storage.audioCacheDirectory),
                sourceId = sessions::currentSourceId,
                clockEpochMillis = clock::nowEpochMillis,
                favoritedAtIso8601 = { Instant.now().toString() },
                diagnostics = AndroidCoreDiagnosticsPort(storage::stats),
            )
            return AndroidNaviampCoreCatalog(
                environment = NaviampCoreEnvironment(
                    services = storedCatalog.services,
                    initialState = storedCatalog.initialState,
                    actionAvailability = AndroidCapabilityPresentation.toCoreActionAvailability(),
                    onAsyncFailure = { command, failure ->
                        throw IllegalStateException("Android Core command failed: $command", failure)
                    },
                ),
                storage = storage,
            )
        }
    }
}

private fun AndroidStorageLocation.toCoreUi() = NaviampStorageLocationUi(
    id = id,
    label = label,
    path = directory.absolutePath,
)

private fun List<AndroidStorageLocation>.idFor(directory: File): String? {
    val target = directory.canonicalFile
    return firstOrNull { location -> location.directory.canonicalFile == target }?.id
}

private const val AndroidSettingsSyncDeviceId = "android"
