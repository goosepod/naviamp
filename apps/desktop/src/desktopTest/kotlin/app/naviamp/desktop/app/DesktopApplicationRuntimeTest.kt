package app.naviamp.desktop

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

class DesktopApplicationRuntimeTest {
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
            connectivity = NaviampConnectivityMonitor { NaviampConnectivitySnapshot(true) },
            errorReporter = NaviampRuntimeErrorReporter { _, _ -> },
        ),
    )
}

private object NoOpPlaybackExecution : NaviampPlaybackExecution {
    override fun seek(positionSeconds: Double) = Unit

    override fun replayCurrent(positionSeconds: Double) = Unit
}

private object EmptyPlaybackSessionRepository : PlaybackSessionRepository {
    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? = null

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
}
