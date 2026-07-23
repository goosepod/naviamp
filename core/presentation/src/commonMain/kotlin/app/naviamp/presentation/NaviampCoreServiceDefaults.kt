package app.naviamp.presentation

import app.naviamp.app.NaviampKeepDownloadedReconciliationApplication
import app.naviamp.app.NaviampKeepDownloadedToggleResult
import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Genre
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.KeepDownloadedCollectionPolicy
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState

/**
 * Optional-effect defaults for a production Core host while native persistence families are wired.
 *
 * These are deliberately Core-owned because the meaning of an unavailable optional feature must
 * not be reimplemented by each platform. Required connection, playback, and settings-sync effects
 * remain explicit inputs. Defaults never fabricate provider content or native capabilities.
 */
fun naviampCoreServiceDefaults(
    providerSource: NaviampCoreMediaProviderSource,
    connection: NaviampCoreProviderSessionPort,
    playback: NaviampCorePlaybackServices,
    settingsSync: NaviampCoreSettingsSyncServices,
    externalUri: NaviampCoreExternalUriPort,
    homeDate: NaviampCoreHomeDateSource = NaviampCoreHomeDateSource { HomeDate(2026, 1) },
    sourceId: () -> String? = { providerSource.current()?.cacheNamespace },
    libraryIndex: LocalLibraryIndexRepository? = null,
    clockEpochMillis: () -> Long,
    favoritedAtIso8601: () -> String,
): NaviampCoreServices = NaviampCoreServices(
    content = NaviampCoreContentServices(
        providerSource = providerSource,
        homeDate = homeDate,
        homeSupplement = NaviampCoreHomeSupplementSource { NaviampCoreHomeSupplement() },
        playlistSupplement = NaviampCorePlaylistBrowseSupplementSource {
            NaviampCorePlaylistBrowseSupplement()
        },
        artistDiscovery = NaviampCoreArtistDiscoveryServices(),
        sonicHomeDiscovery = libraryIndex?.let(::naviampCoreSonicHomeDiscoverySource),
        externalUri = externalUri,
    ),
    connection = connection,
    settings = NaviampCoreSettingsServices(
        interfaceSettings = NaviampCoreInterfaceSettingsStore {},
        cacheSettings = NaviampCoreCacheSettingsPort { it.normalized() },
        maintenance = NaviampCoreMaintenancePort { NaviampCoreMaintenanceResult("complete") },
        sync = settingsSync,
    ),
    downloads = NaviampCoreDownloadServices(
        storage = object : NaviampCoreDownloadStoragePort {
            override suspend fun snapshot(sourceId: String) = NaviampCoreDownloadStorageSnapshot()
            override suspend fun pruneMissing(sourceId: String) = 0
            override suspend fun remove(sourceId: String, track: Track) = Unit
            override suspend fun deleteAll(sourceId: String) = 0
        },
        transfer = NaviampCoreDownloadTransferPort { _, _, update ->
            update(DownloadJobUpdate.Failed(null, "Downloads are not connected to this host yet."))
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        },
        keepDownloaded = object : NaviampCoreKeepDownloadedPort {
            override fun policies(sourceId: String) = emptyList<KeepDownloadedCollectionPolicy>()
            override fun toggle(policy: KeepDownloadedCollectionPolicy) =
                NaviampKeepDownloadedToggleResult.Enable
            override fun reconcile(policy: KeepDownloadedCollectionPolicy, tracks: List<Track>) =
                NaviampKeepDownloadedReconciliationApplication(emptyList(), null, null, false)
        },
        playback = NaviampCoreDownloadedPlaybackPort { _, _ -> },
        network = NaviampCoreMobileNetworkPort { false },
    ),
    playlists = NaviampCorePlaylistServices(
        history = NaviampCorePlaylistHistoryPort { current, _ -> current },
    ),
    radio = NaviampCoreRadioServices(
        playback = playback.effects as? NaviampCoreInternetRadioPlaybackPort
            ?: NaviampCoreInternetRadioPlaybackPort {
                error("Internet radio playback is not connected to this playback engine.")
            },
        recents = object : NaviampCoreInternetRadioRecentsPort {
            override fun current() = emptyList<InternetRadioStation>()
            override suspend fun record(station: InternetRadioStation) = listOf(station)
        },
        generatedRecents = NaviampCoreGeneratedRadioRecentsPort(
            load = { emptyList() },
            save = {},
        ),
    ),
    mixes = naviampCoreStandardMixServices(
        providerSource = providerSource,
        sourceId = sourceId,
        libraryIndex = libraryIndex,
        nowEpochMillis = clockEpochMillis,
    ),
    playback = playback,
    clockEpochMillis = clockEpochMillis,
    favoritedAtIso8601 = favoritedAtIso8601,
)

/** Core-owned unavailable implementation for hosts that have not connected document picking yet. */
fun unavailableNaviampCoreSettingsSyncServices(
    nowEpochMillis: () -> Long,
): NaviampCoreSettingsSyncServices {
    var runtime = SettingsSyncRuntimeState()
    return NaviampCoreSettingsSyncServices(
        controller = app.naviamp.app.NaviampSettingsSyncController(
            deviceId = "naviamp-core",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = nowEpochMillis,
            snapshot = { SettingsSyncLocalSnapshot() },
            applyDocument = {},
        ),
        port = object : NaviampCoreSettingsSyncPort {
            override fun configuration() = NaviampCoreSettingsSyncConfiguration()
            override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) = Unit
            override suspend fun readDocument(directoryPath: String) = null
            override suspend fun readDocumentFile(filePath: String) = null
            override suspend fun writeDocument(
                directoryPath: String,
                document: app.naviamp.domain.settings.SettingsSyncDocument,
            ) = error("Settings sync is not available on this host.")
            override suspend fun chooseDirectory(currentPath: String?, title: String): String? = null
            override suspend fun chooseDocument(currentPath: String?, title: String): String? = null
            override fun defaultDirectory(): String = ""
            override val available = false
        },
    )
}
