package app.naviamp.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.ios.platform.IosCapabilityPresentation
import app.naviamp.ios.platform.IosCoreExternalUriPort
import app.naviamp.ios.platform.IosHomeDateSource
import app.naviamp.ios.playback.IosNativePlaybackIntegration
import app.naviamp.ios.playback.IosBassAudioBackend
import app.naviamp.ios.playback.IosAudioTagReader
import app.naviamp.ios.playback.createIosBassPlaybackEngine
import app.naviamp.ios.playback.iosPlaybackLocalFilePath
import app.naviamp.ios.settings.IosCoreSettingsValueStore
import app.naviamp.ios.settings.IosCoreSettingsSyncPort
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.externalPlaybackBridge
import app.naviamp.storage.IosStorageDriverFactory
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.NaviampIosRasterHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.UIKit.UIViewController
import app.naviamp.bootstrap.NaviampStoredApplication
import app.naviamp.ios.storage.iosStorageAudioDirectory

/**
 * Process-level iOS owner for native database lifetime and the shared Core environment.
 * Product state, actions, navigation, provider policy, and UI remain in common code.
 */
class NaviampIosApplication(
    applicationSupportDirectory: String,
    credentialProtector: StorageCredentialProtector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val databaseLocation = StorageDatabaseLocation(applicationSupportDirectory)
    private val driver: SqlDriver = IosStorageDriverFactory(
        databaseLocation,
    ).createDriver()
    private val database = NaviampStorageDatabase(driver)
    private val settingsValueStore = IosCoreSettingsValueStore()
    private var contentViewController: UIViewController? = null
    private val settingsSyncPort = IosCoreSettingsSyncPort(
        settingsStore = settingsValueStore,
        presenter = { contentViewController },
    )
    private val audioCacheDirectory = "${databaseLocation.directoryPath}/audio-cache"
    private val downloadDirectory = "${databaseLocation.directoryPath}/downloads"
    private val playbackEngine = createIosBassPlaybackEngine()
    private val application = NaviampStoredApplication(
        scope = scope,
        database = database,
        credentialProtector = credentialProtector,
        settingsValueStore = settingsValueStore,
        settingsSyncPort = settingsSyncPort,
        certificateFiles = app.naviamp.ios.settings.IosCoreCertificateFilePort(
            settingsSyncPort, "${databaseLocation.directoryPath}/certificates",
        ),
        databaseLabel = "${databaseLocation.directoryPath}/${databaseLocation.fileName}",
        audioCacheDirectory = iosStorageAudioDirectory(audioCacheDirectory),
        downloadDirectory = iosStorageAudioDirectory(downloadDirectory),
        playbackEngine = playbackEngine,
        waveformBass = IosBassAudioBackend(),
        localFilePath = ::iosPlaybackLocalFilePath,
        audioTagReader = IosAudioTagReader(),
        externalUri = IosCoreExternalUriPort(),
        homeDate = IosHomeDateSource,
        shellCapabilities = IosCapabilityPresentation.shell(playbackEngine),
        actionAvailability = IosCapabilityPresentation.actionAvailability,
        providerDeviceId = "naviamp-ios",
        providerDeviceName = "iOS",
        settingsSyncDeviceId = "ios",
        downloadLocations = listOf(NaviampStorageLocationUi("app-storage", "App storage", downloadDirectory)),
        audioCacheLocations = listOf(NaviampStorageLocationUi("app-storage", "App storage", audioCacheDirectory)),
        selectedDownloadLocationId = "app-storage",
        selectedAudioCacheLocationId = "app-storage",
    )
    private val nativePlayback = IosNativePlaybackIntegration(
        scope = scope,
        bridge = application.core.externalPlaybackBridge(),
        artworkBytes = application::loadProviderArtwork,
    )

    fun viewController(): UIViewController = contentViewController ?: ComposeUIViewController {
        NaviampIosRasterHost(rootLayer = { contentViewController?.view?.layer }) {
            NaviampCoreApp(
                core = application.core,
                screenAwakeEffect = remember { IosScreenAwakeEffect() },
                applicationUpdateChecker = application.environment.applicationUpdateChecker,
            )
        }
    }.also { contentViewController = it }

    fun close() {
        nativePlayback.close()
        application.close()
        playbackEngine.release()
        scope.cancel()
        driver.close()
    }

}
