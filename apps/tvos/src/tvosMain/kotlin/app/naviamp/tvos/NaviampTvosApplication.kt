@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.tvos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.apple.playback.applePlaybackLocalFilePath
import app.naviamp.apple.media.ApplePlaybackSurface
import app.naviamp.bootstrap.*
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import app.naviamp.domain.playback.CoreBassPlaybackEngine
import app.naviamp.host.*
import app.naviamp.presentation.*
import app.naviamp.storage.*
import app.naviamp.tvos.playback.TvosBassAudioBackend
import app.naviamp.tvos.playback.createTvosBassPlaybackEngine
import app.naviamp.ui.*
import platform.Foundation.NSFileManager
import platform.UIKit.UIViewController

/** UIKit/SQLite/BASS resource boundary. All application behavior and recovery policy are in Core. */
class NaviampTvosApplication(
    private val cacheDirectory: String,
    document: StorageRecoveryDocumentPort,
    secretBox: StorageSecretBox,
    lock: StorageRecoveryLock,
) {
    private var driver: SqlDriver? = null
    private var playbackEngine: CoreBassPlaybackEngine? = null
    private var networkMetering: AppleNetworkMeteringSource? = null
    private var contentViewController: UIViewController? = null
    private val controller = naviampRecoverableStoredHostController(
        open = { scope, onFailure ->
            check(NSFileManager.defaultManager.createDirectoryAtPath(cacheDirectory, true, null, null))
            val protector = StorageAeadCredentialProtector(secretBox)
            val recovery = StorageRecoveryState(document, protector, lock, files = AppleRecoveryFiles("$cacheDirectory/certificates"))
            val nativeDriver = NativeStorageDriverFactory(StorageDatabaseLocation(cacheDirectory)).createDriver().also { driver = it }
            val engine = createTvosBassPlaybackEngine().also { playbackEngine = it }
            val metering = AppleNetworkMeteringSource().also { networkMetering = it }
            // Native media registration must succeed before the common host publishes this app.
            val facts = PlatformCapabilities().withStatus(PlatformCapability.SecureCredentialStorage, PlatformCapabilityStatus.Available)
                .withStatus(PlatformCapability.InsecureServerVerification, PlatformCapabilityStatus.Available)
                .withStatus(PlatformCapability.SystemMediaControls, PlatformCapabilityStatus.Available)
                .withStatus(PlatformCapability.BackgroundPlayback, PlatformCapabilityStatus.Available)
            val capabilities = NaviampCapabilityPresentation(facts)
            NaviampStoredApplication(
                scope = scope, database = NaviampStorageDatabase(nativeDriver), credentialProtector = protector,
                settingsValueStore = NaviampRecoverySettingsValueStore(recovery), recoveryState = recovery,
                settingsSyncPort = UnavailableNaviampCoreSettingsSyncPort, certificateFiles = null,
                databaseLabel = cacheDirectory, audioCacheDirectory = appleStorageAudioDirectory("$cacheDirectory/audio"),
                downloadDirectory = appleStorageAudioDirectory("$cacheDirectory/downloads"),
                playbackEngine = engine, waveformBass = TvosBassAudioBackend(), localFilePath = ::applePlaybackLocalFilePath,
                audioTagReader = AppleAudioTagReader(), externalUri = AppleCoreExternalUriPort(), homeDate = AppleHomeDateSource,
                shellCapabilities = capabilities.toShellCapabilitiesUi(engine, sonicSimilarityAvailable = false),
                actionAvailability = capabilities.toCoreActionAvailability(),
                providerDeviceId = "naviamp-tvos", providerDeviceName = "tvOS", settingsSyncDeviceId = "tvos",
                downloadLocations = emptyList(), audioCacheLocations = emptyList(),
                selectedDownloadLocationId = null, selectedAudioCacheLocationId = null,
                networkMetering = metering,
                onAsyncFailure = { _, cause -> onFailure(cause) },
            )
        },
        closeNative = {
            playbackEngine?.release(); playbackEngine = null
            networkMetering?.close(); networkMetering = null
            driver?.close(); driver = null
        },
        erase = { resetStorageRecovery(document, secretBox, lock) },
        attachApplication = { application, scope, retain, onFailure ->
            application.attachPlaybackSurface(scope, ApplePlaybackSurface(), retain, onFailure)
        },
    )

    fun viewController(): UIViewController = contentViewController ?: ComposeUIViewController {
        val textEntry = remember { TvosTextEntry { contentViewController } }
        CompositionLocalProvider(
            LocalNaviampApplicationSurface provides NaviampApplicationSurface.Television,
            LocalNaviampTelevisionTextEntry provides textEntry,
        ) {
            NaviampAppleRasterHost(rootLayer = { contentViewController?.view?.layer }) {
                NaviampRecoverableHostApp(controller) { application ->
                    NaviampCoreApp(application.core, applicationSurface = NaviampApplicationSurface.Television,
                        screenAwakeEffect = remember { AppleScreenAwakeEffect() },
                        applicationUpdateChecker = application.environment.applicationUpdateChecker)
                }
            }
        }
    }.also { contentViewController = it }

    fun close() { contentViewController = null; controller.close() }
}
