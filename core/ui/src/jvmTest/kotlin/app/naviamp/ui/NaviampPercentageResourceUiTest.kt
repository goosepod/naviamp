package app.naviamp.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.ui.generated.resources.Res
import app.naviamp.ui.generated.resources.settings_now_playing_split_background_opacity_value
import kotlin.test.Test
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalTestApi::class)
class NaviampPercentageResourceUiTest {
    @Test
    fun splitViewBackgroundOpacityHasOnePercentSign() = runDesktopComposeUiTest {
        setContent {
            Text(stringResource(Res.string.settings_now_playing_split_background_opacity_value, 0))
        }

        onNodeWithText("0%", substring = false).assertIsDisplayed()
        onAllNodesWithText("0%%", substring = false).assertCountEquals(0)
    }
}
