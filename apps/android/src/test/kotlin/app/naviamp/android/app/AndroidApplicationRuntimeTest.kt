package app.naviamp.android

import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampApplicationControllers
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

class AndroidApplicationRuntimeTest {
    @Test
    fun declaresImplementedAndroidCapabilities() {
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.BackgroundPlayback))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.SystemMediaControls))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.SecureCredentialStorage))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.InsecureServerVerification))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.CustomServerCertificates))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.ClientCertificates))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.Downloads))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.SettingsImportExport))
        assertTrue(AndroidPlatformCapabilities.supports(PlatformCapability.AutomotiveBrowsing))
        assertFalse(AndroidPlatformCapabilities.supports(PlatformCapability.Sharing))
    }

    @Test
    fun activityBootstrapRestoresOnceAndDoesNotOwnShutdown() = runTest {
        var restorations = 0
        val session = AndroidApplicationSession { restorations += 1 }
        val runtime = NaviampApplicationRuntime(
            NaviampPlatformServices(
                capabilities = PlatformCapabilities(),
                session = session,
                playbackSessions = NaviampPlaybackSessionController(EmptyPlaybackSessionRepository),
                playbackExecution = NoOpPlaybackExecution,
                connectivity = NaviampConnectivityMonitor { NaviampConnectivitySnapshot(true) },
                errorReporter = NaviampRuntimeErrorReporter { _, _ -> },
            ),
            NaviampApplicationControllers(pendingProviderActions = EmptyRuntimePendingProviderActions),
        )

        runtime.handle(NaviampHostLifecycleEvent.Start)
        runtime.handle(NaviampHostLifecycleEvent.Start)
        runtime.handle(NaviampHostLifecycleEvent.Shutdown)

        assertEquals(1, restorations)
    }
}

private object EmptyRuntimePendingProviderActions : PendingProviderActionRepository {
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
