package app.naviamp.desktop

import app.naviamp.desktop.playback.bass.DesktopBassPlaybackEngineRuntime
import app.naviamp.desktop.playback.bass.loadDesktopBassAudioBackend
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.domain.playback.ReleasablePlaybackEngine
import app.naviamp.presentation.NaviampCoreHomeDateSource
import app.naviamp.presentation.NaviampCoreMutableNowPlayingSidecars
import app.naviamp.presentation.NaviampCorePlaybackEngineAdapter
import app.naviamp.presentation.NaviampCorePlaybackEngineSettings
import app.naviamp.presentation.NaviampCorePlaybackServices
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreVisualizerSettingsPort
import app.naviamp.presentation.naviampCoreServiceDefaults
import app.naviamp.presentation.unavailableNaviampCoreSettingsSyncServices
import app.naviamp.storage.StorageDatabaseLocation
import kotlinx.coroutines.CoroutineScope
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
            val engine = CoreBassPlaybackEngine(
                backendResult = loadDesktopBassAudioBackend(),
                runtime = DesktopBassPlaybackEngineRuntime(),
            )
            val engineSettings = NaviampCorePlaybackEngineSettings(engine)
            val playbackEffects = NaviampCorePlaybackEngineAdapter(
                scope = scope,
                engine = engine,
                providerSource = sessions.providerSource,
                settings = engineSettings::current,
            )
            val playback = NaviampCorePlaybackServices(
                effects = playbackEffects,
                settings = engineSettings,
                sidecars = NaviampCoreMutableNowPlayingSidecars(),
                visualizerSettings = object : NaviampCoreVisualizerSettingsPort {
                    override fun save(visualizer: app.naviamp.ui.NaviampVisualizer) = Unit
                },
            )
            var syncConfiguration = NaviampCoreSettingsSyncConfiguration()
            val sync = unavailableNaviampCoreSettingsSyncServices(nowEpochMillis).copy(
                port = app.naviamp.desktop.settings.DesktopCoreSettingsSyncPort(
                    configurationState = { syncConfiguration },
                    saveConfigurationState = { syncConfiguration = it },
                ),
            )
            val services = naviampCoreServiceDefaults(
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
            return DesktopV2Composition(
                environment = desktopNaviampCoreEnvironment(
                    services = services,
                    providerSessions = sessions,
                    settingsSync = sync,
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
