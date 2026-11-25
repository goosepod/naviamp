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

        onNodeWithText("v2.12.0", substring = true).assertIsDisplayed()
        onNodeWithText("Changelog").assertIsDisplayed().performClick()
        onNodeWithText("Latest Changes").assertIsDisplayed()
        onNodeWithText(NaviampAboutUi().changelog.first().entries.first()).assertIsDisplayed()
        val roots = onAllNodes(isRoot())
        var rendered: androidx.compose.ui.graphics.ImageBitmap? = null
        waitUntil(timeoutMillis = 5_000) {
            val image = roots[roots.fetchSemanticsNodes().lastIndex].captureToImage()
            val body = onNodeWithText(NaviampAboutUi().changelog.first().entries.first()).captureToImage()
            if (changelogBodyHasPainted(body)) {
                rendered = image
                true
            } else false
        }
        writeAboutReleaseImage(requireNotNull(rendered))
        onNodeWithText("Aurral connections now require Aurral 3.x. Aurral 2.x servers are no longer supported.").performScrollTo().assertIsDisplayed()
        onNodeWithText("Known issues").performScrollTo().assertIsDisplayed()
    }
}

private fun changelogBodyHasPainted(image: androidx.compose.ui.graphics.ImageBitmap): Boolean {
    val pixels = image.toPixelMap()
    var paintedPixels = 0
    // Semantics can update before the desktop frame paints. Require real text pixels
    // in the actual fix entry, independent of how many changes this release contains.
    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
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
    val file = java.io.File("build/reports/release-2.11.2/about-changelog.png")
    file.parentFile.mkdirs()
    javax.imageio.ImageIO.write(output, "png", file)
}
