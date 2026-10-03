package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.ui.NaviampCastPickerProblemUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreCastPickerControllerTest {
    @Test
    fun selectionUsesSharedSessionAndDismissalKeepsTheSession() = runTest {
        val native = Discovery()
        val effect = Session()
        val outputs = NaviampPlaybackOutputSelectionController()
        val sessions = NaviampCastSessionController(effect, outputs).also { it.start() }
        val picker = NaviampCoreCastPickerController(backgroundScope,
            NaviampCastDiscoveryController(native, { testScheduler.currentTime }), sessions, outputs)
        picker.show()
        runCurrent()
        native.listener.onServiceResolved(service)
        runCurrent()
        assertEquals("Onn", picker.state.value.targets.single().displayName)
        picker.select("tv")
        runCurrent()
        assertEquals(1, effect.connects)
        assertFalse(picker.state.value.visible)
        assertEquals(1, native.stops)
        assertEquals(0, effect.disconnects)
        assertEquals(NaviampRemoteOutputPhase.Connected, (outputs.state.value as NaviampPlaybackOutputSelection.Remote).phase)
        picker.show()
        runCurrent()
        picker.selectLocal()
        runCurrent()
        assertEquals(NaviampPlaybackOutputSelection.Local, outputs.state.value)
        assertEquals(1, effect.disconnects)
        picker.close()
    }

    @Test
    fun lostRowsAndConnectionFailuresRemainVisibleAndCanRetry() = runTest {
        val native = Discovery()
        val effect = Session().also { it.accept = false }
        val outputs = NaviampPlaybackOutputSelectionController()
        val sessions = NaviampCastSessionController(effect, outputs).also { it.start() }
        val picker = NaviampCoreCastPickerController(backgroundScope,
            NaviampCastDiscoveryController(native, { testScheduler.currentTime }), sessions, outputs)
        picker.show()
        runCurrent()
        picker.select("tv")
        assertEquals(NaviampCastPickerProblemUi.TargetLost, picker.state.value.problem)
        assertEquals(0, effect.connects)
        native.listener.onServiceResolved(service)
        runCurrent()
        picker.select("tv")
        runCurrent()
        assertTrue(picker.state.value.visible)
        assertEquals(NaviampCastPickerProblemUi.Connection, picker.state.value.problem)
        picker.retry()
        runCurrent()
        assertNull(picker.state.value.problem)
        assertEquals(2, native.starts)
        native.listener.onDiscoveryFailed(NaviampCastDiscoveryProblem.PermissionDenied)
        runCurrent()
        assertEquals(NaviampCastPickerProblemUi.Discovery, picker.state.value.problem)
        picker.close()
    }

    private class Discovery : NaviampCastDiscoveryEffect {
        lateinit var listener: NaviampCastDiscoveryListener
        var starts = 0
        var stops = 0
        override fun start(serviceType: String, listener: NaviampCastDiscoveryListener) { this.listener = listener; starts++ }
        override fun stop() { stops++ }
    }
    private class Session : NaviampCastSessionEffect {
        lateinit var listener: NaviampCastSessionListener
        var connects = 0
        var disconnects = 0
        var accept = true
        override fun start(listener: NaviampCastSessionListener) { this.listener = listener }
        override fun stop() {}
        override fun disconnect() { disconnects++ }
        override suspend fun connect(selectionId: Long, target: NaviampCastDiscoveredTarget): Boolean {
            connects++
            if (accept) listener.onConnected(selectionId, target.target.displayName)
            return accept
        }
    }
    companion object {
        val service = NaviampCastResolvedService("wifi/tv", listOf("192.0.2.1"), 8009,
            mapOf("id" to "tv", "fn" to "Onn"))
    }
}
