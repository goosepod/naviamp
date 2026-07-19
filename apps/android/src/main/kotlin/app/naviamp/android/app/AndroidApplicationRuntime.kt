package app.naviamp.android

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.app.NaviampClock
import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.NaviampConnectivitySnapshot
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlatformServices
import app.naviamp.app.NaviampRuntimeErrorReporter
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus

internal val AndroidPlatformCapabilities: PlatformCapabilities = listOf(
    PlatformCapability.BackgroundPlayback,
    PlatformCapability.SystemMediaControls,
    PlatformCapability.SecureCredentialStorage,
    PlatformCapability.InsecureServerVerification,
    PlatformCapability.CustomServerCertificates,
    PlatformCapability.ClientCertificates,
    PlatformCapability.Downloads,
    PlatformCapability.OfflinePlayback,
    PlatformCapability.SettingsImportExport,
    PlatformCapability.FileSelection,
    PlatformCapability.ApplicationUpdates,
    PlatformCapability.AutomotiveBrowsing,
).fold(PlatformCapabilities()) { capabilities, capability ->
    capabilities.withStatus(capability, PlatformCapabilityStatus.Available)
}
internal val AndroidCapabilityPresentation = NaviampCapabilityPresentation(AndroidPlatformCapabilities)
internal val AndroidSystemClock = NaviampClock(System::currentTimeMillis)

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
    controllers: NaviampApplicationControllers,
    playbackSessions: NaviampPlaybackSessionController,
    playbackExecution: NaviampPlaybackExecution,
    restoreSavedSession: () -> Unit,
): NaviampApplicationRuntime {
    val applicationContext = context.applicationContext
    return NaviampApplicationRuntime(
        services = NaviampPlatformServices(
            capabilities = AndroidPlatformCapabilities,
            session = AndroidApplicationSession(restoreSavedSession),
            playbackSessions = playbackSessions,
            playbackExecution = playbackExecution,
            clock = AndroidSystemClock,
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
        controllers = controllers,
    )
}
