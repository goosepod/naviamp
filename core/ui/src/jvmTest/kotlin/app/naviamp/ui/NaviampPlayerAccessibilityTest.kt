package app.naviamp.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampPlayerAccessibilityTest {
    @Test fun progressHasANameAndRetainsLiveRangeAndSeekAction() = runComposeUiTest {
        val progress = mutableFloatStateOf(.2f)
        val seeks = mutableListOf<Float>()
        setContent {
            WaveformScrubber(emptyList(), .2f, drawValue = { progress.floatValue }, enabled = true,
                colors = NaviampColors(), onValueChange = { progress.floatValue = it },
                onValueChangeFinished = { seeks += it },
                modifier = Modifier.width(200.dp).height(30.dp).testTag("progress"))
        }
        val slider = onNodeWithContentDescription("Playback position")
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(.2f, 0f..1f))
        runOnIdle { progress.floatValue = .4f }
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(.4f, 0f..1f))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(.7f) }
        runOnIdle { assertEquals(listOf(.7f), seeks) }
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(.7f, 0f..1f))
    }

    @Test fun disabledProgressRetainsItsNameWithoutExposingSeek() = runComposeUiTest {
        setContent {
            WaveformScrubber(emptyList(), .3f, enabled = false, colors = NaviampColors(),
                onValueChange = { error("Disabled progress changed") }, onValueChangeFinished = {},
                modifier = Modifier.width(200.dp).height(30.dp))
        }
        val slider = onNodeWithContentDescription("Playback position")
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(.3f, 0f..1f))
        assertFalse(SemanticsActions.SetProgress in slider.fetchSemanticsNode().config)
    }

    @Test fun volumeExposesItsOwnNameLiveRangeAndBoundedAction() = runDesktopComposeUiTest(1280, 720) {
        val volumes = mutableListOf<Int>()
        setContent {
            NaviampNowPlayingPanel(
                NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused",
                    volumePercent = 50, canChangeVolume = true), colors = NaviampColors(),
                actions = NaviampNowPlayingActions(onPlaybackAction = { request ->
                    if (request.action == NowPlayingPlaybackAction.ChangeVolume) volumes += checkNotNull(request.volumePercent)
                }, {}, {}, {}, {}, {}, {}), panelLayout = NaviampPlayerPanelLayout.Full,
            )
        }
        val slider = onNodeWithTag("now-playing-volume-slider")
        slider.assertContentDescriptionEquals("Volume")
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(.5f, 0f..1f))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(-1f) }
        slider.assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
        assertEquals(listOf(100, 0), volumes)
    }

    @Test fun volumeKeyboardSeeksAndFocusTraversalWorkInBothLayoutDirections() {
        for (direction in LayoutDirection.entries) runDesktopComposeUiTest(1280, 720) {
            val volumes = mutableListOf<Int>()
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    NaviampNowPlayingPanel(
                        NowPlayingUi(id = "song", title = "Song", subtitle = "Artist", stateLabel = "Paused",
                            volumePercent = 50, canChangeVolume = true), colors = NaviampColors(),
                        actions = NaviampNowPlayingActions(onPlaybackAction = { request ->
                            if (request.action == NowPlayingPlaybackAction.ChangeVolume) volumes += checkNotNull(request.volumePercent)
                        }, {}, {}, {}, {}, {}, {}), panelLayout = NaviampPlayerPanelLayout.Full,
                    )
                }
            }
            val slider = onNodeWithTag("now-playing-volume-slider")
            slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            slider.assertIsFocused()
            slider.performKeyInput { pressKey(Key.DirectionRight) }
            assertEquals(55, volumes.last(), "Right must follow the visible left-to-right volume track in $direction")
            slider.performKeyInput { pressKey(Key.MoveEnd); pressKey(Key.DirectionUp) }
            slider.assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
            slider.performKeyInput { pressKey(Key.MoveHome); pressKey(Key.DirectionDown) }
            slider.assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))
            slider.performKeyInput { pressKey(Key.Tab) }
            slider.assertIsNotFocused()
        }
    }
}
