package app.naviamp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectSourceMismatchDialogTest {
    @Test
    fun mismatchOffersApprovalGatedTargetProvisioningAndSettingsRecovery() = runComposeUiTest {
        val actions = mutableListOf<String>()
        setContent {
            NaviampConnectSourceMismatchDialog(
                recovery = NaviampConnectSourceMismatchUi(
                    targetName = "Living Room TV",
                    canProvisionTarget = true,
                ),
                colors = NaviampColors.Dark,
                onOpenSettings = { actions += "settings" },
                onProvisionTarget = { actions += "provision" },
                onDismiss = { actions += "dismiss" },
            )
        }

        onNodeWithTag(NaviampConnectSourceMismatchDialogTestTag).assertExists()
        onNodeWithTag(NaviampConnectSourceMismatchSettingsTestTag).performClick()
        assertEquals(listOf("settings"), actions)

        onNodeWithTag(NaviampConnectSourceMismatchProvisionTestTag).performClick()
        assertEquals(listOf("settings", "provision"), actions)
    }

    @Test
    fun mismatchWithoutProvisioningStillOffersSourceSettings() = runComposeUiTest {
        var openedSettings = false
        setContent {
            NaviampConnectSourceMismatchDialog(
                recovery = NaviampConnectSourceMismatchUi(
                    targetName = "Living Room TV",
                    canProvisionTarget = false,
                ),
                colors = NaviampColors.Dark,
                onOpenSettings = { openedSettings = true },
                onProvisionTarget = null,
                onDismiss = {},
            )
        }

        onNodeWithTag(NaviampConnectSourceMismatchSettingsTestTag).performClick()
        assertEquals(true, openedSettings)
    }
}
