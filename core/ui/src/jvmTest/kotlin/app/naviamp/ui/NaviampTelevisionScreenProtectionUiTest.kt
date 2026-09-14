package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import kotlin.test.assertTrue
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampTelevisionScreenProtectionUiTest {
    @Test fun interactiveControlsFit720pAndExposeRepeatState() = captureInteractiveControls(1280, 720, 1f)
    @Test fun interactiveControlsFitNative4kAndExposeRepeatState() = captureInteractiveControls(3840, 2160, 3f)

    @Test fun televisionTextPaletteHasReadableContrastOnItsBaseSurface() {
        val colors = NaviampColors.Dark
        for (text in listOf(colors.primaryText, colors.secondaryText, colors.mutedText)) {
            val ratio = (text.luminance() + 0.05f) / (colors.background.luminance() + 0.05f)
            assertTrue(ratio >= 4.5f, "TV text contrast: $ratio")
        }
    }

    private fun captureInteractiveControls(width: Int, height: Int, density: Float) = runDesktopComposeUiTest(width, height) {
        mainClock.autoAdvance = false
        val repeat = mutableStateOf(NaviampRepeatMode.Queue)
        var commands = 0
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                Box(Modifier.fillMaxSize().background(NaviampColors.Dark.background)) {
                    TelevisionNowPlaying(
                        nowPlaying = NowPlayingUi(id = "track", title = "Playback recovery", subtitle = "Naviamp Artist",
                            stateLabel = "Playing", isPlaying = true, canRepeat = true, repeatMode = repeat.value,
                            waveform = app.naviamp.domain.waveform.AudioWaveform(List(320) { (it % 17 + 1) / 18f }),
                            positionSeconds = 73.0, durationSeconds = 180.0),
                        playbackProgress = null, colors = NaviampColors.Dark,
                        actions = actions {
                            if (it.action == NowPlayingPlaybackAction.CycleRepeatMode) {
                                commands++
                                repeat.value = NaviampRepeatMode.Track
                            }
                        }, onClose = {}, onOpenSettings = {},
                    )
                }
            }
        }
        mainClock.advanceTimeBy(300)
        onNodeWithContentDescription("Repeat all").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.RequestFocus)
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        mainClock.advanceTimeBy(200)
        assertEquals(1, commands)
        onNodeWithContentDescription("Repeat one").assertIsDisplayed().assertIsFocused()
        onNodeWithText("1").assertIsDisplayed()
        onNodeWithTag(TelevisionNowPlayingScrubberTestTag).assertIsDisplayed()
        val pixels = onRoot().captureToImage().toPixelMap()
        val snapshot = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/television-controls/interactive-${width}x$height.png")
        output.parentFile.mkdirs()
        ImageIO.write(snapshot, "png", output)
    }

    @Test fun shiftedListeningLayoutFits720p() = captureShiftedListeningLayout(1280, 720, 1f)
    @Test fun shiftedListeningLayoutFits4k() = captureShiftedListeningLayout(3840, 2160, 3f)

    private fun captureShiftedListeningLayout(width: Int, height: Int, density: Float) = runDesktopComposeUiTest(width, height) {
        mainClock.autoAdvance = false
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
                Box(Modifier.fillMaxSize().background(NaviampColors.Dark.background)) {
                    TelevisionScreenProtectionSurface(televisionScreenProtection(180_000), Modifier.fillMaxSize()) {
                    TelevisionNowPlaying(
                        nowPlaying = NowPlayingUi(id = "track", title = "A Long Listening Session", subtitle = "Naviamp Artist",
                            albumTitle = "Evening Music", stateLabel = "Playing", isPlaying = true,
                            positionSeconds = 123.0, durationSeconds = 360.0),
                        playbackProgress = null, colors = NaviampColors.Dark, interactive = false,
                        actions = actions(), onClose = {}, onOpenSettings = {},
                    )
                    }
                }
            }
        }
        mainClock.advanceTimeBy(3_000)
        onNodeWithText("A Long Listening Session").assertIsDisplayed()
        onNodeWithText("Naviamp Artist").assertIsDisplayed()
        onNodeWithText("Evening Music").assertIsDisplayed()
        onNodeWithTag(TelevisionNowPlayingScrubberTestTag).assertIsDisplayed()
        val pixels = onRoot().captureToImage().toPixelMap()
        val snapshot = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) for (x in 0 until width) snapshot.setRGB(x, y, pixels[x, y].toArgb())
        val output = File("build/reports/television-screen-protection/listening-${width}x$height.png")
        output.parentFile.mkdirs()
        ImageIO.write(snapshot, "png", output)
    }

    @Test
    fun fullScreenDimmingSurvivesTrackChangesAndWakeDoesNotSendPlayback() = runComposeUiTest {
        mainClock.autoAdvance = false
        val track = mutableStateOf("first")
        val playback = mutableListOf<NowPlayingPlaybackActionRequest>()
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(id = track.value, title = track.value, subtitle = "Artist", stateLabel = "Playing",
                    isPlaying = true, canPlayPause = true),
                playbackProgress = null, colors = NaviampColors.Dark,
                actions = actions(playback::add), onClose = {}, onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(TelevisionNowPlayingControlsTimeoutMillis + TelevisionScreenProtectionDelayMillis + 3_000)
        onNodeWithTag("television-now-playing-protection").assert(
            SemanticsMatcher.expectValue(TelevisionScreenProtectionBrightness, 0.55f))
        track.value = "second"
        mainClock.advanceTimeBy(100)
        onNodeWithTag("television-now-playing-protection").assert(
            SemanticsMatcher.expectValue(TelevisionScreenProtectionBrightness, 0.55f))
        onNodeWithTag(TelevisionNowPlayingListeningModeTestTag)
            .performKeyInput { pressKey(Key.DirectionCenter) }
        mainClock.advanceTimeBy(100)
        onNodeWithTag("television-now-playing-protection").assert(
            SemanticsMatcher.expectValue(TelevisionScreenProtectionBrightness, 1f))
        assertEquals(emptyList(), playback)
    }

    @Test
    fun openQueueKeepsNormalBrightnessDuringLongInactivity() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(id = "track", title = "Track", subtitle = "Artist", stateLabel = "Playing",
                    isPlaying = true, canPlayPause = true, queueCurrentIndex = 0,
                    upNext = listOf(NaviampNowPlayingItemUi("next", "Next", "Artist"))),
                playbackProgress = null, colors = NaviampColors.Dark,
                actions = actions(), onClose = {}, onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(200)
        onNodeWithContentDescription("Queue").performClick()
        mainClock.advanceTimeBy(TelevisionScreenProtectionDelayMillis + 3_000)
        onNodeWithTag("television-now-playing-protection").assert(
            SemanticsMatcher.expectValue(TelevisionScreenProtectionBrightness, 1f))
        onNodeWithTag(TelevisionNowPlayingQueueCurrentTestTag).assertExists()
    }

    @Test
    fun previewDimsWithoutMakingControlsInteractive() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            TelevisionNowPlaying(
                nowPlaying = NowPlayingUi(id = "track", title = "Track", subtitle = "Artist", stateLabel = "Paused"),
                playbackProgress = null, colors = NaviampColors.Dark, interactive = false,
                actions = actions(), onClose = {}, onOpenSettings = {},
            )
        }
        mainClock.advanceTimeBy(TelevisionScreenProtectionDelayMillis + 3_000)
        onNodeWithTag("television-now-playing-protection").assert(
            SemanticsMatcher.expectValue(TelevisionScreenProtectionBrightness, 0.55f))
        onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    private fun actions(onPlayback: (NowPlayingPlaybackActionRequest) -> Unit = {}) = NaviampNowPlayingActions(
        onPlaybackAction = onPlayback, onDisplayAction = {}, onCurrentTrackAction = {}, onQueueAction = {},
        onSleepTimerAction = {}, onSelectionAction = {}, onQueueItemAction = {},
    )
}
