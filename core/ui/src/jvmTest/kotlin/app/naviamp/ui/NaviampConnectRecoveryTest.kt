package app.naviamp.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectRecoveryTest {
    @Test
    fun televisionPermissionRecoveryOffersSettingsAndRetry() = runDesktopComposeUiTest(1280, 720) {
        val calls = mutableListOf<String>()
        setContent {
            NaviampConnectRecoveryPanel(
                NaviampConnectRecoveryUi(NaviampConnectRecoveryProblem.LocalNetworkPermission, canOpenSettings = true),
                NaviampColors.Dark, { calls += "retry" }, { calls += "settings" }, television = true,
            )
        }
        onNodeWithTag("connect-recovery-settings").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.Enter); pressKey(Key.DirectionDown) }
        onNodeWithTag("connect-recovery-retry").assertIsFocused()
            .performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("settings", "retry"), calls)
    }

    @Test
    fun networkFailureOffersRetryWithoutUnavailableSettingsAction() = runComposeUiTest {
        var retries = 0
        setContent {
            NaviampConnectRecoveryPanel(
                NaviampConnectRecoveryUi(NaviampConnectRecoveryProblem.DiscoveryUnavailable),
                NaviampColors.Dark, { retries++ }, {},
            )
        }
        onNodeWithTag("connect-recovery-settings").assertDoesNotExist()
        onNodeWithTag("connect-recovery-retry").performClick()
        assertEquals(1, retries)
    }
}
