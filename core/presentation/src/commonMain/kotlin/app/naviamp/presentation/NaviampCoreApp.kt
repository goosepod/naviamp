package app.naviamp.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampSharedAppShell

/** The one product UI entry mounted unchanged by Android, Desktop, iOS, and fake hosts. */
@Composable
fun NaviampCoreApp(
    core: NaviampCore,
    modifier: Modifier = Modifier,
    visualizerBandsProvider: () -> List<Float> = {
        core.state.value.shell.nowPlaying?.visualizerFrame?.bands.orEmpty()
    },
    applicationUpdateChecker: NaviampApplicationUpdateChecker? = null,
) {
    val state by core.state.collectAsState()
    NaviampSharedAppShell(
        modifier = modifier,
        uiState = state.shell,
        settingsSync = state.settingsSync,
        visualizerBandsProvider = visualizerBandsProvider,
        actions = core.actions.shell,
        syncActions = core.actions.settingsSync,
        applicationUpdateChecker = applicationUpdateChecker,
    )
}
