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
        connectionSettings = state.connectionSettings,
        general = state.general,
        playback = state.playback,
        cache = state.cache,
        settingsSync = settingsSync,
        shellChrome = state.shellChrome,
        visualizerBandsProvider = state.visualizerBandsProvider,
        search = state.search,
        home = state.home,
        artistMixBuilder = state.artistMixBuilder,
        albumMixBuilder = state.albumMixBuilder,
        genreMixBuilder = state.genreMixBuilder,
        sonicPathBuilder = state.sonicPathBuilder,
        sonicMixBuilder = state.sonicMixBuilder,
        library = state.library,
        downloads = state.downloads,
        playlists = state.playlists,
        playlistChoices = state.playlistChoices,
        radio = state.radio,
        albumDetail = state.albumDetail,
        artistDetail = state.artistDetail,
        playlistDetail = state.playlistDetail,
        nowPlaying = state.nowPlaying,
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
