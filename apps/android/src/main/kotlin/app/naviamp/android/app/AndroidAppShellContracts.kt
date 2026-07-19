package app.naviamp.android

import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampConnectionSettingsActions
import app.naviamp.ui.NaviampDownloadsActions
import app.naviamp.ui.NaviampSettingsMaintenanceActions
import app.naviamp.ui.NaviampSettingsValueActions
import app.naviamp.ui.NaviampNowPlayingActions
import app.naviamp.ui.SharedAlbumMixBuilderActions
import app.naviamp.ui.SharedArtistMixBuilderActions
import app.naviamp.ui.SharedGenreMixBuilderActions
import app.naviamp.ui.NaviampHomeActions
import app.naviamp.ui.NaviampMediaActions
import app.naviamp.ui.NaviampShellNavigationActions
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.SharedSonicMixBuilderActions
import app.naviamp.ui.SharedSonicPathBuilderActions
import app.naviamp.ui.NaviampLibraryActions
import app.naviamp.ui.NaviampSearchActions
import app.naviamp.ui.NaviampPlaylistsActions
import app.naviamp.ui.NaviampInternetRadioActions
import app.naviamp.ui.NaviampAlbumDetailActions
import app.naviamp.ui.NaviampArtistDetailActions
import app.naviamp.ui.NaviampPlaylistDetailActions

data class AndroidAppShellUiState(
    val modifier: Modifier,
    val presentation: NaviampAppShellUiState,
    val capabilities: NaviampShellCapabilitiesUi,
    val visualizerBandsProvider: () -> List<Float>,
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
