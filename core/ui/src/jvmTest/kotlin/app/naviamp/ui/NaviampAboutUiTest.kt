package app.naviamp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    NaviampAboutSettingsSection(colors = colors, about = NaviampAboutUi())
                }
            }
        }

        onNodeWithText("Changelog").assertIsDisplayed().performClick()
        onNodeWithText("Latest Changes").assertIsDisplayed()
        onNodeWithText("Cast online music from desktop to a Google Cast receiver, control playback and seeking, and return to local listening at the current position.").assertIsDisplayed()
        onNodeWithText("Keep albums and artists downloaded, preview storage needs, and reuse tracks already saved by another collection.").assertIsDisplayed()
        onNodeWithText("Keep macOS animation layers aligned after resize and prevent Stats for Nerds from redrawing the main window; improve playback-control accessibility.")
            .performScrollTo().assertIsDisplayed()
    }
}
