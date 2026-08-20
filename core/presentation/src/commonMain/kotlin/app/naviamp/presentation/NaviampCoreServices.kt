package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampProviderActionController
import app.naviamp.domain.albummix.AlbumMixBuilderService
import app.naviamp.domain.artistmix.ArtistMixBuilderService
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.genremix.GenreMixBuilderService
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.playback.EmptyPlaybackProfileRepository
import app.naviamp.domain.playback.PlaybackProfileRepository
import app.naviamp.domain.settings.RecentRadioStream

/** Provider-backed product inputs that do not depend on an operating-system API. */
data class NaviampCoreContentServices(
    val providerSource: NaviampCoreMediaProviderSource,
    val homeDate: NaviampCoreHomeDateSource,
    val homeSupplement: NaviampCoreHomeSupplementSource,
    val playlistSupplement: NaviampCorePlaylistBrowseSupplementSource,
    val artistDiscovery: NaviampCoreArtistDiscoveryServices,
    val providerResponses: ProviderResponseService? = null,
    val homeLibrary: HomeLibraryRepository? = null,
    val sonicHomeDiscovery: NaviampCoreSonicHomeDiscoverySource? = null,
    val externalUri: NaviampCoreExternalUriPort,
    val libraryGenreRefresh: NaviampCoreLibraryGenreRefreshPort = NaviampCoreLibraryGenreRefreshPort { },
)

fun interface NaviampCoreLibraryGenreRefreshPort {
    suspend fun refresh()
}

/** Common services used to construct the three standard mix feature controllers. */
data class NaviampCoreMixServices(
    val artist: () -> ArtistMixBuilderService,
    val album: () -> AlbumMixBuilderService,
    val genre: () -> GenreMixBuilderService,
)

/** Persistence effects required by playlist product transactions. Queue ownership stays in Core. */
data class NaviampCorePlaylistServices(
    val history: NaviampCorePlaylistHistoryPort,
    val preview: NaviampCoreSmartPlaylistPreviewPort = NaviampCoreSmartPlaylistPreviewPort {
        app.naviamp.domain.smartplaylist.SmartPlaylistPreview(message = "Preview is unavailable.")
    },
)

/** Filesystem/network effects required by the Core-owned Downloads feature. */
data class NaviampCoreDownloadServices(
    val storage: NaviampCoreDownloadStoragePort,
    val transfer: NaviampCoreDownloadTransferPort,
    val keepDownloaded: NaviampCoreKeepDownloadedPort,
    val network: NaviampCoreMobileNetworkPort,
)

/** Native audio and sidecar effects behind the shared playback product. */
data class NaviampCorePlaybackServices(
    val effects: NaviampCorePlaybackEffectPort,
    val settings: NaviampCorePlaybackSettingsPort,
    val sidecars: NaviampCoreNowPlayingSidecarPort,
    val visualizerSettings: NaviampCoreVisualizerSettingsPort,
    val sessions: NaviampPlaybackSessionController,
    val profiles: PlaybackProfileRepository = EmptyPlaybackProfileRepository,
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
    val generatedRecents: NaviampCoreGeneratedRadioRecentsPort,
)

/** Portable persistence effects for Core-owned generated-radio recency policy. */
data class NaviampCoreGeneratedRadioRecentsPort(
    val load: () -> List<RecentRadioStream>,
    val save: (List<RecentRadioStream>) -> Unit,
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
    val providerActions: NaviampProviderActionController,
    val clockEpochMillis: () -> Long,
    val favoritedAtIso8601: () -> String,
    val diagnostics: NaviampCoreDiagnosticsPort = emptyNaviampCoreDiagnosticsPort(),
)
