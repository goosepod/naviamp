package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampContentInsetsTest {
    @Test fun keyboardResizesContentWithoutPanningOrDoubleCountingNavigationInsets() =
        runDesktopComposeUiTest(width = 412, height = 800) {
            val keyboardHeight = mutableStateOf(0)
            setContent {
                Box(Modifier.fillMaxSize().testTag("window")) {
                    Box(Modifier.fillMaxSize()
                        .naviampContentInsets(WindowInsets(top = 24, bottom = 20),
                            WindowInsets(bottom = keyboardHeight.value))) {
                        Column(Modifier.fillMaxSize().testTag("content")) {
                            Box(Modifier.fillMaxWidth().weight(1f).testTag("form"))
                            Box(Modifier.fillMaxWidth().height(androidx.compose.ui.unit.Dp(48f)).testTag("navigation"))
                        }
                    }
                }
            }
            val window = onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
            fun check(bottomInset: Int) {
                val content = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
                val navigation = onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
                assertEquals(window.top + 24f, content.top, 0.5f)
                assertEquals(window.bottom - bottomInset, content.bottom, 0.5f)
                assertEquals(content.bottom, navigation.bottom, 0.5f)
            }
            check(20)
            runOnIdle { keyboardHeight.value = 300 }
            check(300)
            runOnIdle { keyboardHeight.value = 360 }
            check(360)
            runOnIdle { keyboardHeight.value = 0 }
            check(20)
        }
}
