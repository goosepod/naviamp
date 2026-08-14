package app.naviamp.ui

import app.naviamp.domain.network.NaviampAppBuildNumber
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampAboutUiTest {
    @Test
    fun defaultAboutInfoUsesSharedBuildNumber() {
        assertEquals(NaviampAppBuildNumber, NaviampAboutUi().buildNumber)
    }
}
