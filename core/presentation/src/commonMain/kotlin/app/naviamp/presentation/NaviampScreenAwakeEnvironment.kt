package app.naviamp.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.naviamp.app.NaviampScreenAwakeController
import app.naviamp.app.NaviampScreenAwakeEffect
import app.naviamp.app.NaviampScreenAwakeStatus
import app.naviamp.ui.LocalNaviampScreenAwakeUi
import app.naviamp.ui.NaviampScreenAwakeUi
import app.naviamp.ui.naviampScreenAwakeReason

/** Common lifecycle mapping: a visible surface retains its display lease even when it loses focus. */
@Composable
internal fun NaviampScreenAwakeEnvironment(
    effect: NaviampScreenAwakeEffect?,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember(effect, lifecycle) { NaviampScreenAwakeController(effect) }
    val status by controller.status.collectAsState()
    val reason = naviampScreenAwakeReason()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentReason by rememberUpdatedState(reason)
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            // Release directly on STOP; background compositions may no longer receive frames.
            controller.update(currentEnabled, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), currentReason)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.close()
        }
    }
    SideEffect { controller.update(enabled, lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), reason) }
    CompositionLocalProvider(LocalNaviampScreenAwakeUi provides NaviampScreenAwakeUi(
        available = effect != null, failed = status == NaviampScreenAwakeStatus.Failed,
    ), content = content)
}
