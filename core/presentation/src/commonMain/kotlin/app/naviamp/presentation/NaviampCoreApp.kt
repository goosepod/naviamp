package app.naviamp.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampStatsForNerdsDialog
import kotlinx.coroutines.delay

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
    statsForNerdsPresenter: @Composable (NaviampDiagnosticsUi, () -> Unit) -> Unit = { diagnostics, close ->
        NaviampStatsForNerdsDialog(diagnostics, close)
    },
) {
    val state by core.state.collectAsState()
    var diagnosticsRefreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(core, state.shell.connectionSettings.currentSourceId) {
        if (state.shell.connectionSettings.currentSourceId != null) {
            core.maintainProviderSession()
        }
    }
    NaviampSharedAppShell(
        modifier = modifier,
        uiState = state.shell,
        settingsSync = state.settingsSync,
        visualizerBandsProvider = visualizerBandsProvider,
        actions = core.actions.shell,
        syncActions = core.actions.settingsSync,
        applicationUpdateChecker = applicationUpdateChecker,
    )
    if (state.overlays.statsForNerdsVisible) {
        LaunchedEffect(core) {
            while (true) {
                delay(1_000)
                diagnosticsRefreshTick += 1
            }
        }
        statsForNerdsPresenter(
            diagnosticsRefreshTick.let { core.statsForNerdsDiagnostics() },
            { core.dispatch(NaviampCoreCommand.Settings.CloseStats) },
        )
    }
}
