package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampRecoveryFailureScreenTest {
    @Test fun remoteDefaultsToRetryThenCancelBeforeConfirmedReset() = checkRecovery(NaviampApplicationSurface.Standard)
    @Test fun televisionRemoteDefaultsToRetryThenCancelBeforeConfirmedReset() = checkRecovery(NaviampApplicationSurface.Television)

    private fun checkRecovery(surface: NaviampApplicationSurface) = runDesktopComposeUiTest(1280, 720) {
        val confirming = mutableStateOf(false)
        var resets = 0
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalNaviampApplicationSurface provides surface) {
                NaviampRecoveryFailureScreen(NaviampRecoveryFailureUi.Unreadable, false, false, confirming.value,
                    {}, {}, { confirming.value = true }, { resets++ }, { confirming.value = false })
                }
            }
        }
        onNodeWithTag("storage-recovery-retry").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag("storage-recovery-reset").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("storage-recovery-cancel").assertIsFocused()
        assertEquals(0, resets)
        onNodeWithTag("storage-recovery-cancel").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag("storage-recovery-confirm").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        assertEquals(1, resets)
    }
}
