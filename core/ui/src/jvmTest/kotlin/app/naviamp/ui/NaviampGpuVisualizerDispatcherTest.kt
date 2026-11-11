package app.naviamp.ui

import java.awt.EventQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertTrue

class NaviampGpuVisualizerDispatcherTest {
    @Test fun nativePresentationMainDispatcherIsAvailableOnDesktop() = runBlocking {
        withContext(Dispatchers.Main.immediate) {
            assertTrue(EventQueue.isDispatchThread())
        }
    }
}
