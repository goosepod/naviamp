package app.naviamp.android

import android.content.Context
import android.content.Intent
import android.app.ForegroundServiceStartNotAllowedException
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.naviamp.presentation.NaviampCore
import app.naviamp.presentation.NaviampCoreExternalPlaybackBridge
import app.naviamp.presentation.externalPlaybackBridge
import app.naviamp.presentation.playbackServiceRetentionDecisions
import app.naviamp.presentation.NaviampPlaybackServiceLifecycleCoordinator
import kotlinx.coroutines.flow.collect

/** Process-local handoff between the mounted Core owner and Android's service lifecycle. */
object AndroidNaviampPlaybackRuntime {
    @Volatile
    private var activeBridge: NaviampCoreExternalPlaybackBridge? = null

    fun install(bridge: NaviampCoreExternalPlaybackBridge) {
        activeBridge = bridge
    }

    fun uninstall(bridge: NaviampCoreExternalPlaybackBridge) {
        if (activeBridge === bridge) activeBridge = null
    }

    fun bridge(): NaviampCoreExternalPlaybackBridge? = activeBridge
}

/** Keeps Android's foreground service aligned with Core's external-playback retention decision. */
@Composable
fun AndroidNaviampPlaybackLifecycle(core: NaviampCore, lifecycle: Lifecycle) {
    val context = LocalContext.current.applicationContext
    val bridge = androidx.compose.runtime.remember(core) { core.externalPlaybackBridge() }
    val coordinator = remember(bridge, context) {
        NaviampPlaybackServiceLifecycleCoordinator(
            start = { context.startNaviampPlaybackService() },
            stop = { context.stopService(AndroidNaviampPlaybackService.refreshIntent(context)); Unit },
        )
    }
    DisposableEffect(lifecycle, coordinator) {
        val observer = LifecycleEventObserver { _, _ ->
            coordinator.visibilityChanged(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        coordinator.visibilityChanged(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            coordinator.visibilityChanged(false)
        }
    }
    LaunchedEffect(bridge, coordinator) {
        bridge.snapshots.playbackServiceRetentionDecisions().collect(coordinator::retentionChanged)
    }
}

/** Translate Android's background-start restriction into a shared, retryable native result. */
internal fun Context.startNaviampPlaybackService(): Boolean = try {
    startForegroundService(AndroidNaviampPlaybackService.foregroundStartIntent(this))
    true
} catch (error: IllegalStateException) {
    if (Build.VERSION.SDK_INT < 31 || error !is ForegroundServiceStartNotAllowedException) throw error
    Log.w("NaviampPlaybackService", "Android deferred the playback foreground-service start")
    false
}

internal fun Context.naviampPlaybackServiceIntent(action: String): Intent =
    Intent(this, AndroidNaviampPlaybackService::class.java).setAction(action)
