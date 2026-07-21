package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.presentation.NaviampCore
import app.naviamp.presentation.NaviampCoreActionAvailability
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.NaviampCoreServices
import app.naviamp.presentation.rememberNaviampCore
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.ui.NaviampApplicationUpdateChecker
import kotlinx.coroutines.CoroutineScope

/**
 * Complete input boundary for the replacement Desktop host.
 *
 * It contains Core services and genuine host integrations only. Product controllers, route state,
 * action factories, and screen models are deliberately unrepresentable here.
 */
internal data class DesktopNaviampCoreEnvironment(
    val services: NaviampCoreServices,
    val initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
    val actionAvailability: NaviampCoreActionAvailability =
        DesktopCapabilityPresentation.toCoreActionAvailability(),
    val applicationUpdateChecker: NaviampApplicationUpdateChecker? = null,
    val onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Desktop Core command failed: $command", cause)
    },
)

internal fun createDesktopNaviampCore(
    scope: CoroutineScope,
    environment: DesktopNaviampCoreEnvironment,
): NaviampCore = NaviampCore.create(
    scope = scope,
    services = environment.services,
    initialState = environment.initialState,
    actionAvailability = environment.actionAvailability,
    onAsyncFailure = environment.onAsyncFailure,
)

/** The replacement Desktop product surface: construct Core once and mount its shared app unchanged. */
@Composable
internal fun DesktopNaviampCoreHost(
    environment: DesktopNaviampCoreEnvironment,
    modifier: Modifier = Modifier,
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
    )
}
