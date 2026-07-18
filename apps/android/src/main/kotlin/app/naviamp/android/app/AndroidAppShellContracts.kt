package app.naviamp.android

import androidx.compose.ui.Modifier
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampDownloadsScreenUi
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.NaviampStorageLocationUi
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampPlaylistChoiceUi
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.NaviampAboutUi
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.NaviampHomeScreenUi
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampPlaylistsScreenUi
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampInternetRadioScreenUi
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistDetailActions

data class AndroidAppShellUiState(
    val modifier: Modifier,
    val connection: NaviampShellConnectionUi,
    val interfaceSettings: InterfaceSettings,
    val playbackSettings: PlaybackSettings,
    val cacheSettings: CacheSettings,
    val diagnostics: NaviampDiagnosticsUi,
    val about: NaviampAboutUi,
    val capabilities: NaviampShellCapabilitiesUi,
    val selectedVisualizer: NaviampVisualizer,
    val visualizerBandsProvider: () -> List<Float>,
    val search: NaviampSearchScreenUi,
    val home: NaviampHomeScreenUi,
    val artistMixBuilder: SharedArtistMixBuilderUi,
    val albumMixBuilder: SharedAlbumMixBuilderUi,
    val genreMixBuilder: SharedGenreMixBuilderUi,
    val sonicPathBuilder: SharedSonicPathBuilderUi,
    val sonicMixBuilder: SharedSonicMixBuilderUi,
    val library: NaviampLibraryScreenUi,
    val downloads: NaviampDownloadsScreenUi,
    val downloadLocations: List<NaviampStorageLocationUi>,
    val audioCacheLocations: List<NaviampStorageLocationUi>,
    val selectedDownloadLocationId: String?,
    val selectedAudioCacheLocationId: String?,
    val playlists: NaviampPlaylistsScreenUi,
    val playlistChoices: List<NaviampPlaylistChoiceUi>,
    val radio: NaviampInternetRadioScreenUi,
    val albumDetail: NaviampAlbumDetailScreenUi,
    val artistDetail: NaviampArtistDetailScreenUi,
    val playlistDetail: NaviampPlaylistDetailScreenUi,
    val nowPlaying: NowPlayingUi?,
    val nowPlayingOpen: Boolean,
    val selectedRoute: SharedRoute,
)

data class AndroidAppShellActions(
    val onRouteSelected: (SharedRoute) -> Unit,
    val connectionActions: NaviampConnectionSettingsActions,
    val valueActions: NaviampSettingsValueActions,
    val maintenanceActions: NaviampSettingsMaintenanceActions,
    val searchActions: NaviampSearchActions,
    val artistMixActions: SharedArtistMixBuilderActions,
    val albumMixActions: SharedAlbumMixBuilderActions,
    val genreMixActions: SharedGenreMixBuilderActions,
    val sonicPathActions: SharedSonicPathBuilderActions,
    val sonicMixActions: SharedSonicMixBuilderActions,
    val downloadsActions: NaviampDownloadsActions,
    val libraryActions: NaviampLibraryActions,
    val playlistsActions: NaviampPlaylistsActions,
    val radioActions: NaviampInternetRadioActions,
    val albumDetailActions: NaviampAlbumDetailActions,
    val artistDetailActions: NaviampArtistDetailActions,
    val playlistDetailActions: NaviampPlaylistDetailActions,
    val onRefreshHome: () -> Unit,
    val onTrackSelected: (SharedTrackRowUi) -> Unit,
    val onAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    val onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    val onArtistSelected: (SharedMediaItemUi) -> Unit,
    val onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit,
    val onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    val onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    val onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    val onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    val onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    val onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    val onMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    val onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    val onSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
    val onOpenNowPlaying: () -> Unit,
    val onCloseNowPlaying: () -> Unit,
    val nowPlayingActions: NaviampNowPlayingActions,
)
