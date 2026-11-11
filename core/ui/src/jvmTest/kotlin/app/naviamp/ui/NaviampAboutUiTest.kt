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
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
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

        onNodeWithText("v2.11.1", substring = true).assertIsDisplayed()
        onNodeWithText("Changelog").assertIsDisplayed().performClick()
        onNodeWithText("Latest Changes").assertIsDisplayed()
        onNodeWithText("Open Stream Details from the Now Playing menu for internet radio to inspect available station, broadcast, audio quality, artwork, and additional metadata. Details update while the stream plays and support copying text.").assertIsDisplayed()
        val roots = onAllNodes(isRoot())
        var rendered: androidx.compose.ui.graphics.ImageBitmap? = null
        waitUntil(timeoutMillis = 5_000) {
            val image = roots[roots.fetchSemanticsNodes().lastIndex].captureToImage()
            if (changelogBodyHasPainted(image)) {
                rendered = image
                true
            } else false
        }
        writeAboutReleaseImage(requireNotNull(rendered))
        onNodeWithText("Choose a fresh queue order when shuffle is enabled again, while restoring the original upcoming order when shuffle is turned off.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("Reuse equivalent generated Radio stations in Recently Played Radio, refresh recency on replay, and consolidate existing duplicate entries while preserving distinct stations.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("No special upgrade steps are required.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("The signed Flatpak currently contains v2.10.0; newer updates await signing. Use the direct Linux downloads for this release. Store and beta channel versions may differ from GitHub.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("Known issues").performScrollTo().assertIsDisplayed()
    }
}

private fun changelogBodyHasPainted(image: androidx.compose.ui.graphics.ImageBitmap): Boolean {
    val pixels = image.toPixelMap()
    var paintedPixels = 0
    // Semantics can update before the desktop frame paints. Require real text pixels
    // in the lower body, beyond the header and first changelog entry.
    for (y in pixels.height / 2 until pixels.height) for (x in 0 until pixels.width) {
        val pixel = pixels[x, y]
        if (pixel.alpha > 0.5f && maxOf(pixel.red, pixel.green, pixel.blue) > 0.2f) {
            if (++paintedPixels >= 100) return true
        }
    }
    return false
}

private fun writeAboutReleaseImage(image: androidx.compose.ui.graphics.ImageBitmap) {
    val pixels = image.toPixelMap()
    val output = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) output.setRGB(x, y, pixels[x, y].toArgb())
    val file = java.io.File("build/reports/release-2.11.1/about-changelog.png")
    file.parentFile.mkdirs()
    javax.imageio.ImageIO.write(output, "png", file)
}
