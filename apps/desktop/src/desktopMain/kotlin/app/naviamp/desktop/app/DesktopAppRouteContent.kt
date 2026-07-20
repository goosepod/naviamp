package app.naviamp.desktop

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampProductRouteContent
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi

@Composable
fun ColumnScope.DesktopRouteContent(
    shellState: NaviampAppShellUiState,
    shellActions: NaviampAppShellActions,
    appColors: DesktopAppColors,
    appRoute: NaviampRoute,
    libraryListState: LazyListState,
    settingsSync: NaviampSettingsSyncUi,
    settingsSyncActions: NaviampSettingsSyncActions,
) {
    NaviampProductRouteContent(
        shellState = shellState,
        shellActions = shellActions,
        colors = appColors,
        appRoute = appRoute,
        libraryListState = libraryListState,
        settingsContent = {
            DesktopSettingsPanel(
                appColors = appColors,
                connectionSettings = shellState.connectionSettings,
                general = shellState.general,
                playback = shellState.playback,
                cache = shellState.cache,
                settingsSync = settingsSync,
                connectionActions = shellActions.connectionActions,
                syncActions = settingsSyncActions,
                valueActions = shellActions.valueActions,
                maintenanceActions = shellActions.maintenanceActions,
            )
        },
    )
}
