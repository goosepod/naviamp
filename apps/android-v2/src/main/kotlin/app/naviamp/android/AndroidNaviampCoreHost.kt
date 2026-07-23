package app.naviamp.android

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreHost
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.NaviampCoreServices
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.ui.NaviampApplicationUpdateChecker

/**
 * Complete input boundary for the replacement Android Activity host.
 *
 * Product state, actions, routes, menus, and feature controllers are deliberately absent. Android
 * supplies only implementations of Core service contracts plus launch/lifecycle intent facts.
 */
internal typealias AndroidNaviampCoreEnvironment = NaviampCoreEnvironment

internal fun androidNaviampCoreEnvironment(
    services: NaviampCoreServices,
    initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
    applicationUpdateChecker: NaviampApplicationUpdateChecker? = null,
    onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Android Core command failed: $command", cause)
    },
): AndroidNaviampCoreEnvironment = NaviampCoreEnvironment(
    services = services,
    initialState = initialState,
    actionAvailability = AndroidCapabilityPresentation.toCoreActionAvailability(),
    applicationUpdateChecker = applicationUpdateChecker,
    onAsyncFailure = onAsyncFailure,
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
    NaviampCoreHost(
        environment = environment,
        modifier = modifier,
    )
}
