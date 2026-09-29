package app.naviamp.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.runComposeUiTest
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampRadioDjEditorTest {
    @Test
    fun compactPlayerOpensDjEditorDirectly() = runDesktopComposeUiTest(width = 360, height = 640) {
        val editorOpen = mutableStateOf(false)
        setContent {
            NaviampNowPlayingPanel(
                nowPlaying = NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Playing"),
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}, onCreateRadioDj = { editorOpen.value = true }),
                panelLayout = NaviampPlayerPanelLayout.Standalone,
            )
            if (editorOpen.value) {
                NaviampRadioDjCreationDialog(
                    colors = NaviampColors.Dark,
                    playbackSettings = PlaybackSettings(),
                    onPlaybackSettingsChanged = {},
                    onDismissRequest = { editorOpen.value = false },
                )
            }
        }

        onNodeWithContentDescription("New DJ").performClick()
        onNodeWithText("DJ name").assertExists()
        onNodeWithText("Cancel").performScrollTo().performClick()
        assertFalse(editorOpen.value)
    }

    @Test
    fun nowPlayingCreatesAndCancelsDjsWithoutChangingPlayback() = runDesktopComposeUiTest(width = 420, height = 900) {
        val settings = mutableStateOf(PlaybackSettings())
        val editorOpen = mutableStateOf(false)
        var playbackCommands = 0
        setContent {
            NaviampNowPlayingPanel(
                nowPlaying = NowPlayingUi(
                    id = "song",
                    title = "Song",
                    subtitle = "Artist",
                    stateLabel = "Playing",
                    radioDjs = settings.value.radioDjs,
                ),
                colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions(
                    onPlaybackAction = { playbackCommands++ },
                    onDisplayAction = {},
                    onCurrentTrackAction = {},
                    onQueueAction = {},
                    onSleepTimerAction = {},
                    onSelectionAction = {},
                    onQueueItemAction = {},
                    onCreateRadioDj = { editorOpen.value = true },
                ),
                panelLayout = NaviampPlayerPanelLayout.Standalone,
            )
            if (editorOpen.value) {
                NaviampRadioDjCreationDialog(
                    colors = NaviampColors.Dark,
                    playbackSettings = settings.value,
                    onPlaybackSettingsChanged = { settings.value = it },
                    onDismissRequest = { editorOpen.value = false },
                )
            }
        }

        onNodeWithContentDescription("New DJ").performClick()
        onNodeWithText("DJ name").performTextInput("Evening")
        onNodeWithText("Save").performClick()
        waitForIdle()
        assertEquals("Evening", settings.value.radioDjs.single().name)
        assertFalse(editorOpen.value)
        assertEquals(0, playbackCommands)

        onNodeWithContentDescription("DJs").performClick()
        onNodeWithText("Evening").assertExists()
        onNodeWithText("Create new DJ").performClick()
        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertEquals(1, settings.value.radioDjs.size)
        assertFalse(editorOpen.value)
        assertEquals(0, playbackCommands)
    }

    @Test
    fun newDjWrapsOptionsAtLargerTextSize() = runDesktopComposeUiTest(width = 420, height = 900) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
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
        }
        onNodeWithText("Radio DJs").performClick()
        onNodeWithText("New DJ").performClick()
        onNodeWithText("Artist blocks").assertExists()
        val pixels = onRoot().captureToImage().toPixelMap()
        val snapshot = java.awt.image.BufferedImage(pixels.width, pixels.height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = java.io.File("build/reports/dj-editor/new-dj-large-text.png")
        output.parentFile.mkdirs()
        javax.imageio.ImageIO.write(snapshot, "png", output)
    }

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
