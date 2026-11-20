package app.naviamp.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionPlaybackKeysTest {
    @Test fun loadingTransportReceivesInitialFocusOnceReadyWithoutStealingLaterFocus() = runComposeUiTest {
        val enabled = mutableStateOf(false)
        val mode = object : InputModeManager {
            override var inputMode by mutableStateOf(InputMode.Touch)
            override fun requestInputMode(inputMode: InputMode): Boolean { this.inputMode = inputMode; return true }
        }
        setContent {
            CompositionLocalProvider(LocalInputModeManager provides mode) { Column {
                TelevisionIconButton(enabled.value, NaviampTransportIcons.Play, "Play", NaviampColors.Dark,
                    initiallyFocused = true, onClick = {})
                Box(Modifier.testTag("other").focusable())
            } }
        }
        onNodeWithContentDescription("Play").assertIsNotEnabled()
        runOnIdle { enabled.value = true }
        waitForIdle()
        onNodeWithContentDescription("Play").assertIsFocused()
        assertEquals(InputMode.Keyboard, mode.inputMode)
        onNodeWithTag("other").performSemanticsAction(SemanticsActions.RequestFocus)
        runOnIdle { enabled.value = false }
        waitForIdle()
        runOnIdle { enabled.value = true }
        waitForIdle()
        onNodeWithTag("other").assertIsFocused()
    }

    @Test fun heldButtonTogglesOnceAcrossPlaybackStateChanges() = runComposeUiTest {
        val playing = mutableStateOf(NowPlayingUi(id = "track", title = "Title", subtitle = "Artist", stateLabel = "Playing", isPlaying = true))
        val requests = mutableListOf<NowPlayingPlaybackActionRequest>()
        setContent {
            Box(Modifier.naviampTelevisionPlaybackKeys(playing.value) {
                requests += it
                playing.value = playing.value.copy(isPlaying = false, isPaused = true)
            }) { Box(Modifier.testTag("focus").focusable()) }
        }
        onNodeWithTag("focus").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { keyDown(Key.MediaPlayPause) }
        waitForIdle()
        onNodeWithTag("focus").performKeyInput { advanceEventTime(1_000); keyUp(Key.MediaPlayPause) }
        assertEquals(listOf(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.TogglePlayPause)), requests)
        onNodeWithTag("focus").performKeyInput { pressKey(Key.MediaPlayPause) }
        assertEquals(2, requests.size)
    }

    @Test fun missingOrUnavailablePlaybackDoesNotConsumeOrStartPlayback() = runComposeUiTest {
        val playing = mutableStateOf<NowPlayingUi?>(null)
        var commands = 0
        var unhandled = 0
        setContent {
            Box(Modifier.onKeyEvent { if (it.type == KeyEventType.KeyDown) unhandled++; true }
                .naviampTelevisionPlaybackKeys(playing.value) { commands++ }) {
                Box(Modifier.testTag("focus").focusable())
            }
        }
        onNodeWithTag("focus").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.MediaPlayPause) }
        runOnIdle { playing.value = NowPlayingUi(id = "track", title = "Title", subtitle = "Artist", stateLabel = "Paused", canPlayPause = false) }
        onNodeWithTag("focus").performKeyInput { pressKey(Key.MediaPlayPause) }
        assertEquals(0, commands)
        assertEquals(2, unhandled)
    }

    @Test fun focusedControlReceivesButtonBeforeShell() = runComposeUiTest {
        var shell = 0
        var child = 0
        setContent {
            Box(Modifier.naviampTelevisionPlaybackKeys(NowPlayingUi(id = "track", title = "Title", subtitle = "Artist", stateLabel = "Paused")) { shell++ }) {
                Box(Modifier.testTag("focus").onPreviewKeyEvent {
                    if (it.key == Key.MediaPlayPause) { if (it.type == KeyEventType.KeyDown) child++; true } else false
                }.focusable())
            }
        }
        onNodeWithTag("focus").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { pressKey(Key.MediaPlayPause) }
        assertEquals(1, child)
        assertEquals(0, shell)
    }

    @Test fun backgroundingWithoutKeyReleaseDoesNotSwallowNextForegroundPress() = runComposeUiTest {
        val focused = mutableStateOf(true)
        var commands = 0
        setContent {
            val native = LocalWindowInfo.current
            val window = remember { object : WindowInfo by native {
                override val isWindowFocused: Boolean get() = focused.value
            } }
            CompositionLocalProvider(LocalWindowInfo provides window) {
                Box(Modifier.naviampTelevisionPlaybackKeys(NowPlayingUi(id = "track", title = "Title", subtitle = "Artist", stateLabel = "Paused")) { commands++ }) {
                    Box(Modifier.testTag("focus").focusable())
                }
            }
        }
        onNodeWithTag("focus").performSemanticsAction(SemanticsActions.RequestFocus)
            .performKeyInput { keyDown(Key.MediaPlayPause) }
        runOnIdle { focused.value = false }
        waitForIdle()
        runOnIdle { focused.value = true }
        waitForIdle()
        // No release was delivered while backgrounded. The first foreground repeat must work,
        // then further repeats from that same hold must remain suppressed.
        onNodeWithTag("focus").performKeyInput { advanceEventTime(1_000); keyUp(Key.MediaPlayPause) }
        assertEquals(2, commands)
        onNodeWithTag("focus").performKeyInput { pressKey(Key.MediaPlayPause) }
        assertEquals(3, commands)
    }
}
