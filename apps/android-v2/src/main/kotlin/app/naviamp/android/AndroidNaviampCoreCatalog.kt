package app.naviamp.android

import android.content.Context
import app.naviamp.app.NaviampClock
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCorePlaybackServices
import app.naviamp.presentation.NaviampCoreStoredRepositories
import app.naviamp.presentation.NaviampCoreStoredSettings
import app.naviamp.presentation.naviampCoreStoredServiceCatalog
import app.naviamp.ui.NaviampStorageLocationUi
import java.io.File
import java.time.Instant
import java.time.LocalDate

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
        storage.close()
    }

    companion object {
        fun create(
            context: Context,
            playbackEngine: PlaybackEngine,
            playback: NaviampCorePlaybackServices,
            directoryPicker: AndroidCoreUriPicker,
            documentPicker: AndroidCoreUriPicker,
        ): AndroidNaviampCoreCatalog {
            val appContext = context.applicationContext
            val clock = NaviampClock(System::currentTimeMillis)
            val settingsStore = AndroidSettingsStore(appContext)
            val cacheSettings = settingsStore.loadCacheSettings()
            val storage = AndroidStorageDependencies(appContext)
            cacheSettings.customDownloadDirectory?.let(::File)?.let(storage::updateDownloadDirectory)
            cacheSettings.customAudioCacheDirectory?.let(::File)?.let(storage::updateAudioCacheDirectory)
            storage.updateAudioCacheLimit(cacheSettings.maxAudioCacheBytes)
            val sessions = androidCoreProviderSessionPort(storage, clock)
            val syncPort = AndroidCoreSettingsSyncPort(
                context = appContext,
                settingsStore = settingsStore,
                directoryPicker = directoryPicker,
                documentPicker = documentPicker,
            )
            val downloadLocations = androidDownloadStorageLocations(appContext)
            val audioCacheLocations = androidAudioCacheStorageLocations(appContext)
            val storedCatalog = naviampCoreStoredServiceCatalog(
                providerSessions = sessions,
                providerSource = sessions.providerSource,
                playback = playback,
                playbackEngine = playbackEngine,
                settingsSyncPort = syncPort,
                settings = settingsStore.toCoreStoredSettings(storage),
                repositories = NaviampCoreStoredRepositories(
                    mediaSources = storage,
                    providerMediaSources = storage,
                    libraryIndex = storage,
                    providerResponses = storage,
                    keepDownloaded = storage,
                    radioDjPresets = storage,
                    maintenance = storage,
                    updateAudioCacheLimit = storage::updateAudioCacheLimit,
                ),
                externalUri = AndroidCoreExternalUriPort(appContext),
                homeDate = NaviampCoreHomeDateSource {
                    LocalDate.now().let { date -> HomeDate(date.year, date.dayOfYear) }
                },
                shellCapabilities = AndroidCapabilityPresentation.toShellCapabilitiesUi(playbackEngine),
                settingsSyncDeviceId = AndroidSettingsSyncDeviceId,
                downloadLocations = downloadLocations.map(AndroidStorageLocation::toCoreUi),
                audioCacheLocations = audioCacheLocations.map(AndroidStorageLocation::toCoreUi),
                selectedDownloadLocationId = downloadLocations.idFor(storage.downloadDirectory),
                selectedAudioCacheLocationId = audioCacheLocations.idFor(storage.audioCacheDirectory),
                sourceId = { storage.latestNavidromeSource()?.id },
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

private fun AndroidSettingsStore.toCoreStoredSettings(
    storage: AndroidStorageDependencies,
) = NaviampCoreStoredSettings(
    loadInterface = ::loadInterfaceSettings,
    saveInterface = ::saveInterfaceSettings,
    loadPlayback = {
        loadPlaybackSettings().copy(radioDjs = storage.radioDjPresets())
    },
    loadCache = ::loadCacheSettings,
    saveCache = ::saveCacheSettings,
    loadVisualizer = ::loadVisualizerSettings,
    saveVisualizer = ::saveVisualizerSettings,
    loadRecentRadioStreams = ::loadRecentRadioStreams,
    saveRecentRadioStreams = ::saveRecentRadioStreams,
    loadRecentInternetRadioStations = ::loadRecentInternetRadioStations,
    saveRecentInternetRadioStations = ::saveRecentInternetRadioStations,
    loadSyncRuntime = {
        loadSettingsSync().let { persisted ->
            SettingsSyncRuntimeState(
                autoExportEnabled = persisted.autoExportEnabled,
                lastLocalUpdateEpochMillis = persisted.lastLocalUpdateEpochMillis,
                lastAppliedSyncUpdateEpochMillis = persisted.lastAppliedSyncUpdateEpochMillis,
            )
        }
    },
    saveSyncRuntime = { runtime ->
        saveSettingsSync(
            loadSettingsSync().copy(
                autoExportEnabled = runtime.autoExportEnabled,
                lastLocalUpdateEpochMillis = runtime.lastLocalUpdateEpochMillis,
                lastAppliedSyncUpdateEpochMillis = runtime.lastAppliedSyncUpdateEpochMillis,
            ),
        )
    },
)

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
