package app.naviamp.desktop

import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlatformServices

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
        capabilities = DesktopPlatformCapabilities,
        session = DesktopApplicationSession(hasSavedConnection, restoreSavedSession),
        playbackSessions = playbackSessions,
        playbackExecution = playbackExecution,
        clock = DesktopSystemClock,
        connectivity = DesktopConnectivityMonitor(),
        errorReporter = DesktopRuntimeErrorReporter(),
    ),
    controllers = controllers,
)
