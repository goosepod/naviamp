package app.naviamp.android

import androidx.compose.runtime.Composable
import app.naviamp.ui.NaviampCacheSettingsUi
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.toConnectionSettingsUi
import app.naviamp.ui.toGeneralSettingsUi
import app.naviamp.ui.toPlaybackSettingsUi

@Composable
fun AndroidAppShellContent(
    state: AndroidAppShellUiState,
    actions: AndroidAppShellActions,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    syncActions: NaviampSettingsSyncActions = NaviampSettingsSyncActions(),
) {
    NaviampSharedAppShell(
        modifier = state.modifier,
        connectionSettings = state.connection.toConnectionSettingsUi(state.capabilities),
        general = state.interfaceSettings.toGeneralSettingsUi(state.about),
        playback = state.playbackSettings.toPlaybackSettingsUi(
            capabilities = state.capabilities,
            downloadBytes = state.downloads.downloadBytes,
        ),
        cache = NaviampCacheSettingsUi(
            settings = state.cacheSettings,
            diagnostics = state.diagnostics,
            fileSelectionAvailable = state.capabilities.fileSelection,
            downloadLocations = state.downloadLocations,
            audioCacheLocations = state.audioCacheLocations,
            selectedDownloadLocationId = state.selectedDownloadLocationId,
            selectedAudioCacheLocationId = state.selectedAudioCacheLocationId,
        ),
        settingsSync = settingsSync,
        supportsDownloads = state.capabilities.downloads,
        supportsApplicationUpdates = state.capabilities.applicationUpdates,
        selectedVisualizer = state.selectedVisualizer,
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
        nowPlayingOpen = state.nowPlayingOpen,
        selectedRoute = state.selectedRoute,
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
