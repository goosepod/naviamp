package app.naviamp.android

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.naviamp.presentation.NaviampCore
import app.naviamp.presentation.NaviampCoreExternalPlaybackBridge
import app.naviamp.presentation.externalPlaybackBridge
import kotlinx.coroutines.flow.collectLatest

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
fun AndroidNaviampPlaybackLifecycle(core: NaviampCore) {
    val context = LocalContext.current.applicationContext
    val bridge = androidx.compose.runtime.remember(core) { core.externalPlaybackBridge() }
    DisposableEffect(bridge) {
        AndroidNaviampPlaybackRuntime.install(bridge)
        onDispose {
            AndroidNaviampPlaybackRuntime.uninstall(bridge)
        }
    }
    LaunchedEffect(bridge) {
        bridge.snapshots.collectLatest { snapshot ->
            if (snapshot.shouldRetainPlaybackService) {
                context.startForegroundService(AndroidNaviampPlaybackService.refreshIntent(context))
            } else {
                context.stopService(AndroidNaviampPlaybackService.refreshIntent(context))
            }
        }
    }
}

internal fun Context.naviampPlaybackServiceIntent(action: String): Intent =
    Intent(this, AndroidNaviampPlaybackService::class.java).setAction(action)
