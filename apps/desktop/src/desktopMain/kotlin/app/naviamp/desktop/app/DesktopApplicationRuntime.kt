package app.naviamp.desktop

import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.NaviampConnectivitySnapshot
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlatformServices
import app.naviamp.app.NaviampRuntimeErrorReporter
import app.naviamp.domain.app.PlatformCapabilities

/** Thin Desktop adapter that delegates restoration to the existing connection controller. */
internal class DesktopApplicationSession(
    private val hasSavedConnection: Boolean,
    private val restoreSavedSession: () -> Unit,
) : NaviampApplicationSession {
    override suspend fun restore() {
        if (hasSavedConnection) restoreSavedSession()
    }
}

internal fun desktopApplicationRuntime(
    controllers: NaviampApplicationControllers,
    playbackSessions: NaviampPlaybackSessionController,
    playbackExecution: NaviampPlaybackExecution,
    hasSavedConnection: Boolean,
    restoreSavedSession: () -> Unit,
): NaviampApplicationRuntime = NaviampApplicationRuntime(
    services = NaviampPlatformServices(
        capabilities = PlatformCapabilities(),
        session = DesktopApplicationSession(hasSavedConnection, restoreSavedSession),
        playbackSessions = playbackSessions,
        playbackExecution = playbackExecution,
        // Desktop currently has no live OS connectivity monitor. Preserve its online-first behavior
        // behind the contract until the dedicated Desktop platform service is extracted.
        connectivity = NaviampConnectivityMonitor {
            NaviampConnectivitySnapshot(available = true)
        },
        errorReporter = NaviampRuntimeErrorReporter { error, cause ->
            System.err.println("Naviamp runtime ${error.operation}: ${error.message}")
            cause?.printStackTrace(System.err)
        },
    ),
    controllers = controllers,
)
