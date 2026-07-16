package app.naviamp.android

import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidNavigationStatePropertyTest {
    @Test
    fun writesExistingAndroidStateShapeIntoSharedController() {
        val controller = NaviampNavigationController()
        var state by AndroidNavigationStateProperty(controller)
        val selected = NaviampNavigationState(
            route = NaviampRoute.Downloads,
            lastContentRoute = NaviampRoute.Library,
        )

        state = selected

        assertEquals(selected, state)
        assertEquals(selected, controller.state.value)
    }
}
