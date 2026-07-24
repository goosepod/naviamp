package app.naviamp.ios

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import app.cash.sqldelight.db.SqlDriver
import app.naviamp.ios.platform.IosCapabilityPresentation
import app.naviamp.ios.platform.IosClock
import app.naviamp.ios.platform.IosCoreExternalUriPort
import app.naviamp.ios.settings.IosCoreSettingsStore
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreHost
import app.naviamp.presentation.naviampCoreStoredServiceCatalog
import app.naviamp.presentation.unavailableNaviampCoreDownloadServices
import app.naviamp.presentation.unavailableNaviampCorePlaybackServices
import app.naviamp.presentation.unavailableNaviampCoreSettingsSyncServices
import app.naviamp.provider.navidrome.NavidromeCoreProviderSessionPort
import app.naviamp.provider.navidrome.navidromeProviderSessionOpener
import app.naviamp.storage.IosStorageDriverFactory
import app.naviamp.storage.NaviampStorageDatabase
import app.naviamp.storage.StorageCoreRepositoryCatalog
import app.naviamp.storage.StorageCredentialProtector
import app.naviamp.storage.StorageDatabaseLocation
import platform.UIKit.UIViewController

/**
 * Process-level iOS owner for native database lifetime and the shared Core environment.
 * Product state, actions, navigation, provider policy, and UI remain in common code.
 */
class NaviampIosApplication(
    applicationSupportDirectory: String,
    credentialProtector: StorageCredentialProtector,
) {
    private val databaseLocation = StorageDatabaseLocation(applicationSupportDirectory)
    private val driver: SqlDriver = IosStorageDriverFactory(
        databaseLocation,
    ).createDriver()
    private val settings = IosCoreSettingsStore()
    private val repositories = StorageCoreRepositoryCatalog(
        database = NaviampStorageDatabase(driver),
        credentialProtector = credentialProtector,
        nowEpochMillis = IosClock::nowEpochMillis,
        databaseLabel = "${databaseLocation.directoryPath}/${databaseLocation.fileName}",
        clearAudioCacheFiles = {},
        clearDownloadFiles = {},
    )
    private val sessions = NavidromeCoreProviderSessionPort(
        mediaSources = repositories.mediaSources,
        initialSource = repositories.mediaSources.latestMediaSource(),
        sessionOpener = navidromeProviderSessionOpener(
            cacheMaintenanceRepository = repositories.maintenance,
            providerMediaSourceRepository = repositories.mediaSources,
            nowEpochMillis = IosClock::nowEpochMillis,
        ),
    )
    private val playback = unavailableNaviampCorePlaybackServices(
        persistSettings = settings::savePlayback,
        sessions = repositories.playbackSessions,
    )
    private val storedCatalog = naviampCoreStoredServiceCatalog(
        providerSessions = sessions,
        providerSource = sessions.providerSource,
        playback = playback,
        downloads = unavailableNaviampCoreDownloadServices(),
        playbackEngine = app.naviamp.presentation.UnavailableNaviampPlaybackEngine,
        settingsSyncPort = unavailableNaviampCoreSettingsSyncServices(IosClock::nowEpochMillis).port,
        settings = settings.storedSettings(),
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
        homeDate = IosClock.homeDate,
        shellCapabilities = IosCapabilityPresentation.shell,
        settingsSyncDeviceId = "ios",
        sourceId = { repositories.mediaSources.latestMediaSource()?.id },
        clockEpochMillis = IosClock::nowEpochMillis,
        favoritedAtIso8601 = IosClock::nowIso8601,
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
        driver.close()
    }
}
