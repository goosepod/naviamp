package app.naviamp.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.naviamp.ui.NaviampLocaleEnvironment
import app.naviamp.ui.createNaviampLocaleEffect
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampApplicationSurface
import app.naviamp.ui.LocalNaviampApplicationSurface
import app.naviamp.ui.NaviampBusyDialog
import app.naviamp.ui.NaviampCastOutputUi
import app.naviamp.ui.defaultNaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampDiagnosticsUi
import app.naviamp.ui.NaviampSharedAppShell
import app.naviamp.ui.NaviampStatsForNerdsDialog
import app.naviamp.ui.NaviampTelevisionAppShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import app.naviamp.app.NaviampPlaybackOutputSelection
import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.app.NaviampRemoteOutputPhase

/**
 * Complete, host-neutral input boundary for the Naviamp product.
 *
 * Platform clients may provide implementations of service contracts and native capability facts,
 * but they do not get a separate product composition model.
 */
data class NaviampCoreEnvironment(
    val services: NaviampCoreServices,
    val initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
    val actionAvailability: NaviampCoreActionAvailability = NaviampCoreActionAvailability(),
    val applicationUpdateChecker: NaviampApplicationUpdateChecker? = defaultNaviampApplicationUpdateChecker(),
    val onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Core command failed: $command", cause)
    },
)

/** Constructs the complete product for non-Compose hosts and tests. */
fun createNaviampCore(
    scope: CoroutineScope,
    environment: NaviampCoreEnvironment,
): NaviampCore = NaviampCore.create(
    scope = scope,
    services = environment.services,
    initialState = environment.initialState,
    actionAvailability = environment.actionAvailability,
    onAsyncFailure = environment.onAsyncFailure,
)

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
    val core = remember(scope, services, initialState, actionAvailability, onAsyncFailure) {
        NaviampCore.create(
            scope = scope,
            services = services,
            initialState = initialState,
            actionAvailability = actionAvailability,
            onAsyncFailure = onAsyncFailure,
        )
    }
    DisposableEffect(core) {
        onDispose(core::close)
    }
    return core
}

/** Renders a borrowed Core; its creator owns cleanup, independently of window lifetime. */
@Composable
fun NaviampCoreApp(
    core: NaviampCore,
    modifier: Modifier = Modifier,
    applicationSurface: NaviampApplicationSurface = NaviampApplicationSurface.Standard,
    screenAwakeEffect: app.naviamp.app.NaviampScreenAwakeEffect? = null,
    visualizerBandsProvider: () -> List<Float> = {
        core.state.value.shell.nowPlaying?.visualizerFrame?.bands.orEmpty()
    },
    applicationUpdateChecker: NaviampApplicationUpdateChecker? = null,
    statsForNerdsPresenter: @Composable (NaviampDiagnosticsUi, () -> Unit) -> Unit = { diagnostics, close ->
        NaviampStatsForNerdsDialog(diagnostics, close)
    },
) {
    val state by core.state.collectAsState()
    val playbackOutput by core.playbackOutputs.state.collectAsState()
    val selectedCast = (playbackOutput as? NaviampPlaybackOutputSelection.Remote)
        ?.takeIf { it.target.kind == NaviampRemoteOutputKind.Cast }
    val castOutput = NaviampCastOutputUi(
        available = core.castAvailable,
        selectedTargetName = selectedCast?.target?.displayName,
        selected = selectedCast != null,
        playbackActive = selectedCast?.playbackAuthorityActive == true,
        unavailable = selectedCast?.phase == NaviampRemoteOutputPhase.Unavailable,
    )
    var diagnosticsRefreshTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(core, state.shell.connectionSettings.currentSourceId) {
        if (state.shell.connectionSettings.currentSourceId != null) {
            core.maintainProviderSession()
        }
    }
    NaviampLocaleEnvironment(state.shell.general.interfaceSettings.language, remember { createNaviampLocaleEffect() }) {
        NaviampScreenAwakeEnvironment(screenAwakeEffect, state.shell.general.interfaceSettings.keepScreenAwake) {
            CompositionLocalProvider(LocalNaviampApplicationSurface provides applicationSurface) {
                when (applicationSurface) {
                    NaviampApplicationSurface.Standard -> NaviampSharedAppShell(
                        modifier = modifier,
                        uiState = state.shell,
                        settingsSync = state.settingsSync,
                        playbackProgress = core.playbackProgress,
                        visualizerBandsProvider = visualizerBandsProvider,
                        actions = core.actions.shell,
                        syncActions = core.actions.settingsSync,
                        applicationUpdateChecker = applicationUpdateChecker,
                        castOutput = castOutput,
                        onCastPicker = core::showCastPicker,
                        onCastSelectLocal = core::selectLocalPlayback,
                    )
                    NaviampApplicationSurface.Television -> NaviampTelevisionAppShell(
                        modifier = modifier,
                        uiState = state.shell,
                        settingsSync = state.settingsSync,
                        playbackProgress = core.playbackProgress,
                        visualizerBandsProvider = visualizerBandsProvider,
                        actions = core.actions.shell,
                        syncActions = core.actions.settingsSync,
                    )
                }
                state.overlays.busyMessage?.let { message ->
                    NaviampBusyDialog(message)
                }
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
        }
    }
}

/** The single product surface mounted unchanged by every thin platform client. */
@Composable
fun NaviampCoreHost(
    environment: NaviampCoreEnvironment,
    modifier: Modifier = Modifier,
    statsForNerdsPresenter: @Composable (NaviampDiagnosticsUi, () -> Unit) -> Unit = { diagnostics, close ->
        NaviampStatsForNerdsDialog(diagnostics, close)
    },
) {
    val core = rememberNaviampCore(
        services = environment.services,
        initialState = environment.initialState,
        actionAvailability = environment.actionAvailability,
        onAsyncFailure = environment.onAsyncFailure,
    )
    NaviampCoreApp(
        core = core,
        modifier = modifier,
        applicationUpdateChecker = environment.applicationUpdateChecker,
        statsForNerdsPresenter = statsForNerdsPresenter,
    )
}
