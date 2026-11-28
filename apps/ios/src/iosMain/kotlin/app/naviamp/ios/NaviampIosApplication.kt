package app.naviamp.ios

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.apple.media.ApplePlaybackSurface
import app.naviamp.apple.playback.applePlaybackLocalFilePath
import app.naviamp.bootstrap.NaviampRecoverableHostApp
import app.naviamp.bootstrap.NaviampStoredApplication
import app.naviamp.bootstrap.attachPlaybackSurface
import app.naviamp.bootstrap.naviampRecoverableStoredHostController
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.ios.platform.IosCapabilityPresentation
import app.naviamp.ios.playback.IosBassAudioBackend
import app.naviamp.ios.playback.createIosBassPlaybackEngine
import app.naviamp.ios.settings.IosCoreCertificateFilePort
import app.naviamp.ios.settings.IosCoreSettingsSyncPort
import app.naviamp.ios.settings.IosCoreSettingsValueStore
import app.naviamp.presentation.AppleCoreExternalUriPort
import app.naviamp.presentation.AppleHomeDateSource
import app.naviamp.presentation.AppleScreenAwakeEffect
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.vehicleBrowseController
import app.naviamp.storage.AppleAudioTagReader
import app.naviamp.storage.NativeStorageDriverFactory
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.storage.appleStorageAudioDirectory
import app.naviamp.ui.NaviampAppleRasterHost
import app.naviamp.ui.NaviampStorageLocationKind
import app.naviamp.ui.NaviampStorageLocationUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import platform.UIKit.UIViewController
import kotlin.native.ref.WeakReference

/**
 * Process-level iOS owner for native database and BASS lifetime. Startup, retry, failure and
 * product behavior come from the shared recoverable host and Core.
 */
@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
class NaviampIosApplication(
    applicationSupportDirectory: String,
    credentialProtector: StorageCredentialProtector,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val databaseLocation = StorageDatabaseLocation(applicationSupportDirectory)
    private val settingsValueStore = IosCoreSettingsValueStore()
    private val contentViewControllers = mutableListOf<WeakReference<UIViewController>>()
    private val settingsSyncPort = IosCoreSettingsSyncPort(
        settingsStore = settingsValueStore,
        presenter = { contentViewControllers.mapNotNull { it.get() }
            .lastOrNull { it.viewIfLoaded?.window?.isKeyWindow() == true } },
    )
    private val audioCacheDirectory = "${databaseLocation.directoryPath}/audio-cache"
    private val downloadDirectory = "${databaseLocation.directoryPath}/downloads"
    private var driver: SqlDriver? = null
    private var playbackEngine: CoreBassPlaybackEngine? = null
    private val controller = naviampRecoverableStoredHostController(
        open = { applicationScope, onFailure ->
            val nativeDriver = NativeStorageDriverFactory(databaseLocation).createDriver().also { driver = it }
            val engine = createIosBassPlaybackEngine().also { playbackEngine = it }
            NaviampStoredApplication(
                scope = applicationScope,
                database = NaviampStorageDatabase(nativeDriver),
                credentialProtector = credentialProtector,
                settingsValueStore = settingsValueStore,
                settingsSyncPort = settingsSyncPort,
                certificateFiles = IosCoreCertificateFilePort(settingsSyncPort, "${databaseLocation.directoryPath}/certificates"),
                databaseLabel = "${databaseLocation.directoryPath}/${databaseLocation.fileName}",
                audioCacheDirectory = appleStorageAudioDirectory(audioCacheDirectory),
                downloadDirectory = appleStorageAudioDirectory(downloadDirectory),
                playbackEngine = engine,
                waveformBass = IosBassAudioBackend(),
                localFilePath = ::applePlaybackLocalFilePath,
                audioTagReader = AppleAudioTagReader(),
                externalUri = AppleCoreExternalUriPort(),
                homeDate = AppleHomeDateSource,
                shellCapabilities = IosCapabilityPresentation.shell(engine),
                actionAvailability = IosCapabilityPresentation.actionAvailability,
                providerDeviceIdPrefix = "naviamp-ios",
                providerDeviceName = "iOS",
                settingsSyncDeviceId = "ios",
                downloadLocations = listOf(appStorageLocation(downloadDirectory)),
                audioCacheLocations = listOf(appStorageLocation(audioCacheDirectory)),
                selectedDownloadLocationId = AppStorageLocationId,
                selectedAudioCacheLocationId = AppStorageLocationId,
                onAsyncFailure = { _, cause -> onFailure(cause) },
            ).also { it.core.startRuntimeServices() }
        },
        closeNative = {
            playbackEngine?.release(); playbackEngine = null
            driver?.close(); driver = null
        },
        // Durable iOS storage has no reset effect; retry is the only recovery action.
        erase = null,
        attachApplication = { application, applicationScope, retain, onFailure ->
            application.attachPlaybackSurface(applicationScope, ApplePlaybackSurface(), retain, onFailure)
        },
    )

    init {
        // CarPlay can launch without phone UI, so the application cannot wait for composition.
        controller.start()
    }

    fun viewController(): UIViewController {
        var reference: WeakReference<UIViewController>? = null
        val viewController = ComposeUIViewController {
            NaviampAppleRasterHost(rootLayer = { reference?.get()?.view?.layer }) {
                NaviampRecoverableHostApp(controller) { application ->
                    NaviampCoreApp(
                        core = application.core,
                        screenAwakeEffect = remember { AppleScreenAwakeEffect() },
                        applicationUpdateChecker = application.environment.applicationUpdateChecker,
                    )
                }
            }
        }
        val controllerReference = WeakReference(viewController)
        reference = controllerReference
        contentViewControllers.removeAll { it.get() == null }
        contentViewControllers.add(controllerReference)
        return viewController
    }

    fun releaseViewController(controller: UIViewController) {
        contentViewControllers.removeAll { it.get() == null || it.get() === controller }
    }

    /** Swift ABI entry point; capabilities are read from Apple's connected template environment. */
    fun vehicleBrowseSession(
        maximumItems: Int,
        maximumTabs: Int,
        supportsSearch: Boolean = false,
        changed: (NaviampIosVehicleBrowseState) -> Unit,
    ): NaviampIosVehicleBrowseSession = NaviampIosVehicleBrowseSession(scope, {
        val application = controller.state.first { it.application != null }.application!!
        application.core.vehicleBrowseController(scope, maximumItems, maximumTabs, supportsSearch)
    }, changed)

    fun close() {
        controller.close()
        scope.cancel()
    }
}

private const val AppStorageLocationId = "app-storage"

private fun appStorageLocation(path: String) =
    NaviampStorageLocationUi(AppStorageLocationId, "", path, NaviampStorageLocationKind.AppStorage)
