package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngineRuntime
import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.network.KtorSharedHttpClient
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.ReleasablePlaybackEngine
import app.naviamp.domain.playback.AudioOutputDevicePlaybackEngine
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.NaviampCoreInterfaceSettingsStore
import app.naviamp.presentation.NaviampCoreCacheSettingsPort
import app.naviamp.presentation.NaviampCoreProviderNowPlayingSidecars
import app.naviamp.presentation.NaviampCorePlaybackEngineAdapter
import app.naviamp.presentation.NaviampCorePlaybackEngineSettings
import app.naviamp.presentation.NaviampCorePlaybackServices
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreVisualizerSettingsPort
import app.naviamp.presentation.naviampCoreServiceDefaults
import app.naviamp.presentation.unavailableNaviampCoreSettingsSyncServices
import app.naviamp.presentation.toShellCapabilitiesUi
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.jvmGeneratedCoverArtBytes
import app.naviamp.ui.resetJvmPlatformCoverArtByteLoader
import app.naviamp.ui.setJvmPlatformCoverArtByteLoader
import app.naviamp.ui.naviampVisualizerFromName
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.playback.emptyPlaybackAudioAssetRepository
import app.naviamp.domain.waveform.AudioWaveformService
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.desktop.DesktopAudioWaveformAnalyzer
import app.naviamp.desktop.settings.DesktopCoreSettingsStore
import app.naviamp.desktop.settings.defaultDesktopCoreSettingsPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

/** Owns only Desktop filesystem/native resources required by the shared Core app. */
internal class DesktopV2Composition private constructor(
    val environment: DesktopNaviampCoreEnvironment,
    private val engine: ReleasablePlaybackEngine,
    private val storage: DesktopStorageRepositories,
) : AutoCloseable {
    override fun close() {
        resetJvmPlatformCoverArtByteLoader()
        engine.release()
        storage.close()
    }

    companion object {
        fun create(scope: CoroutineScope): DesktopV2Composition {
            val nowEpochMillis = System::currentTimeMillis
            val dataDirectory = desktopV2DataDirectory()
            Files.createDirectories(dataDirectory)
            val storage = DesktopStorageRepositories.open(
                location = StorageDatabaseLocation(dataDirectory.toString()),
                audioCacheDirectory = dataDirectory.resolve("audio-cache"),
                downloadDirectory = dataDirectory.resolve("downloads"),
                nowEpochMillis = nowEpochMillis,
            )
            val sessions = desktopCoreProviderSessionPort(
                storage = storage.mediaSources,
                cacheMaintenanceRepository = storage.maintenance,
                nowEpochMillis = nowEpochMillis,
            )
            val fallbackArtworkHttp = KtorSharedHttpClient()
            setJvmPlatformCoverArtByteLoader { url ->
                jvmGeneratedCoverArtBytes(url)
                    ?: (sessions.currentProvider() as? NavidromeProvider)
                        ?.takeIf { provider -> provider.ownsUrl(url) }
                        ?.bytes(url)
                    ?: fallbackArtworkHttp.getBytes(url)
                    ?: ByteArray(0)
            }
            val engine = CoreBassPlaybackEngine(
                backendResult = loadDesktopBassAudioBackend(),
                runtime = DesktopBassPlaybackEngineRuntime(),
            )
            val settingsStore = DesktopCoreSettingsStore(defaultDesktopCoreSettingsPath())
            val playbackSessions = NaviampPlaybackSessionController(storage.playbackSessions)
            storage.mediaSources.latestMediaSource()?.id?.let { sourceId ->
                if (playbackSessions.load(sourceId) == null) {
                    settingsStore.loadLegacyPlaybackSession()?.let { legacy ->
                        playbackSessions.save(legacy, sourceId)
                        settingsStore.removeLegacyPlaybackSession()
                    }
                }
            }
            val initialInterfaceSettings = settingsStore.loadInterfaceSettings()
            val initialPlaybackSettings = settingsStore.loadPlaybackSettings()
            val initialCacheSettings = settingsStore.loadCacheSettings()
            var activeCacheSettings = initialCacheSettings
            val initialVisualizer = naviampVisualizerFromName(
                settingsStore.loadVisualizerSettings().selectedVisualizer,
            )
            val engineSettings = NaviampCorePlaybackEngineSettings(
                engine = engine,
                initial = initialPlaybackSettings,
                persist = settingsStore::savePlaybackSettings,
            )
            val playbackEffects = NaviampCorePlaybackEngineAdapter(
                scope = scope,
                engine = engine,
                providerSource = sessions.providerSource,
                settings = engineSettings::current,
            )
            val waveformService = AudioWaveformService(
                waveformRepository = storage.audioWaveforms,
                audioAssets = emptyPlaybackAudioAssetRepository(),
                analyzer = DesktopAudioWaveformAnalyzer(),
                waveformsEnabled = { activeCacheSettings.waveformsEnabled },
                waveformBucketCount = { activeCacheSettings.waveformBucketCount },
                cacheAudioBeforeAnalysis = { false },
                workContext = Dispatchers.IO,
            )
            val playback = NaviampCorePlaybackServices(
                effects = playbackEffects,
                settings = engineSettings,
                sidecars = NaviampCoreProviderNowPlayingSidecars(
                    providerSource = sessions.providerSource,
                    waveformService = waveformService,
                    playbackSettings = engineSettings::current,
                    audioCachingEnabled = { activeCacheSettings.audioCachingEnabled },
                ),
                visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
                    override fun save(visualizer: app.naviamp.ui.NaviampVisualizer) {
                        settingsStore.saveVisualizerSettings(VisualizerSettings(visualizer.name))
                    }
                },
                sessions = playbackSessions,
            )
            var syncConfiguration = NaviampCoreSettingsSyncConfiguration()
            val sync = unavailableNaviampCoreSettingsSyncServices(nowEpochMillis).copy(
                port = app.naviamp.desktop.settings.DesktopCoreSettingsSyncPort(
                    configurationState = { syncConfiguration },
                    saveConfigurationState = { syncConfiguration = it },
                ),
            )
            val serviceDefaults = naviampCoreServiceDefaults(
                providerSource = sessions.providerSource,
                connection = sessions,
                playback = playback,
                settingsSync = sync,
                externalUri = DesktopExternalUriPort(),
                homeDate = NaviampCoreHomeDateSource {
                    LocalDate.now().let { HomeDate(it.year, it.dayOfYear) }
                },
                clockEpochMillis = nowEpochMillis,
                favoritedAtIso8601 = { Instant.now().toString() },
            )
            val services = serviceDefaults.copy(
                settings = serviceDefaults.settings.copy(
                    interfaceSettings = NaviampCoreInterfaceSettingsStore(settingsStore::saveInterfaceSettings),
                    cacheSettings = NaviampCoreCacheSettingsPort { requested ->
                        requested.normalized().also { effective ->
                            activeCacheSettings = effective
                            settingsStore.saveCacheSettings(effective)
                        }
                    },
                ),
            )
            val initialState = NaviampCoreInitialState().let { initial ->
                initial.copy(
                    product = initial.product.copy(
                        shell = initial.product.shell.copy(
                            general = initial.product.shell.general.copy(interfaceSettings = initialInterfaceSettings),
                            playback = initial.product.shell.playback.copy(settings = initialPlaybackSettings),
                            cache = initial.product.shell.cache.copy(settings = initialCacheSettings),
                            shellChrome = initial.product.shell.shellChrome.copy(
                                selectedVisualizer = initialVisualizer,
                            ),
                        ),
                    ),
                )
            }
            return DesktopV2Composition(
                environment = desktopNaviampCoreEnvironment(
                    services = services,
                    providerSessions = sessions,
                    settingsSync = sync,
                    initialState = initialState,
                    shellCapabilities = DesktopCapabilityPresentation.toShellCapabilitiesUi(
                        playbackEngine = engine,
                        sonicSimilarityAvailable = false,
                    ),
                    audioOutputDeviceSelectionAvailable =
                        (engine as? AudioOutputDevicePlaybackEngine)
                            ?.supportsAudioOutputDeviceSelection == true,
                    audioOutputDevices =
                        (engine as? AudioOutputDevicePlaybackEngine)?.outputDevices().orEmpty(),
                    onAsyncFailure = { command, failure ->
                        System.err.println("Naviamp Core command failed: $command: ${failure.message}")
                        failure.printStackTrace()
                    },
                ),
                engine = engine,
                storage = storage,
            )
        }
    }
}

private fun desktopV2DataDirectory(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") ->
            home.resolve("Library/Application Support/Naviamp")
        os.contains("win") ->
            Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString()).resolve("Naviamp")
        else -> home.resolve(".local/share/naviamp")
    }
}
