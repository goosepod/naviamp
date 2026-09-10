package app.naviamp.app

import kotlin.test.*

class NaviampScreenAwakeControllerTest {
    @Test fun onlyEnabledVisibleSurfacesAcquireAndEveryExitReleases() {
        var acquired = 0
        var released = 0
        val controller = NaviampScreenAwakeController(NaviampScreenAwakeEffect { reason ->
            assertEquals("Localized reason", reason)
            acquired++
            NaviampScreenAwakeLease { released++ }
        })
        fun update(enabled: Boolean, visible: Boolean) = controller.update(enabled, visible, "Localized reason")
        update(false, true)
        update(true, false)
        assertEquals(0, acquired)
        update(true, true)
        update(true, true)
        assertEquals(1, acquired)
        assertEquals(NaviampScreenAwakeStatus.Active, controller.status.value)
        update(true, false)
        assertEquals(1, released)
        update(true, true)
        assertEquals(2, acquired)
        update(false, true)
        assertEquals(2, released)
        update(true, true)
        controller.close()
        controller.close()
        update(true, true)
        assertEquals(3, acquired)
        assertEquals(3, released)
        assertEquals(NaviampScreenAwakeStatus.Inactive, controller.status.value)
    }

    @Test fun failedAcquisitionRetriesOnlyAfterVisibilityOrPreferenceChanges() {
        var attempts = 0
        val controller = NaviampScreenAwakeController(NaviampScreenAwakeEffect {
            if (++attempts == 1) error("native acquisition failed")
            NaviampScreenAwakeLease {}
        })
        controller.update(true, true, "reason")
        assertEquals(NaviampScreenAwakeStatus.Failed, controller.status.value)
        controller.update(true, true, "reason")
        assertEquals(1, attempts)
        controller.update(true, false, "reason")
        controller.update(true, true, "reason")
        assertEquals(NaviampScreenAwakeStatus.Active, controller.status.value)
        controller.close()
    }

    @Test fun failedReleaseRetainsOwnershipForCleanup() {
        var releases = 0
        val controller = NaviampScreenAwakeController(NaviampScreenAwakeEffect {
            NaviampScreenAwakeLease { if (++releases == 1) error("native release failed") }
        })
        controller.update(true, true, "reason")
        controller.update(false, true, "reason")
        assertEquals(NaviampScreenAwakeStatus.Failed, controller.status.value)
        controller.close()
        assertEquals(2, releases)
        assertEquals(NaviampScreenAwakeStatus.Inactive, controller.status.value)
    }

    @Test fun unsupportedEffectNeverClaimsToKeepTheScreenAwake() {
        val controller = NaviampScreenAwakeController(null)
        controller.update(true, true, "reason")
        controller.close()
        assertEquals(NaviampScreenAwakeStatus.Unavailable, controller.status.value)
        val unavailableResource = NaviampScreenAwakeController(NaviampScreenAwakeEffect { null })
        unavailableResource.update(true, true, "reason")
        assertEquals(NaviampScreenAwakeStatus.Failed, unavailableResource.status.value)
    }
}
