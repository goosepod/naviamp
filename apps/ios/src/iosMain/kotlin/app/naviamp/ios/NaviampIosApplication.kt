package app.naviamp.ios

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.ios.platform.IosCapabilityPresentation
import app.naviamp.ios.platform.IosCoreExternalUriPort
import app.naviamp.ios.platform.IosHomeDateSource
import app.naviamp.ios.playback.createIosBassPlaybackEngine
import app.naviamp.ios.settings.IosCoreSettingsValueStore
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreHost
import app.naviamp.presentation.naviampCoreSettingsValueCatalog
import app.naviamp.presentation.naviampCoreStreamingPlaybackServices
import app.naviamp.presentation.naviampCoreStoredServiceCatalog
import app.naviamp.presentation.naviampNowEpochMillis
import app.naviamp.presentation.naviampNowIso8601
import app.naviamp.presentation.unavailableNaviampCoreDownloadServices
import app.naviamp.presentation.unavailableNaviampCoreSettingsSyncServices
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener
import app.naviamp.storage.IosStorageDriverFactory
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCoreRepositoryCatalog
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import app.naviamp.provider.navidrome.NavidromeProvider
import app.naviamp.ui.resetIosPlatformCoverArtByteLoader
import app.naviamp.ui.setIosPlatformCoverArtByteLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.UIKit.UIViewController

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
    private val settings = naviampCoreSettingsValueCatalog(IosCoreSettingsValueStore())
    private val repositories = StorageCoreRepositoryCatalog(
        database = NaviampStorageDatabase(driver),
        credentialProtector = credentialProtector,
        nowEpochMillis = ::naviampNowEpochMillis,
        databaseLabel = "${databaseLocation.directoryPath}/${databaseLocation.fileName}",
        deleteKnownAudioCacheFile = { false },
        deleteKnownDownloadFile = { false },
    )
    private val sessions = NavidromeCoreProviderSessionPort(
        mediaSources = repositories.mediaSources,
        initialSource = repositories.mediaSources.latestMediaSource(),
        sessionOpener = navidromeProviderSessionOpener(
            cacheMaintenanceRepository = repositories.maintenance,
            providerMediaSourceRepository = repositories.mediaSources,
            nowEpochMillis = ::naviampNowEpochMillis,
        ),
    )
    init {
        setIosPlatformCoverArtByteLoader { url ->
            (sessions.currentProvider() as? NavidromeProvider)
                ?.takeIf { provider -> provider.ownsUrl(url) }
                ?.bytes(url)
        }
    }
    private val playbackEngine = createIosBassPlaybackEngine()
    private val playback = naviampCoreStreamingPlaybackServices(
        scope = scope,
        engine = playbackEngine,
        providerSource = sessions.providerSource,
        initialPlaybackSettings = settings.storedSettings.loadPlayback(),
        persistPlaybackSettings = settings.savePlayback,
        playbackSessionRepository = repositories.playbackSessions,
        saveVisualizerSettings = settings.storedSettings.saveVisualizer,
        verifyProviderNetworkCertificates = {
            repositories.mediaSources.latestMediaSource()?.tlsSettings?.insecureSkipTlsVerification != true
        },
    )
    private val storedCatalog = naviampCoreStoredServiceCatalog(
        providerSessions = sessions,
        providerSource = sessions.providerSource,
        playback = playback,
        downloads = unavailableNaviampCoreDownloadServices(),
        playbackEngine = playbackEngine,
        settingsSyncPort = unavailableNaviampCoreSettingsSyncServices(::naviampNowEpochMillis).port,
        settings = settings.storedSettings,
        repositories = app.naviamp.presentation.NaviampCoreStoredRepositories(
            mediaSources = repositories.mediaSources,
            providerMediaSources = repositories.mediaSources,
            libraryIndex = repositories.libraryIndex,
            providerResponses = repositories.providerResponses,
            keepDownloaded = repositories.keepDownloaded,
            radioDjPresets = repositories.radioDjPresets,
            maintenance = repositories.maintenance,
        ),
        externalUri = IosCoreExternalUriPort(),
        homeDate = IosHomeDateSource,
        shellCapabilities = IosCapabilityPresentation.shell(playbackEngine),
        settingsSyncDeviceId = "ios",
        sourceId = { repositories.mediaSources.latestMediaSource()?.id },
        clockEpochMillis = ::naviampNowEpochMillis,
        favoritedAtIso8601 = ::naviampNowIso8601,
    )
    private val environment = NaviampCoreEnvironment(
        services = storedCatalog.services,
        initialState = storedCatalog.initialState,
        actionAvailability = IosCapabilityPresentation.actionAvailability,
        onAsyncFailure = { command, cause ->
            throw IllegalStateException("iOS Core command failed: $command", cause)
        },
    )

    fun viewController(): UIViewController = ComposeUIViewController {
        NaviampCoreHost(
            environment = environment,
            modifier = Modifier.safeDrawingPadding().imePadding(),
        )
    }

    fun close() {
        resetIosPlatformCoverArtByteLoader()
        playbackEngine.release()
        scope.cancel()
        driver.close()
    }
}
