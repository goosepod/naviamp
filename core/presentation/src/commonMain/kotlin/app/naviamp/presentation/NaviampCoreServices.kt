package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.home.HomeLibraryRepository

/** Provider-backed product inputs that do not depend on an operating-system API. */
data class NaviampCoreContentServices(
    val providerSource: NaviampCoreMediaProviderSource,
    val homeDate: NaviampCoreHomeDateSource,
    val homeSupplement: NaviampCoreHomeSupplementSource,
    val playlistSupplement: NaviampCorePlaylistBrowseSupplementSource,
    val artistDiscovery: NaviampCoreArtistDiscoveryServices,
    val providerResponses: ProviderResponseService? = null,
    val homeLibrary: HomeLibraryRepository? = null,
    val externalUri: NaviampCoreExternalUriPort,
)

/** Common services used to construct the three standard mix feature controllers. */
data class NaviampCoreMixServices(
    val artist: () -> ArtistMixBuilderService,
    val album: () -> AlbumMixBuilderService,
    val genre: () -> GenreMixBuilderService,
    val standardPlayback: NaviampCoreStandardMixPlaybackPort,
    val sonicPlayback: NaviampCoreSonicPlaybackPort,
    val sonicQueue: NaviampCoreSonicQueuePort,
)

/** Persistence and native effects required by playlist product transactions. */
data class NaviampCorePlaylistServices(
    val playback: NaviampCorePlaylistPlaybackPort,
    val queue: NaviampCorePlaylistQueuePort,
    val downloads: NaviampCorePlaylistDownloadPort,
    val history: NaviampCorePlaylistHistoryPort,
    val smartProviderSource: NaviampCoreSmartPlaylistProviderSource,
)

/** Filesystem/network effects required by the Core-owned Downloads feature. */
data class NaviampCoreDownloadServices(
    val storage: NaviampCoreDownloadStoragePort,
    val transfer: NaviampCoreDownloadTransferPort,
    val keepDownloaded: NaviampCoreKeepDownloadedPort,
    val playback: NaviampCoreDownloadedPlaybackPort,
    val network: NaviampCoreMobileNetworkPort,
)

/** Native audio and sidecar effects behind the shared playback product. */
data class NaviampCorePlaybackServices(
    val effects: NaviampCorePlaybackEffectPort,
    val settings: NaviampCorePlaybackSettingsPort,
    val sidecars: NaviampCoreNowPlayingSidecarPort,
    val visualizerSettings: NaviampCoreVisualizerSettingsPort,
    val sessions: NaviampPlaybackSessionController,
)

/** Settings stores and maintenance mechanisms; Core owns their ordering and presentation. */
data class NaviampCoreSettingsServices(
    val interfaceSettings: NaviampCoreInterfaceSettingsStore,
    val cacheSettings: NaviampCoreCacheSettingsPort,
    val maintenance: NaviampCoreMaintenancePort,
    val sync: NaviampCoreSettingsSyncServices,
)

/** Live-radio playback and recent-station persistence effects. */
data class NaviampCoreRadioServices(
    val playback: NaviampCoreInternetRadioPlaybackPort,
    val recents: NaviampCoreInternetRadioRecentsPort,
)

/**
 * Complete dependency catalog for [NaviampCore].
 *
 * The groups make native responsibilities explicit and keep the composition root readable. A host
 * supplies implementations; it does not assemble feature controllers or product action handlers.
 */
data class NaviampCoreServices(
    val content: NaviampCoreContentServices,
    val connection: NaviampCoreProviderSessionPort,
    val settings: NaviampCoreSettingsServices,
    val downloads: NaviampCoreDownloadServices,
    val playlists: NaviampCorePlaylistServices,
    val radio: NaviampCoreRadioServices,
    val mixes: NaviampCoreMixServices,
    val playback: NaviampCorePlaybackServices,
    val clockEpochMillis: () -> Long,
    val favoritedAtIso8601: () -> String,
)
