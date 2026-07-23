package app.naviamp.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.domain.app.NaviampNavigationState
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
 * Complete input boundary for the replacement Android Activity host.
 *
 * Product state, actions, routes, menus, and feature controllers are deliberately absent. Android
 * supplies only implementations of Core service contracts plus launch/lifecycle intent facts.
 */
internal data class AndroidNaviampCoreEnvironment(
    val services: NaviampCoreServices,
    val initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
    val actionAvailability: NaviampCoreActionAvailability =
        AndroidCapabilityPresentation.toCoreActionAvailability(),
    val applicationUpdateChecker: NaviampApplicationUpdateChecker? = null,
    val onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Android Core command failed: $command", cause)
    },
)

internal fun createAndroidNaviampCore(
    scope: CoroutineScope,
    environment: AndroidNaviampCoreEnvironment,
): NaviampCore = NaviampCore.create(
    scope = scope,
    services = environment.services,
    initialState = environment.initialState,
    actionAvailability = environment.actionAvailability,
    onAsyncFailure = environment.onAsyncFailure,
)

internal fun AndroidNaviampCoreEnvironment.withNavigation(
    navigation: NaviampNavigationState,
): AndroidNaviampCoreEnvironment = copy(
    initialState = initialState.copy(navigation = navigation),
)

/** The replacement Android product surface: construct Core once and mount shared UI unchanged. */
@Composable
internal fun AndroidNaviampCoreHost(
    environment: AndroidNaviampCoreEnvironment,
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
