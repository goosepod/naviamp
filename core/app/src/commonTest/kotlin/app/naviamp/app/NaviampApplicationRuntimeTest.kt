package app.naviamp.app

import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import app.naviamp.domain.app.PlatformCapabilityStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NaviampApplicationRuntimeTest {
    @Test
    fun restoresOnceThenForwardsForegroundAndBackgroundEvents() = runTest {
        val fixture = RuntimeFixture()

        fixture.runtime.handle(NaviampHostLifecycleEvent.Start)
        fixture.runtime.handle(NaviampHostLifecycleEvent.Start)
        fixture.runtime.handle(NaviampHostLifecycleEvent.EnterForeground)
        fixture.runtime.handle(NaviampHostLifecycleEvent.EnterBackground)

        assertEquals(listOf("restore", "foreground", "background"), fixture.session.events)
        assertEquals(NaviampRuntimePhase.Background, fixture.runtime.state.value.phase)
        assertEquals(fixture.connectivitySnapshot, fixture.runtime.state.value.connectivity)
        assertTrue(fixture.runtime.services.capabilities.supports(PlatformCapability.BackgroundPlayback))
    }

    @Test
    fun restorationFailureIsReportedAndCanBeRetried() = runTest {
        val failure = IllegalStateException("restore failed")
        val fixture = RuntimeFixture(restoreFailures = 1, failure = failure)

        fixture.runtime.handle(NaviampHostLifecycleEvent.Start)

        assertEquals(NaviampRuntimePhase.Failed, fixture.runtime.state.value.phase)
        assertEquals(NaviampRuntimeOperation.Restore, fixture.runtime.state.value.lastError?.operation)
        assertEquals("restore failed", fixture.runtime.state.value.lastError?.message)
        assertSame(failure, fixture.reportedCauses.single())

        fixture.runtime.handle(NaviampHostLifecycleEvent.Start)

        assertEquals(NaviampRuntimePhase.Ready, fixture.runtime.state.value.phase)
        assertEquals(listOf("restore", "restore"), fixture.session.events)
    }

    @Test
    fun shutdownIsIdempotentAndStopsEvenWhenCleanupFails() = runTest {
        val fixture = RuntimeFixture(shutdownFails = true)

        fixture.runtime.handle(NaviampHostLifecycleEvent.Start)
        fixture.runtime.handle(NaviampHostLifecycleEvent.Shutdown)
        fixture.runtime.handle(NaviampHostLifecycleEvent.Shutdown)

        assertEquals(listOf("restore", "shutdown"), fixture.session.events)
        assertEquals(NaviampRuntimePhase.Stopped, fixture.runtime.state.value.phase)
        assertEquals(NaviampRuntimeOperation.Shutdown, fixture.runtime.state.value.lastError?.operation)
    }

    @Test
    fun cancellationPropagatesWithoutBecomingAReportedRuntimeFailure() = runTest {
        val fixture = RuntimeFixture(failure = CancellationException("host stopped"), restoreFailures = 1)

        assertFailsWith<CancellationException> {
            fixture.runtime.handle(NaviampHostLifecycleEvent.Start)
        }

        assertTrue(fixture.reportedCauses.isEmpty())
        assertEquals(null, fixture.runtime.state.value.lastError)
    }
}

private class RuntimeFixture(
    restoreFailures: Int = 0,
    failure: Throwable = IllegalStateException("operation failed"),
    shutdownFails: Boolean = false,
) {
    val connectivitySnapshot = NaviampConnectivitySnapshot(available = true, mobileData = true)
    val session = RecordingSession(restoreFailures, failure, shutdownFails)
    val reportedCauses = mutableListOf<Throwable?>()
    val runtime = NaviampApplicationRuntime(
        NaviampPlatformServices(
            capabilities = PlatformCapabilities().withStatus(
                PlatformCapability.BackgroundPlayback,
                PlatformCapabilityStatus.Available,
            ),
            session = session,
            connectivity = NaviampConnectivityMonitor { connectivitySnapshot },
            errorReporter = NaviampRuntimeErrorReporter { _, cause -> reportedCauses += cause },
        ),
    )
}

private class RecordingSession(
    private var restoreFailures: Int,
    private val failure: Throwable,
    private val shutdownFails: Boolean,
) : NaviampApplicationSession {
    val events = mutableListOf<String>()

    override suspend fun restore() {
        events += "restore"
        if (restoreFailures > 0) {
            restoreFailures -= 1
            throw failure
        }
    }

    override suspend fun enterForeground() {
        events += "foreground"
    }

    override suspend fun enterBackground() {
        events += "background"
    }

    override suspend fun shutdown() {
        events += "shutdown"
        if (shutdownFails) throw failure
    }
}
