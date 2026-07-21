package app.naviamp.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampSharedAppShell

/** Constructs the complete product once for a thin host composition. */
@Composable
fun rememberNaviampCore(
    services: NaviampCoreServices,
    initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
    actionAvailability: NaviampCoreActionAvailability = NaviampCoreActionAvailability(),
    onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Core command failed: $command", cause)
    },
): NaviampCore {
    val scope = rememberCoroutineScope()
    return remember(scope, services, initialState, actionAvailability, onAsyncFailure) {
        NaviampCore.create(
            scope = scope,
            services = services,
            initialState = initialState,
            actionAvailability = actionAvailability,
            onAsyncFailure = onAsyncFailure,
        )
    }
}

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
