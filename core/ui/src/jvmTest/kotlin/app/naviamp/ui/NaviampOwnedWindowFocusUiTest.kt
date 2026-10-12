package app.naviamp.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampOwnedWindowFocusUiTest {
    @Test fun ownerActivationChangesFocusWithoutReplacingNativeGeometry() = runComposeUiTest {
        var groupActive by mutableStateOf<Boolean?>(true)
        var nativeActive by mutableStateOf(false)
        var observedFocus = false
        var observedSize = IntSize.Zero
        val native = object : WindowInfo {
            override val isWindowFocused: Boolean get() = nativeActive
            override val containerSize: IntSize = IntSize(700, 500)
        }
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides native, LocalNaviampWindowGroupActive provides groupActive) {
                NaviampOwnedWindowFocusScope {
                    val info = LocalWindowInfo.current
                    val focused = info.isWindowFocused
                    val size = info.containerSize
                    SideEffect { observedFocus = focused; observedSize = size }
                }
            }
        }
        runOnIdle { assertTrue(observedFocus); assertEquals(native.containerSize, observedSize); groupActive = false }
        waitForIdle()
        runOnIdle { assertFalse(observedFocus); groupActive = null }
        waitForIdle()
        runOnIdle { assertFalse(observedFocus); nativeActive = true }
        waitForIdle()
        runOnIdle { assertTrue(observedFocus); assertEquals(native.containerSize, observedSize) }
    }
}
