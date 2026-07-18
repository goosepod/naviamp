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
import app.naviamp.ui.NaviampAboutUi
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.SharedAlbumMixBuilderUi
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderUi
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderUi
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.NaviampHomeScreenUi
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.NaviampShellChromeUi
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicMixBuilderUi
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderUi
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
    val shellChrome: NaviampShellChromeUi,
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
)

data class AndroidAppShellActions(
    val navigationActions: NaviampShellNavigationActions,
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
    val homeActions: NaviampHomeActions,
    val mediaActions: NaviampMediaActions,
    val nowPlayingActions: NaviampNowPlayingActions,
)
