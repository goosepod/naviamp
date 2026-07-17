package app.naviamp.android

import app.naviamp.app.NaviampApplicationRuntime
import app.naviamp.app.NaviampConnectivityMonitor
import app.naviamp.app.NaviampConnectivitySnapshot
import app.naviamp.app.NaviampHostLifecycleEvent
import app.naviamp.app.NaviampPlatformServices
import app.naviamp.app.NaviampPlaybackSessionController
import app.naviamp.app.NaviampPlaybackExecution
import app.naviamp.app.NaviampRuntimeErrorReporter
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidApplicationRuntimeTest {
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
        )

        runtime.handle(NaviampHostLifecycleEvent.Start)
        runtime.handle(NaviampHostLifecycleEvent.Start)
        runtime.handle(NaviampHostLifecycleEvent.Shutdown)

        assertEquals(1, restorations)
    }
}

private object NoOpPlaybackExecution : NaviampPlaybackExecution {
    override fun seek(positionSeconds: Double) = Unit

    override fun replayCurrent(positionSeconds: Double) = Unit
}

private object EmptyPlaybackSessionRepository : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}
