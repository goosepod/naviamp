package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampConnectQrPanelTest {
    @Test fun theQrIsShownNextToTheCode() = runComposeUiTest {
        setContent {
            NaviampConnectShowCodePanel(
                NaviampConnectSettingsUi(localDeviceName = "Living Room", pairingCode = "482913",
                    pairingLink = "naviamp://connect?v=1#n=Living%20Room"),
                NaviampColors.Dark, actions(mutableListOf()),
            )
        }
        onNodeWithTag("connect-qr").assertExists()
        onNodeWithContentDescription("QR code for connecting to Living Room").assertExists()
        onNodeWithText("482 913").assertExists()
    }

    @Test fun withoutALinkOnlyTheCodeIsShown() = runComposeUiTest {
        setContent {
            NaviampConnectShowCodePanel(NaviampConnectSettingsUi(localDeviceName = "TV", pairingCode = "482913"),
                NaviampColors.Dark, actions(mutableListOf()))
        }
        onNodeWithTag("connect-qr").assertDoesNotExist()
    }

    @Test fun aScanOpensTheCameraAndHandsTheTextToCore() = runComposeUiTest {
        val calls = mutableListOf<String>()
        val scanner = FakeScanner(granted = true)
        setContent { enterPanel(scanner, calls) }
        onNodeWithTag("connect-qr-viewfinder").assertDoesNotExist()
        onNodeWithTag("connect-qr-scan").performClick()
        onNodeWithTag("connect-qr-viewfinder").assertExists()
        onNodeWithTag("fake-scan").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        assertEquals(listOf("scanned:naviamp://connect?v=1#x"), calls)
        onNodeWithTag("connect-qr-viewfinder").assertDoesNotExist()
    }

    @Test fun deniedCameraAccessFallsBackToTheCode() = runComposeUiTest {
        val scanner = FakeScanner(granted = false, answer = false)
        setContent { enterPanel(scanner, mutableListOf()) }
        onNodeWithTag("connect-qr-scan").performClick()
        assertEquals(1, scanner.requests)
        onNodeWithTag("connect-qr-denied").assertExists()
        onNodeWithTag("connect-qr-viewfinder").assertDoesNotExist()
        onNodeWithTag("connect-code-entry").assertExists()
    }

    @Test fun aDeviceWithoutACameraShowsNoScanButton() = runComposeUiTest {
        setContent { enterPanel(FakeScanner(granted = true, available = false), mutableListOf()) }
        onNodeWithTag("connect-qr-scan").assertDoesNotExist()
        onNodeWithTag("connect-code-entry").assertExists()
    }

    @Composable
    private fun enterPanel(scanner: NaviampQrScanner, calls: MutableList<String>) {
        CompositionLocalProvider(LocalNaviampQrScanner provides scanner) {
            NaviampConnectEnterCodePanel(NaviampConnectSettingsUi(), NaviampColors.Dark, actions(calls))
        }
    }

    private class FakeScanner(
        granted: Boolean,
        private val answer: Boolean = true,
        override val available: Boolean = true,
    ) : NaviampQrScanner {
        private var permission by mutableStateOf(granted)
        var requests = 0

        @Composable override fun hasCameraPermission() = permission

        @Composable override fun rememberCameraPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = {
            requests += 1
            permission = answer
            onResult(answer)
        }

        @Composable override fun Viewfinder(onScanned: (String) -> Unit, modifier: Modifier) {
            Box(modifier) {
                androidx.compose.material3.TextButton(onClick = { onScanned("naviamp://connect?v=1#x") },
                    modifier = Modifier.testTag("fake-scan")) { androidx.compose.material3.Text("scan") }
            }
        }
    }

    private fun actions(calls: MutableList<String>) = NaviampConnectSettingsActions(
        onStartPairingMode = {}, onStopPairingMode = {}, onTargetSelected = {}, onTrustedDeviceSelected = {},
        onPlaybackDeviceSelected = {}, onLocalDeviceNameChanged = {}, onTrustedDeviceAliasChanged = { _, _ -> },
        onForgetTrustedDevice = {}, onPairingCodeChanged = {}, onSubmitPairingCode = {},
        onRemotePrevious = {}, onRemotePlayPause = {}, onRemoteNext = {}, onStopControlling = {},
        onRemoteHandoffQueue = {}, onReceiveRemoteQueue = {}, onProvisionTarget = {},
        onDismissSourceMismatchRecovery = {},
        remoteNowPlayingActions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
        onConnectLinkScanned = { calls += "scanned:$it" },
    )
}
