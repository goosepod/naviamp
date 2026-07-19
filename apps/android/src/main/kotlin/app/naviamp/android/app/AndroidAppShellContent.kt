package app.naviamp.android

import androidx.compose.runtime.Composable
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampSharedAppShell

@Composable
fun AndroidAppShellContent(
    state: AndroidAppShellUiState,
    actions: AndroidAppShellActions,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    syncActions: NaviampSettingsSyncActions = NaviampSettingsSyncActions(),
) {
    NaviampSharedAppShell(
        modifier = state.modifier,
        uiState = state.presentation,
        settingsSync = settingsSync,
        visualizerBandsProvider = state.visualizerBandsProvider,
        navigationActions = actions.navigationActions,
        connectionActions = actions.connectionActions,
        syncActions = syncActions,
        valueActions = actions.valueActions,
        maintenanceActions = actions.maintenanceActions,
        searchActions = actions.searchActions,
        artistMixActions = actions.artistMixActions,
        albumMixActions = actions.albumMixActions,
        genreMixActions = actions.genreMixActions,
        sonicPathActions = actions.sonicPathActions,
        sonicMixActions = actions.sonicMixActions,
        downloadsActions = actions.downloadsActions,
        libraryActions = actions.libraryActions,
        playlistsActions = actions.playlistsActions,
        radioActions = actions.radioActions,
        albumDetailActions = actions.albumDetailActions,
        artistDetailActions = actions.artistDetailActions,
        playlistDetailActions = actions.playlistDetailActions,
        homeActions = actions.homeActions,
        mediaActions = actions.mediaActions,
        nowPlayingActions = actions.nowPlayingActions,
    )
}
