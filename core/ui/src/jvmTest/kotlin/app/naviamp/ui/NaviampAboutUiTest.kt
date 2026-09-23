package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.naviamp.domain.network.NaviampAppBuildNumber
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampAboutUiTest {
    @Test
    fun defaultAboutInfoUsesSharedBuildNumber() {
        assertEquals(NaviampAppBuildNumber, NaviampAboutUi().buildNumber)
    }

    @Test
    fun changelogRendersTheCurrentReleaseInAbout() = runDesktopComposeUiTest(720, 900) {
        setContent {
            val colors = NaviampColors.Dark
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = colors.background,
                    surface = colors.controlSurface,
                    primary = colors.accent,
                    onPrimary = colors.onAccent,
                    onBackground = colors.primaryText,
                    onSurface = colors.primaryText,
                ),
                typography = rememberNaviampTypography(),
            ) {
                NaviampAboutSettingsSection(colors = colors, about = NaviampAboutUi())
            }
        }

        onNodeWithText("Changelog").assertIsDisplayed().performClick()
        onNodeWithText("Latest Changes").assertIsDisplayed()
        onNodeWithText("Fixed popup menus and dialogs jumping, flickering, and responding to clicks in the wrong location.").assertIsDisplayed()
    }
}
