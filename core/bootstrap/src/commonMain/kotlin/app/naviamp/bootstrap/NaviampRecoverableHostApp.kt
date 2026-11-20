package app.naviamp.bootstrap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.naviamp.ui.NaviampRecoveryFailureScreen

/** The same ready/failure/reset flow is available to every thin native host. */
@Composable
fun <T> NaviampRecoverableHostApp(
    controller: NaviampRecoverableHostController<T>,
    content: @Composable (T) -> Unit,
) {
    val state by controller.state.collectAsState()
    LaunchedEffect(controller) { controller.start() }
    val app = state.application
    if (app != null) content(app)
    if (app == null || state.failure != null || state.confirmReset) {
        NaviampRecoveryFailureScreen(
            failure = state.failure, starting = state.starting, overlay = app != null,
            confirmReset = state.confirmReset, resetAvailable = controller.resetAvailable,
            onRetry = controller::retry, onDismiss = controller::dismissFailure,
            onRequestReset = controller::requestReset, onConfirmReset = controller::confirmReset, onCancelReset = controller::cancelReset,
        )
    }
}
