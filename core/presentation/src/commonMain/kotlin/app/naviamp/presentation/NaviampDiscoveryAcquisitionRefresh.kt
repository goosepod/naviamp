package app.naviamp.presentation

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Product visibility/lifecycle policy is shared; hosts only supply their normal lifecycle. */
@Composable
internal fun NaviampDiscoveryAcquisitionRefresh(core: NaviampCore, state: NaviampCoreState) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var started by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val discovery = state.shell.home.providerDiscovery
    val visible = state.shell.shellChrome.selectedRoute == app.naviamp.ui.SharedRoute.Home &&
        (discovery?.requestsOpen == true || discovery?.artistPage == true)
    LaunchedEffect(core, started, visible, discovery?.sourceGeneration, discovery?.requestsOpen, discovery?.artistDetail?.artist?.id,
        discovery?.releasePage?.release?.id, discovery?.artistDetail?.actionBusy) {
        if (started && visible && discovery?.artistDetail?.actionBusy != true) core.maintainDiscoveryAcquisitions()
    }
}
