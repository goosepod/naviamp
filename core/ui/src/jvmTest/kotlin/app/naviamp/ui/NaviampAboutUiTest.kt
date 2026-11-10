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

        onNodeWithText("Changelog").assertIsDisplayed().performClick()
        onNodeWithText("Latest Changes").assertIsDisplayed()
        onNodeWithText("Browse a dedicated Aurral hub with full Recommendations and Search pages, related artists, releases and editions, track previews, Trending charts and genre blocking. Track requests through waiting-for-release, acquisition and validated Library availability, including after reconnecting.").assertIsDisplayed()
        onNodeWithText("Install the Linux app as an official signed x86_64 Flatpak, with repository-managed updates and private application storage.").assertIsDisplayed()
        onNodeWithText("Choose square album artwork corners and an artwork layout that fills the Now Playing artwork area. The shared settings follow export, import and sync.").assertIsDisplayed()
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
        onNodeWithText("Stop the old stream immediately when manually skipping to a track that takes time to load.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("Save the final paused playback position so a later restart restores the current checkpoint.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("The local database upgrades automatically to store Aurral request history. Existing connections, settings and album playback profiles are retained.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("The signed Flatpak currently packages v2.10.0. Its v2.11.0 update is pending access to the existing signing environment; use the direct Linux download for v2.11.0 until that update is published.")
            .performScrollTo().assertIsDisplayed()
        onNodeWithText("Android TV playback can restart after a stream failure or Wi-Fi interruption. Authenticated streams without finite byte ranges or provider time offsets retain buffered-seeking limitations.")
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
    val file = java.io.File("build/reports/release-2.11/about-changelog.png")
    file.parentFile.mkdirs()
    javax.imageio.ImageIO.write(output, "png", file)
}
