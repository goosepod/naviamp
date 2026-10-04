package app.naviamp.ui

import app.naviamp.domain.settings.DesktopShortcutPlatform
import kotlin.test.*

class NaviampWindowControllerTest {
    @Test fun restoresGeometryAndPlacementAcrossRepeatedTransitions() {
        for (placement in listOf(NaviampWindowPlacement.Floating, NaviampWindowPlacement.Maximized)) {
            val initial = NaviampWindowSnapshot(placement, 1080f, 720f, -1000f, 80f)
            val requests = mutableListOf<NaviampWindowSnapshot>()
            val controller = NaviampWindowController({ requests.add(it); true }, initial)
            repeat(3) {
                assertTrue(controller.toggle())
                controller.observe(initial.copy(placement = NaviampWindowPlacement.Fullscreen, width = 1920f, height = 1080f))
                assertEquals(initial, controller.windowedSnapshot)
                assertTrue(controller.toggle())
                assertEquals(initial, requests.last())
                assertEquals(initial, controller.state)
            }
        }
    }

    @Test fun nativeControlsAndWindowedResizeUpdateTheSharedAction() {
        val initial = NaviampWindowSnapshot()
        val controller = NaviampWindowController({ true }, initial)
        val resized = initial.copy(width = 1200f, x = 50f, y = 20f)
        controller.observe(resized)
        controller.observe(resized.copy(placement = NaviampWindowPlacement.Fullscreen))
        assertFalse(controller.escape(overlayVisible = true))
        assertEquals(NaviampWindowPlacement.Fullscreen, controller.state.placement)
        assertTrue(controller.escape(overlayVisible = false))
        assertEquals(resized, controller.state)
        assertFalse(controller.escape(overlayVisible = false))
    }

    @Test fun fullscreenResizeDoesNotReplaceMaximizedRestoreGeometry() {
        val initial = NaviampWindowSnapshot(NaviampWindowPlacement.Maximized, 2560f, 1400f, -8f, -8f)
        val requests = mutableListOf<NaviampWindowSnapshot>()
        val controller = NaviampWindowController({ requests.add(it); true }, initial)
        assertTrue(controller.toggle())
        controller.observe(initial.copy(placement = NaviampWindowPlacement.Fullscreen, height = 1440f))
        controller.observe(controller.state.copy(width = 1920f, height = 1080f, x = 0f, y = 0f))
        assertTrue(controller.escape(overlayVisible = false))
        assertEquals(initial, requests.last())
        assertEquals(initial, controller.windowedSnapshot)
    }

    @Test fun rejectedAndThrowingEffectsPreserveStateAndRestoreGeometry() {
        for (effect in listOf(NaviampWindowEffect { false }, NaviampWindowEffect { error("Native failure") })) {
            val initial = NaviampWindowSnapshot()
            val controller = NaviampWindowController(effect, initial)
            assertFalse(controller.toggle())
            assertEquals(initial, controller.state)
            assertEquals(initial, controller.windowedSnapshot)
        }
    }

    @Test fun removedMonitorRestoresToPlatformDefaultWithoutChangingSize() {
        val snapshot = NaviampWindowSnapshot(width = 1080f, height = 720f, x = -1500f, y = 10f)
        val primary = NaviampWindowScreen(0f, 0f, 1920f, 1080f)
        assertEquals(snapshot.copy(x = null, y = null), snapshot.onAvailableScreen(listOf(primary)))
        assertEquals(snapshot, snapshot.onAvailableScreen(listOf(primary, NaviampWindowScreen(-1920f, 0f, 1920f, 1080f))))
    }

    @Test fun shortcutsRequireExactModifiers() {
        for (platform in DesktopShortcutPlatform.entries) {
            assertTrue(naviampWindowToggleShortcut(platform, true, false, false, false, false, false))
            assertFalse(naviampWindowToggleShortcut(platform, true, false, true, false, false, false))
            assertEquals(platform == DesktopShortcutPlatform.MacOS,
                naviampWindowToggleShortcut(platform, false, true, true, true, false, false))
            assertFalse(naviampWindowToggleShortcut(platform, false, true, true, true, true, false))
            assertFalse(naviampWindowToggleShortcut(platform, false, true, true, true, false, true))
        }
    }
}
