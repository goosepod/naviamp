package app.naviamp.desktop

import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampApplicationSession
import app.naviamp.app.NaviampCapabilityPresentation
import app.naviamp.app.NaviampClock
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampPlatformServices
import app.naviamp.app.NaviampRuntimeErrorReporter
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus

internal val DesktopPlatformCapabilities: PlatformCapabilities = listOf(
    PlatformCapability.BackgroundPlayback,
    PlatformCapability.InsecureServerVerification,
    PlatformCapability.CustomServerCertificates,
    PlatformCapability.ClientCertificates,
    PlatformCapability.Downloads,
    PlatformCapability.OfflinePlayback,
    PlatformCapability.SettingsImportExport,
    PlatformCapability.FileSelection,
).fold(PlatformCapabilities()) { capabilities, capability ->
    capabilities.withStatus(capability, PlatformCapabilityStatus.Available)
}
internal val DesktopCapabilityPresentation = NaviampCapabilityPresentation(DesktopPlatformCapabilities)
internal val DesktopSystemClock = NaviampClock(System::currentTimeMillis)

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
        errorReporter = NaviampRuntimeErrorReporter { error, cause ->
            System.err.println("Naviamp runtime ${error.operation}: ${error.message}")
            cause?.printStackTrace(System.err)
        },
    ),
    controllers = controllers,
)
