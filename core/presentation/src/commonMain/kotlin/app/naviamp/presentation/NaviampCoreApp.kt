package app.naviamp.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampDiagnosticsSectionUi
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampStatsForNerdsDialog

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
        val nowPlaying = state.shell.nowPlaying
        val coreSections = listOf(
            NaviampDiagnosticsSectionUi(
                title = "Application",
                rows = listOf(
                    "Route" to state.shell.shellChrome.selectedRoute.label,
                    "Connection" to (state.shell.connectionSettings.connection.status ?: "Not connected"),
                ),
            ),
            NaviampDiagnosticsSectionUi(
                title = "Playback",
                rows = listOf(
                    "State" to (nowPlaying?.stateLabel ?: "Idle"),
                    "Track" to (nowPlaying?.title ?: "None"),
                    "Artist" to (nowPlaying?.subtitle ?: "None"),
                    "Audio" to (nowPlaying?.audioInfo?.ifBlank { "Unknown" } ?: "None"),
                    "Position" to (nowPlaying?.positionSeconds?.let { "${it.toInt()}s" } ?: "Unknown"),
                    "Duration" to (nowPlaying?.durationSeconds?.let { "${it.toInt()}s" } ?: "Unknown"),
                    "Visualizer" to if (nowPlaying?.visualizerAvailable == true) "Available" else "Unavailable",
                    "Waveform" to if (nowPlaying?.waveform != null) "Loaded" else "Unavailable",
                    "Queue" to "${(nowPlaying?.backTo?.size ?: 0) + (nowPlaying?.upNext?.size ?: 0) + if (nowPlaying != null) 1 else 0} tracks",
                ),
            ),
        ) + core.playbackDiagnostics().takeIf { it.isNotEmpty() }?.let { rows ->
            listOf(NaviampDiagnosticsSectionUi("Playback engine", rows))
        }.orEmpty()
        NaviampStatsForNerdsDialog(
            diagnostics = NaviampDiagnosticsUi(coreSections + state.shell.cache.diagnostics.sections),
            onDismissRequest = { core.dispatch(NaviampCoreCommand.Settings.CloseStats) },
        )
    }
}
