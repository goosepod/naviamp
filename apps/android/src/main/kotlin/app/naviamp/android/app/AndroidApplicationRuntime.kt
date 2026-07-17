package app.naviamp.android

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.NaviampConnectivitySnapshot
import app.naviamp.app.NaviampNavigationController
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlatformServices
import app.naviamp.app.NaviampRuntimeErrorReporter
import app.naviamp.domain.app.PlatformCapabilities

/**
 * Thin Android adapter for shared application startup.
 *
 * Deliberately does not override shutdown: the Activity is not the owner of the playback session,
 * which may continue in the foreground service after the UI leaves composition.
 */
internal class AndroidApplicationSession(
    private val restoreSavedSession: () -> Unit,
) : NaviampApplicationSession {
    override suspend fun restore() {
        restoreSavedSession()
    }
}

internal fun androidApplicationRuntime(
    context: Context,
    navigation: NaviampNavigationController,
    playbackSessions: NaviampPlaybackSessionController,
    restoreSavedSession: () -> Unit,
): NaviampApplicationRuntime {
    val applicationContext = context.applicationContext
    return NaviampApplicationRuntime(
        services = NaviampPlatformServices(
            capabilities = PlatformCapabilities(),
            session = AndroidApplicationSession(restoreSavedSession),
            playbackSessions = playbackSessions,
            connectivity = NaviampConnectivityMonitor {
                val connectivityManager = applicationContext
                    .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val network = connectivityManager?.activeNetwork
                NaviampConnectivitySnapshot(
                    available = network != null && connectivityManager.getNetworkCapabilities(network) != null,
                    mobileData = applicationContext.isActiveNetworkMobileData(),
                )
            },
            errorReporter = NaviampRuntimeErrorReporter { error, cause ->
                Log.e("NaviampRuntime", "${error.operation}: ${error.message}", cause)
            },
        ),
        navigation = navigation,
    )
}
