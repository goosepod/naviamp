package app.naviamp.desktop

import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationControllers
import app.naviamp.app.NaviampClock
import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.NaviampConnectivitySnapshot
import app.naviamp.app.NaviampHostLifecycleEvent
import app.naviamp.app.NaviampPlatformServices
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampRuntimeErrorReporter
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.provider.PendingProviderAction
import app.naviamp.domain.provider.PendingProviderActionRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopApplicationRuntimeTest {
    @Test
    fun declaresImplementedDesktopCapabilities() {
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.BackgroundPlayback))
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.Downloads))
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.SettingsImportExport))
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.FileSelection))
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.InsecureServerVerification))
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.CustomServerCertificates))
        assertTrue(DesktopPlatformCapabilities.supports(PlatformCapability.ClientCertificates))
        assertFalse(DesktopPlatformCapabilities.supports(PlatformCapability.SystemMediaControls))
        assertFalse(DesktopPlatformCapabilities.supports(PlatformCapability.SecureCredentialStorage))
        assertFalse(DesktopPlatformCapabilities.supports(PlatformCapability.AutomotiveBrowsing))
    }

    @Test
    fun restoresSavedConnectionOnce() = runTest {
        var restorations = 0
        val runtime = runtimeWith(DesktopApplicationSession(true) { restorations += 1 })

        runtime.handle(NaviampHostLifecycleEvent.Start)
        runtime.handle(NaviampHostLifecycleEvent.Start)

        assertEquals(1, restorations)
    }

    @Test
    fun skipsRestorationWithoutSavedConnection() = runTest {
        var restorations = 0
        val runtime = runtimeWith(DesktopApplicationSession(false) { restorations += 1 })

        runtime.handle(NaviampHostLifecycleEvent.Start)

        assertEquals(0, restorations)
    }

    private fun runtimeWith(session: DesktopApplicationSession) = NaviampApplicationRuntime(
        NaviampPlatformServices(
            capabilities = PlatformCapabilities(),
            session = session,
            playbackSessions = NaviampPlaybackSessionController(EmptyPlaybackSessionRepository),
            playbackExecution = NoOpPlaybackExecution,
            clock = NaviampClock { 0L },
            connectivity = NaviampConnectivityMonitor { NaviampConnectivitySnapshot(true) },
            errorReporter = NaviampRuntimeErrorReporter { _, _ -> },
        ),
        NaviampApplicationControllers(pendingProviderActions = EmptyPendingProviderActions),
    )
}

private object EmptyPendingProviderActions : PendingProviderActionRepository {
    override fun enqueuePendingProviderAction(
        sourceId: String,
        actionType: String,
        entityId: String,
        boolValue: Boolean?,
        longValue: Long?,
        replaceMatchingEntityAction: Boolean,
    ) = Unit

    override fun pendingProviderActions(sourceId: String, limit: Int): List<PendingProviderAction> = emptyList()
    override fun deletePendingProviderAction(id: Long) = Unit
    override fun markPendingProviderActionFailed(id: Long, errorMessage: String?) = Unit
}

private object NoOpPlaybackExecution : NaviampPlaybackExecution {
    override fun pause() = Unit

    override fun resume() = Unit

    override fun startOrRestore(): Boolean = false

    override fun seek(positionSeconds: Double) = Unit

    override fun replayCurrent(positionSeconds: Double) = Unit

    override fun setVolume(percent: Int) = Unit

    override fun stop() = Unit
}

private object EmptyPlaybackSessionRepository : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}
