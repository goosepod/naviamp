package app.naviamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampRadioDjEditorTest {
    @Test
    fun newDjFitsADesktopSplitPanel() = runDesktopComposeUiTest(width = 420, height = 900) {
        setContent {
            var editorOpen by remember { mutableStateOf(false) }
            NaviampPlaybackSettingsSection(
                colors = NaviampColors.Dark,
                playbackSettings = PlaybackSettings(),
                djEditorOpen = editorOpen,
                onDjEditorOpenChanged = { editorOpen = it },
                supportsReplayGain = true,
                supportsGapless = true,
                supportsCrossfade = true,
                supportsEqualizer = true,
                onPlaybackSettingsChanged = {},
            )
        }
        onNodeWithText("Radio DJs").performClick()
        onNodeWithText("New DJ").performClick()
        onAllNodesWithText("New DJ").assertCountEquals(1)
        val pixels = onRoot().captureToImage().toPixelMap()
        val snapshot = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = java.io.File("build/reports/dj-editor/new-dj-split-panel.png")
        output.parentFile.mkdirs()
        javax.imageio.ImageIO.write(snapshot, "png", output)
    }

    @Test
    fun newDjHasOnePageHeadingAndDisabledSaveUntilNamed() = runComposeUiTest {
        setContent {
            var editorOpen by remember { mutableStateOf(false) }
            NaviampPlaybackSettingsSection(
                colors = NaviampColors.Dark,
                playbackSettings = PlaybackSettings(),
                djEditorOpen = editorOpen,
                onDjEditorOpenChanged = { editorOpen = it },
                supportsReplayGain = true,
                supportsGapless = true,
                supportsCrossfade = true,
                supportsEqualizer = true,
                onPlaybackSettingsChanged = {},
            )
        }
        onNodeWithText("Radio DJs").performClick()
        onNodeWithText("New DJ").performClick()
        onAllNodesWithText("Radio DJs").assertCountEquals(0)
        onAllNodesWithText("New DJ").assertCountEquals(1)
        onNodeWithText("Save").assertIsNotEnabled()
        val pixels = onRoot().captureToImage().toPixelMap()
        val snapshot = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = java.io.File("build/reports/dj-editor/new-dj.png")
        output.parentFile.mkdirs()
        javax.imageio.ImageIO.write(snapshot, "png", output)
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Radio DJs").assertExists()
    }
}
