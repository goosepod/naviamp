package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Shared rendered checks also compile against the Android UI-test adapter. */
@OptIn(ExperimentalTestApi::class)
class NaviampRepeatControlsUiTest {
    @Test
    fun fullPlayerCyclesFromGreyOffThroughBothActiveModesAndBack() = runComposeUiTest {
        mainClock.autoAdvance = false
        val mode = mutableStateOf(NaviampRepeatMode.Off)
        val available = mutableStateOf(true)
        var actions = 0
        setContent {
            Box(Modifier.size(500.dp, 700.dp).background(NaviampColors.Dark.background)) {
                NaviampNowPlayingPanel(
                    nowPlaying = NowPlayingUi(id = "fixture", title = "Track", subtitle = "Artist",
                        stateLabel = "Paused", canRepeat = available.value, repeatMode = mode.value),
                    colors = NaviampColors.Dark,
                    actions = NaviampNowPlayingActions({ request ->
                        if (request.action == NowPlayingPlaybackAction.CycleRepeatMode) {
                            actions++
                            mode.value = when (mode.value) {
                                NaviampRepeatMode.Off -> NaviampRepeatMode.Queue
                                NaviampRepeatMode.Queue -> NaviampRepeatMode.Track
                                NaviampRepeatMode.Track -> NaviampRepeatMode.Off
                            }
                        }
                    }, {}, {}, {}, {}, {}, {}),
                )
            }
        }
        mainClock.advanceTimeBy(100)
        val off = onNodeWithContentDescription("Repeat off").assertIsEnabled().captureToImage().peakLuminance()
        onNodeWithContentDescription("Repeat off").performClick()
        mainClock.advanceTimeByFrame()
        val all = onNodeWithContentDescription("Repeat queue").assertIsEnabled().captureToImage().peakLuminance()
        assertTrue(all > off + 0.25f, "Repeat Off must visibly dim the icon: off=$off, all=$all")
        onNodeWithText("A").assertDoesNotExist()
        onNodeWithContentDescription("Repeat queue").performClick()
        mainClock.advanceTimeByFrame()
        onNodeWithContentDescription("Repeat current track").assertIsEnabled().performClick()
        mainClock.advanceTimeByFrame()
        onNodeWithContentDescription("Repeat off").assertIsEnabled()
        runOnIdle { assertEquals(3, actions); available.value = false }
        mainClock.advanceTimeByFrame()
        onNodeWithContentDescription("Repeat off").assertIsNotEnabled().performClick()
        runOnIdle { assertEquals(3, actions) }
    }

    @Test
    fun oneKeepsTheSameLoopAndAddsALegibleCenterAt20And24Dp() = runComposeUiTest {
        val one = mutableStateOf(false)
        val size = mutableStateOf(20)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                Box(Modifier.size(44.dp).background(Color.Black)) {
                    NaviampTransportIconButton(
                        enabled = true,
                        icon = naviampRepeatIcon(if (one.value) NaviampRepeatMode.Track else NaviampRepeatMode.Queue),
                        contentDescription = "Fixture repeat", colors = NaviampColors.Dark,
                        iconSize = size.value.dp, onClick = {},
                    )
                }
            }
        }
        for (pixels in listOf(20, 24)) {
            runOnIdle { size.value = pixels; one.value = false }
            val all = onNodeWithContentDescription("Fixture repeat").captureToImage().toPixelMap()
            runOnIdle { one.value = true }
            val track = onNodeWithContentDescription("Fixture repeat").captureToImage().toPixelMap()
            var extraCenterPixels = 0
            for (y in 0 until all.height) for (x in 0 until all.width) {
                val center = x in (all.width * .38f).toInt()..(all.width * .56f).toInt() &&
                    y in (all.height * .31f).toInt()..(all.height * .59f).toInt()
                if (center && track[x, y].luminance() > all[x, y].luminance() + .15f) extraCenterPixels++
                if (!center) assertEquals(all[x, y], track[x, y], "Loop changed at $pixels dp: $x,$y")
            }
            assertTrue(extraCenterPixels >= 3, "The centered 1 vanished at $pixels dp")
        }
    }

    @Test
    fun televisionOffIsMutedEvenWithTheHighContrastTextPalette() = runComposeUiTest {
        val selected = mutableStateOf(false)
        setContent {
            Box(Modifier.size(60.dp).background(NaviampTelevisionColors.background)) {
                TelevisionIconButton(
                    enabled = true, icon = NaviampTransportIcons.Repeat, description = "Fixture TV repeat",
                    colors = NaviampTelevisionColors, selected = selected.value,
                    inactive = !selected.value, onClick = { selected.value = !selected.value },
                )
            }
        }
        val off = onNodeWithContentDescription("Fixture TV repeat").captureToImage().peakLuminance()
        onNodeWithContentDescription("Fixture TV repeat").assertIsEnabled().performClick()
        val active = onNodeWithContentDescription("Fixture TV repeat").captureToImage().peakLuminance()
        assertTrue(active > off + .25f, "TV Off must remain distinct with its white muted-text palette")
    }

    private fun ImageBitmap.peakLuminance(): Float {
        val pixels = toPixelMap()
        var peak = 0f
        for (y in 0 until height) for (x in 0 until width) peak = maxOf(peak, pixels[x, y].luminance())
        return peak
    }
}
