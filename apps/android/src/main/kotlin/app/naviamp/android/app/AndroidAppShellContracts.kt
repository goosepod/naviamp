package app.naviamp.android

import androidx.compose.ui.Modifier
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.smartplaylist.SmartPlaylistDefinition
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
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.NowPlayingDisplayActionRequest
import app.naviamp.ui.NowPlayingItemActionRequest
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.NowPlayingSelectionActionRequest
import app.naviamp.ui.NowPlayingSleepTimerActionRequest
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedAlbumDetailUi
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistDetailUi
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.SharedHomeDiscoveryTrackActionRequest
import app.naviamp.ui.SharedHomeStationUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedMediaItemActionRequest
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.SharedMixBuilderUi
import app.naviamp.ui.SharedPlaylistDetailUi
import app.naviamp.ui.SharedPlaylistSortMode
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedSimilarArtistUi
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderUi
import app.naviamp.ui.StationRowActionRequest
import app.naviamp.ui.SharedTrackRowActionRequest
import app.naviamp.ui.SharedTrackRowUi
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.NaviampShellConnectionUi
import app.naviamp.ui.NaviampLibraryScreenUi
import app.naviamp.ui.NaviampSearchScreenUi

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
    val home: SharedHomeUi,
    val homeRefreshing: Boolean,
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
    val playlistItems: List<SharedMediaItemUi>,
    val recentPlaylistIds: List<String>,
    val playlistSortMode: SharedPlaylistSortMode,
    val playlistChoices: List<NaviampPlaylistChoiceUi>,
    val playlistActionStatus: String?,
    val playlistRefreshing: Boolean,
    val radioStations: List<InternetRadioStation>,
    val radioRefreshing: Boolean,
    val albumDetail: SharedAlbumDetailUi?,
    val artistDetail: SharedArtistDetailUi?,
    val playlistDetail: SharedPlaylistDetailUi?,
    val nowPlaying: NowPlayingUi?,
    val nowPlayingOpen: Boolean,
    val selectedRoute: SharedRoute,
)

data class AndroidAppShellActions(
    val onRouteSelected: (SharedRoute) -> Unit,
    val connectionActions: NaviampConnectionSettingsActions,
    val valueActions: NaviampSettingsValueActions,
    val maintenanceActions: NaviampSettingsMaintenanceActions,
    val onQueryChanged: (String) -> Unit,
    val onSearch: () -> Unit,
    val onClearSearch: () -> Unit,
    val artistMixActions: SharedArtistMixBuilderActions,
    val albumMixActions: SharedAlbumMixBuilderActions,
    val genreMixActions: SharedGenreMixBuilderActions,
    val sonicPathActions: SharedSonicPathBuilderActions,
    val sonicMixActions: SharedSonicMixBuilderActions,
    val downloadsActions: NaviampDownloadsActions,
    val onLibraryQueryChanged: (String) -> Unit,
    val onRefreshHome: () -> Unit,
    val onRefreshLibrary: () -> Unit,
    val onLoadMoreLibrary: () -> Unit,
    val onRefreshPlaylists: () -> Unit,
    val onRefreshRadioStations: () -> Unit,
    val onTrackSelected: (SharedTrackRowUi) -> Unit,
    val onAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumFavoriteToggled: (SharedMediaItemUi) -> Unit,
    val onMixAlbumSelected: (SharedMediaItemUi) -> Unit,
    val onAlbumPlay: (SharedAlbumDetailUi, Boolean) -> Unit,
    val onAlbumTrackSelected: (SharedTrackRowUi) -> Unit,
    val onAlbumRadio: (SharedAlbumDetailUi) -> Unit,
    val onAlbumAddToQueue: (SharedAlbumDetailUi) -> Unit,
    val onAlbumDownload: (SharedAlbumDetailUi) -> Unit,
    val onAlbumAddToPlaylist: (SharedAlbumDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onAlbumCreatePlaylistAndAdd: (SharedAlbumDetailUi, String) -> Unit,
    val onTrackAction: (SharedTrackRowActionRequest) -> Unit,
    val onArtistRadio: (SharedArtistDetailUi) -> Unit,
    val onArtistPlay: (SharedArtistDetailUi) -> Unit,
    val onArtistShuffle: (SharedArtistDetailUi) -> Unit,
    val onArtistAddToQueue: (SharedArtistDetailUi) -> Unit,
    val onArtistAddToPlaylist: (SharedArtistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onArtistCreatePlaylistAndAdd: (SharedArtistDetailUi, String) -> Unit,
    val onArtistPopularPlay: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularRadio: (SharedArtistDetailUi) -> Unit,
    val onArtistPopularTrackSelected: (SharedTrackRowUi) -> Unit,
    val onArtistPopularAddToQueue: (SharedArtistDetailUi) -> Unit,
    val onFindSimilarArtists: (SharedArtistDetailUi) -> Unit,
    val onSimilarArtistSelected: (SharedSimilarArtistUi) -> Unit,
    val onSimilarArtistExternalSelected: (String) -> Unit,
    val onArtistSelected: (SharedMediaItemUi) -> Unit,
    val onArtistFavoriteToggled: (SharedMediaItemUi) -> Unit,
    val onPlaylistSelected: (SharedMediaItemUi) -> Unit,
    val onPlaylistSortModeChanged: (SharedPlaylistSortMode) -> Unit,
    val onPlaylistPlay: (SharedMediaItemUi, Boolean) -> Unit,
    val onPlaylistAddToQueue: (SharedPlaylistDetailUi) -> Unit,
    val onPlaylistAddToPlaylist: (SharedPlaylistDetailUi, NaviampPlaylistChoiceUi?) -> Unit,
    val onPlaylistCreatePlaylistAndAdd: (SharedPlaylistDetailUi, String) -> Unit,
    val onPlaylistCopy: (SharedPlaylistDetailUi, String, Boolean) -> Unit,
    val onPlaylistRename: (SharedMediaItemUi, String) -> Unit,
    val onPlaylistDelete: (SharedMediaItemUi) -> Unit,
    val onStandardPlaylistUpdate: suspend (SharedMediaItemUi, List<SharedTrackRowUi>) -> Unit,
    val onMediaItemAction: (SharedMediaItemActionRequest) -> Unit,
    val onSmartPlaylistSave: suspend (SmartPlaylistDefinition) -> Unit,
    val onSmartPlaylistUpdate: suspend (SharedMediaItemUi, SmartPlaylistDefinition) -> Unit,
    val onSmartPlaylistSaveWithPassword: suspend (SmartPlaylistDefinition, String) -> Unit,
    val onSmartPlaylistUpdateWithPassword: suspend (SharedMediaItemUi, SmartPlaylistDefinition, String) -> Unit,
    val onSmartPlaylistLoad: suspend (SharedMediaItemUi) -> SmartPlaylistDefinition,
    val onPlaylistBack: () -> Unit,
    val onPlaylistTrackSelected: (SharedTrackRowUi) -> Unit,
    val onRecentRadioSelected: (SharedMediaItemUi) -> Unit,
    val onMixBuilderSelected: (SharedMixBuilderUi) -> Unit,
    val onRadioStationSelected: (InternetRadioStation) -> Unit,
    val onRadioStationSave: (InternetRadioStation) -> Unit,
    val onStationAction: (StationRowActionRequest) -> Unit,
    val onHomeStationSelected: (SharedHomeStationUi) -> Unit,
    val onSonicDiscoveryTrackAction: (SharedHomeDiscoveryTrackActionRequest) -> Unit,
    val onOpenNowPlaying: () -> Unit,
    val onCloseNowPlaying: () -> Unit,
    val onNowPlayingPlaybackAction: (NowPlayingPlaybackActionRequest) -> Unit,
    val onNowPlayingDisplayAction: (NowPlayingDisplayActionRequest) -> Unit,
    val onNowPlayingCurrentTrackAction: (NowPlayingCurrentTrackUiActionRequest) -> Unit,
    val onNowPlayingQueueAction: (NowPlayingQueueActionRequest) -> Unit,
    val onNowPlayingSleepTimerAction: (NowPlayingSleepTimerActionRequest) -> Unit,
    val onNowPlayingSelectionAction: (NowPlayingSelectionActionRequest) -> Unit,
    val onQueueItemAction: (NowPlayingItemActionRequest) -> Unit,
)
