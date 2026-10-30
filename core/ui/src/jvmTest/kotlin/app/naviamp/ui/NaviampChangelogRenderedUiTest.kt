package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampChangelogRenderedUiTest {
    @Test fun actualAboutChangelogRendersAllCurrentReleaseEntriesAndScrollsToTheEnd() = runComposeUiTest {
        val about = NaviampAboutUi()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.width(720.dp).height(650.dp).background(NaviampColors.Dark.background)
                    .verticalScroll(rememberScrollState())) {
                    NaviampAboutSettingsSection(NaviampColors.Dark, about)
                }
            }
        }
        onNodeWithText("Changelog").performScrollTo().performClick()
        onNodeWithText("Latest Changes").assertExists()
        val entries = about.changelog.flatMap { it.entries }
        entries.forEach { entry -> onNodeWithText(entry).performScrollTo().assertIsDisplayed() }
        System.getenv("NAVIAMP_CHANGELOG_CAPTURE")?.let { directory ->
            java.io.File(directory).mkdirs()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "about-changelog-end.png"))
            onNodeWithText(entries.first()).performScrollTo()
            javax.imageio.ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", java.io.File(directory, "about-changelog-start.png"))
        }
    }

    @Test fun televisionAboutNavigatesTheActualReleaseEntriesWithoutTruncation() = runDesktopComposeUiTest(850, 480) {
        val about = NaviampAboutUi()
        setContent { TelevisionAboutSettings(NaviampAppShellUiState(general = NaviampGeneralSettingsUi(about = about)),
            NaviampColors.Dark, FocusRequester()) }
        onNodeWithText("Naviamp").performSemanticsAction(SemanticsActions.RequestFocus)
        about.changelog.flatMap { it.entries }.forEach { entry ->
            onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            onNodeWithText(entry).assertIsFocused().assertIsDisplayed()
        }
    }
}
