package app.naviamp.android

import androidx.compose.runtime.Composable
import app.naviamp.ui.NaviampAppShellActions
import app.naviamp.ui.NaviampSettingsSyncActions
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.NaviampSharedAppShell

@Composable
fun AndroidAppShellContent(
    state: AndroidAppShellUiState,
    actions: NaviampAppShellActions,
    settingsSync: NaviampSettingsSyncUi = NaviampSettingsSyncUi(),
    syncActions: NaviampSettingsSyncActions = NaviampSettingsSyncActions(),
) {
    NaviampSharedAppShell(
        modifier = state.modifier,
        uiState = state.presentation,
        settingsSync = settingsSync,
        visualizerBandsProvider = state.visualizerBandsProvider,
        actions = actions,
        syncActions = syncActions,
    )
}
