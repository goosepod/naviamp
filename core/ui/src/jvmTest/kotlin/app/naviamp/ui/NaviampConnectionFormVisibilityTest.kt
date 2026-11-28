package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectionFormVisibilityTest {
    @Test
    fun anErrorThatArrivesWhileScrolledToSaveIsScrolledIntoViewAndAnnounced() = runDesktopComposeUiTest(600, 500) {
        val status = mutableStateOf<String?>(null)
        val scroll = mutableStateOf<androidx.compose.foundation.ScrollState?>(null)
        var scrollToBottom: () -> Unit = {}
        setContent {
            androidx.compose.material3.MaterialTheme {
                val state = rememberScrollState()
                val coroutines = rememberCoroutineScope()
                scroll.value = state
                scrollToBottom = { coroutines.launch { state.scrollTo(state.maxValue) } }
                Column(Modifier.fillMaxSize().verticalScroll(state)) {
                    NaviampConnectionForm(
                        ConnectionFormState(serverUrl = "https://music.example", username = "demo"),
                        NaviampColors.Dark, isReconnect = true,
                        connectionStatus = status.value, connectionStatusIsError = status.value != null,
                        onFormChanged = {}, onConnect = {}, onCancel = {},
                    )
                }
            }
        }
        waitForIdle()
        scrollToBottom()
        waitForIdle()
        val bottom = scroll.value!!.value
        check(bottom > 0) { "The form must be taller than the window for this check" }

        status.value = "connection_aurral_companion_sign_in_failed"
        waitForIdle()

        onNodeWithTag(ConnectionErrorCardTestTag).assertIsDisplayed()
        onNodeWithText("Aurral did not accept this username and password.", substring = true).assertIsDisplayed()
        assertEquals(LiveRegionMode.Assertive,
            onNodeWithTag(ConnectionErrorCardTestTag).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
        check(scroll.value!!.value < bottom) { "The form did not scroll up to the error" }
    }

    @Test
    fun editingFormShowsAUsableCancelBesideSave() = runDesktopComposeUiTest(600, 1400) {
        var cancels = 0
        setContent {
            androidx.compose.material3.MaterialTheme {
                NaviampConnectionForm(
                    ConnectionFormState(serverUrl = "https://music.example", username = "demo"),
                    NaviampColors.Dark, isReconnect = true,
                    onFormChanged = {}, onConnect = {}, onCancel = { cancels++ },
                )
            }
        }
        onNodeWithTag(ConnectionConnectButtonTestTag).assertIsDisplayed()
        val cancel = onNodeWithTag(ConnectionCancelButtonTestTag).assertIsDisplayed()
        check(cancel.fetchSemanticsNode().size.width > 0) { "Cancel was squeezed to zero width by Save" }
        cancel.performClick()
        assertEquals(1, cancels)
    }
}
