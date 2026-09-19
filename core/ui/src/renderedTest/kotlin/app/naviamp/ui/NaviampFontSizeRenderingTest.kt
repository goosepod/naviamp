package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.naviamp.domain.settings.InterfaceFontSize
import app.naviamp.domain.settings.InterfaceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the same shared components on desktop and a real Android activity. */
@OptIn(ExperimentalTestApi::class)
class NaviampFontSizeRenderingTest {
    @Test
    fun miniPlayerQueueAndLyricsUseThePlayerScale() = runComposeUiTest {
        val general = mutableStateOf(InterfaceFontSize.Standard)
        val player = mutableStateOf(InterfaceFontSize.Standard)
        val surface = mutableStateOf(0)
        val nowPlaying = NowPlayingUi(
            title = "Player title", subtitle = "Artist", stateLabel = "Paused",
            upNext = listOf(NaviampNowPlayingItemUi("next", "Queue title", "Artist")),
        )
        val actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {})
        setContent {
            NaviampFontSizeScope(general.value, includeStandardScale = true) {
                NaviampFontSizeScope(player.value, general.value) {
                    Box(Modifier.size(280.dp, 420.dp)) {
                        when (surface.value) {
                            0 -> NaviampMiniNowPlaying(nowPlaying, NaviampColors(), {}, actions)
                            1 -> NaviampQueueContent(nowPlaying, null, NaviampColors(), actions)
                            2 -> CenteredLyricsList(
                                listOf(NaviampLyricLineUi(0, text = "Lyric line")), 0, null, 0,
                                NaviampColors(), rememberLazyListState(), {},
                            )
                        }
                    }
                }
            }
        }
        for ((index, title) in listOf("Player title", "Queue title", "Lyric line").withIndex()) {
            runOnIdle { surface.value = index; general.value = InterfaceFontSize.Standard; player.value = InterfaceFontSize.Standard }
            fun fontPixels(): Float {
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                return layouts.single().layoutInput.run { density.run { style.fontSize.toPx() } }
            }
            val standard = fontPixels()
            runOnIdle { general.value = InterfaceFontSize.Large }
            assertEquals(standard, fontPixels(), 0.01f)
            runOnIdle { player.value = InterfaceFontSize.Large }
            assertTrue(fontPixels() > standard)
            runOnIdle { player.value = InterfaceFontSize.Standard }
            assertEquals(standard, fontPixels(), 0.01f)
        }
    }

    @Test
    fun settingsControlsChangeAndResetEachPreferenceIndependentlyAtLargeSystemScale() = runComposeUiTest {
        val settings = mutableStateOf(InterfaceSettings())
        setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NaviampFontSizeScope(settings.value.generalFontSize, includeStandardScale = true) {
                    Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                        FontSizeSettings(NaviampColors(), settings.value, { settings.value = it })
                    }
                }
            }
        }
        onAllNodesWithText("Large")[0].performScrollTo().performClick()
        onAllNodesWithText("Small")[1].performScrollTo().performClick()
        runOnIdle {
            assertEquals(InterfaceFontSize.Large, settings.value.generalFontSize)
            assertEquals(InterfaceFontSize.Small, settings.value.nowPlayingFontSize)
        }
        onAllNodesWithText("Standard")[0].performScrollTo().performClick()
        runOnIdle { assertEquals(InterfaceFontSize.Small, settings.value.nowPlayingFontSize) }
        onAllNodesWithText("Standard")[1].performScrollTo().performClick()
        runOnIdle { assertEquals(InterfaceSettings(), settings.value) }
    }

    @Test
    fun renderedScopesStayIndependentAcrossEveryChoiceAndAccessibilityScale() = runComposeUiTest {
        val general = mutableStateOf(InterfaceFontSize.Standard)
        val player = mutableStateOf(InterfaceFontSize.Standard)
        val accessibility = mutableStateOf(1f)
        var generalPixels = 0f
        var playerPixels = 0f
        var physicalDensity = 0f
        var originalDensity = 0f
        setContent {
            originalDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(originalDensity, accessibility.value)) {
                NaviampFontSizeScope(general.value, includeStandardScale = true) {
                    physicalDensity = LocalDensity.current.density
                    Column {
                        Text("General", fontSize = 14.sp, onTextLayout = { generalPixels = it.layoutInput.density.run { 14.sp.toPx() } })
                        NaviampFontSizeScope(player.value, general.value) {
                            Text("Player", fontSize = 14.sp, onTextLayout = { playerPixels = it.layoutInput.density.run { 14.sp.toPx() } })
                        }
                    }
                }
            }
        }
        for (system in listOf(1f, 2f)) {
            for (generalSize in InterfaceFontSize.entries) {
                for (playerSize in InterfaceFontSize.entries) {
                    runOnIdle {
                        accessibility.value = system
                        general.value = generalSize
                        player.value = playerSize
                    }
                    waitForIdle()
                    runOnIdle {
                        assertEquals(originalDensity, physicalDensity)
                        assertEquals(Density(originalDensity, system * 1.08f * generalSize.scale).run { 14.sp.toPx() }, generalPixels, 0.01f)
                        assertEquals(Density(originalDensity, system * 1.08f * playerSize.scale).run { 14.sp.toPx() }, playerPixels, 0.01f)
                    }
                }
            }
        }
    }

    @Test
    fun shortMultilineDescriptionCanExpandCollapseAndResetForAnotherItem() = runComposeUiTest {
        val key = mutableStateOf("artist")
        val description = "First\nSecond\nThird\nFourth"
        setContent {
            Column(Modifier.width(280.dp)) {
                NaviampProviderDescription(description, key.value, NaviampColors())
            }
        }
        onNodeWithText("More…").performClick()
        onNodeWithText("Less").assertExists()
        val layouts = mutableListOf<TextLayoutResult>()
        onNodeWithText(description).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(4, layouts.single().lineCount)
        assertTrue(!layouts.single().hasVisualOverflow)
        onNodeWithText("Less").performClick()
        onNodeWithText("More…").assertExists()
        onNodeWithText("More…").performClick()
        runOnIdle { key.value = "album" }
        onNodeWithText("Less").assertDoesNotExist()
        onNodeWithText("More…").assertExists()
    }

    @Test
    fun expansionFollowsMeasuredWidthAndLiveFontChanges() = runComposeUiTest {
        val width = mutableStateOf(300.dp)
        val size = mutableStateOf(InterfaceFontSize.Small)
        val text = "Readable artist biography with words that wrap naturally across the available space."
        assertTrue(text.length < 260)
        setContent {
            NaviampFontSizeScope(size.value) {
                Column(Modifier.width(width.value)) {
                    NaviampProviderDescription(text, "artist", NaviampColors())
                }
            }
        }
        onNodeWithText("More…").assertDoesNotExist()
        runOnIdle { width.value = 140.dp; size.value = InterfaceFontSize.Large }
        onNodeWithText("More…").assertExists()
        runOnIdle { width.value = 300.dp; size.value = InterfaceFontSize.Small }
        onNodeWithText("More…").assertDoesNotExist()
    }
}
