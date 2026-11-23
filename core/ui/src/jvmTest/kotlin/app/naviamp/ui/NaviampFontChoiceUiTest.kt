package app.naviamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.SystemFont
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.naviamp.domain.playback.EqualizerSettings
import app.naviamp.domain.settings.InterfaceFontFamily
import app.naviamp.domain.settings.InterfaceFontSize
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.hostOs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders under the app typography so results never depend on this machine's default font. */
@OptIn(ExperimentalTestApi::class)
class NaviampFontChoiceUiTest {
    private val colors = NaviampColors.Dark
    private val actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {})
    private val nowPlaying = NowPlayingUi(
        id = "song", title = "Song title", subtitle = "Song artist", stateLabel = "Paused", albumLine = "Song album",
        durationSeconds = 240.0, positionSeconds = 30.0,
        upNext = listOf(NaviampNowPlayingItemUi("next", "Queued title", "Queued artist")),
    )

    @Test
    fun everyFontChoiceReachesEveryTextOnRepresentativeScreensWithoutARestart() = runDesktopComposeUiTest(1100, 900) {
        val settings = mutableStateOf(InterfaceSettings())
        var themeFamily: FontFamily? = null
        setContent {
            MaterialTheme(typography = rememberNaviampTypography(settings.value)) {
                themeFamily = MaterialTheme.typography.bodyLarge.fontFamily
                NaviampFontSizeScope(settings.value.generalFontSize, includeStandardScale = true) {
                    Row {
                        Column(Modifier.width(380.dp).verticalScroll(rememberScrollState())) {
                            TrackRow(
                                SharedTrackRowUi("track", "Track title", "Track artist", durationLabel = "3:41", canToggleFavorite = false),
                                colors, null, false, false, false, false, false, showCoverArt = false, trackNumber = 7,
                            )
                            NaviampMiniNowPlaying(nowPlaying, colors, {}, actions)
                            NaviampProviderDescription("An artist biography.", "artist", colors)
                            NaviampCompactSearchField("Query", "Search", colors, {}, {})
                            SelectableSettingsRow(colors, "Selectable setting", "A setting subtitle", selected = true) {}
                        }
                        Box(Modifier.width(700.dp).fillMaxHeight()) {
                            NaviampNowPlayingPanel(nowPlaying, colors = colors, actions = actions, panelLayout = NaviampPlayerPanelLayout.Full)
                        }
                    }
                }
            }
        }
        val resolved = mutableMapOf<InterfaceFontFamily, FontFamily>()
        for (family in InterfaceFontFamily.entries) {
            runOnIdle { settings.value = InterfaceSettings(fontFamily = family) }
            waitForIdle()
            val expected = assertNotNull(themeFamily)
            resolved[family] = expected
            val layouts = textLayouts()
            assertTrue(layouts.size >= 12, "Representative screens must render text: ${layouts.map { it.first }}")
            for ((text, layout) in layouts) {
                assertEquals(expected, layout.layoutInput.style.fontFamily, "$family must reach '$text'")
                assertEquals(
                    if (family == InterfaceFontFamily.Inter) "tnum" else null,
                    layout.layoutInput.style.fontFeatureSettings,
                    "$family numerals for '$text'",
                )
            }
        }
        assertEquals(InterfaceFontFamily.entries.size, resolved.values.toSet().size)
    }

    @Test
    fun interKeepsChangingTimesAtAFixedWidth() = runDesktopComposeUiTest(400, 200) {
        var widths = emptyList<Int>()
        setContent {
            MaterialTheme(typography = rememberNaviampTypography(InterfaceSettings(fontFamily = InterfaceFontFamily.Inter))) {
                val measurer = rememberTextMeasurer()
                val style = LocalTextStyle.current
                widths = listOf("0:00", "1:11", "4:07", "8:58").map { measurer.measure(it, style).size.width }
            }
        }
        runOnIdle { assertEquals(1, widths.toSet().size, "Tabular digits must not change width: $widths") }
    }

    @Test
    fun fontPageSwitchesTheAppLiveWhileStayingReadableInNunitoSans() = runDesktopComposeUiTest(420, 1000) {
        val settings = mutableStateOf(InterfaceSettings())
        var nunito: FontFamily? = null
        var app: FontFamily? = null
        setContent {
            MaterialTheme(typography = rememberNaviampTypography()) { nunito = MaterialTheme.typography.bodyLarge.fontFamily }
            MaterialTheme(typography = rememberNaviampTypography(settings.value)) {
                app = MaterialTheme.typography.bodyLarge.fontFamily
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Outside the font page")
                    FontSettings(colors, settings.value) { settings.value = it }
                }
            }
        }
        for (family in listOf(InterfaceFontFamily.Inter, InterfaceFontFamily.System, InterfaceFontFamily.NunitoSans)) {
            onNodeWithText(family.label()).performScrollTo().performClick()
            runOnIdle { assertEquals(InterfaceSettings(fontFamily = family), settings.value) }
            waitForIdle()
            assertEquals(app, layout("Outside the font page").layoutInput.style.fontFamily)
            assertEquals(app.describe(), layout("Every album, every artist, every track.").layoutInput.style.fontFamily.describe())
            assertEquals(nunito, layout(family.label()).layoutInput.style.fontFamily, "The picker stays in Nunito Sans")
        }
        runOnIdle { settings.value = InterfaceSettings(fontFamily = InterfaceFontFamily.Inter) }
        waitForIdle()
        assertNotEquals(nunito, app)
    }

    @Test
    fun aRememberedFontMissingOnThisDeviceFallsBackToNunitoSansAndCanBeReset() = runDesktopComposeUiTest(420, 1000) {
        val settings = mutableStateOf(InterfaceSettings(fontFamily = InterfaceFontFamily.Inter, installedFontFamily = "Missing Font 625"))
        var nunito: FontFamily? = null
        setContent {
            MaterialTheme(typography = rememberNaviampTypography()) { nunito = MaterialTheme.typography.bodyLarge.fontFamily }
            MaterialTheme(typography = rememberNaviampTypography(settings.value)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Outside the font page")
                    FontSettings(colors, settings.value) { settings.value = it }
                }
            }
        }
        onNodeWithTag("font-unavailable").assertExists()
        onNodeWithText("Missing Font 625 is not available on this device, so Naviamp is using Nunito Sans.").assertExists()
        assertEquals(nunito, layout("Outside the font page").layoutInput.style.fontFamily)
        runOnIdle { assertEquals("Missing Font 625", settings.value.installedFontFamily, "The stored choice is kept") }
        onNodeWithText("Inter").performScrollTo().performClick()
        runOnIdle {
            assertNull(settings.value.installedFontFamily)
            assertEquals(InterfaceFontFamily.Inter, settings.value.fontFamily)
        }
        onNodeWithTag("font-unavailable").assertDoesNotExist()
    }

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun desktopListsInstalledFamiliesAndAppliesTheChosenOne() = runDesktopComposeUiTest(420, 1200) {
        val installed = assertNotNull(naviampInstalledFonts())
        val started = System.nanoTime()
        val families = presentableInstalledFontFamilies(installed.familyNames())
        println("Listed ${families.size} installed families in ${(System.nanoTime() - started) / 1_000_000} ms")
        assertTrue(families.isNotEmpty())
        val name = families.firstOrNull { it.equals("DejaVu Sans", ignoreCase = true) } ?: families.first()
        val settings = mutableStateOf(InterfaceSettings())
        setContent {
            MaterialTheme(typography = rememberNaviampTypography(settings.value)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Outside the font page")
                    FontSettings(colors, settings.value) { settings.value = it }
                }
            }
        }
        val row = hasText(name) and hasAnyAncestor(hasTestTag("installed-font-list"))
        waitUntil(timeoutMillis = 10_000) { onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("installed-font-search").performScrollTo().performTextInput(name)
        onNode(row).performClick()
        runOnIdle { assertEquals(name, settings.value.installedFontFamily) }
        waitForIdle()
        val applied = layout("Outside the font page").layoutInput.style.fontFamily as FontListFontFamily
        assertTrue(applied.fonts.all { (it as SystemFont).identity == name }, "$name must be applied: ${applied.fonts}")
        onNodeWithText("Nunito Sans").performScrollTo().performClick()
        runOnIdle { assertEquals(InterfaceSettings(), settings.value) }
    }

    @Test
    fun desktopReportsRealSymbolFontsAsUnusable() {
        val installed = assertNotNull(naviampInstalledFonts())
        val names = presentableInstalledFontFamilies(installed.familyNames())
        val text = names.firstOrNull { it.equals("DejaVu Sans", ignoreCase = true) } ?: names.first()
        assertTrue(isUsableInstalledFont(installed, text), "$text draws Latin text")
        assertTrue(text in usableInstalledFontFamilies(installed))
        // OpenSymbol ships with LibreOffice; skip where it is not installed.
        val symbol = names.firstOrNull { it.equals("OpenSymbol", ignoreCase = true) } ?: return
        assertTrue(!isUsableInstalledFont(installed, symbol), "$symbol must be filtered out")
        assertTrue(symbol !in usableInstalledFontFamilies(installed))
        assertEquals(emptyList(), usableInstalledFamiliesMatching(installed, symbol))
    }

    @OptIn(ExperimentalTextApi::class)
    @Test
    fun linuxSystemFontFollowsTheDesktopConfiguration() {
        if (hostOs != OS.Linux) return
        val family = naviampSystemFontFamily(listOf(FontWeight.Normal)) as FontListFontFamily
        val name = (family.fonts.single() as SystemFont).identity
        val installed = presentableInstalledFontFamilies(assertNotNull(naviampInstalledFonts()).familyNames())
        assertTrue(installed.any { it.equals(name, ignoreCase = true) }, "$name must be an installed family")
    }

    @Test
    fun nowPlayingFitsAtTheLargestSizeWithEveryFont() {
        val layouts = listOf(
            Triple(420, 800, NowPlayingDisplaySettings()),
            Triple(1280, 720, NowPlayingDisplaySettings()),
            Triple(1280, 720, NowPlayingDisplaySettings(fillArtworkArea = true)),
        )
        for (family in InterfaceFontFamily.entries) for ((width, height, display) in layouts) {
            runDesktopComposeUiTest(width, height) {
                val settings = largest(family)
                setContent {
                    MaterialTheme(typography = rememberNaviampTypography(settings)) {
                        NaviampFontSizeScope(settings.generalFontSize, includeStandardScale = true) {
                            NaviampFontSizeScope(settings.nowPlayingFontSize, settings.generalFontSize) {
                                NaviampNowPlayingPanel(nowPlaying, colors = colors, actions = actions, displaySettings = display)
                            }
                        }
                    }
                }
                onNodeWithText("Song title").assertExists()
                assertNoTextOverflow("$family ${width}x$height fill=${display.fillArtworkArea}")
            }
        }
    }

    @Test
    fun settingsRowsAndTheEqualizerFitAtTheLargestSizeWithEveryFont() {
        for (family in InterfaceFontFamily.entries) {
            runDesktopComposeUiTest(360, 2400) {
                val settings = largest(family)
                setContent {
                    MaterialTheme(typography = rememberNaviampTypography(settings)) {
                        NaviampFontSizeScope(settings.generalFontSize, includeStandardScale = true) {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                FontSettings(colors, settings) {}
                                SelectableSettingsRow(colors, "Selectable setting", "A setting subtitle", selected = true) {}
                                SettingsRow("A settings row", "A settings row subtitle", colors, value = "150%") {}
                            }
                        }
                    }
                }
                onNodeWithText("A settings row").assertExists()
                assertNoTextOverflow("$family settings")
            }
            runDesktopComposeUiTest(258, 900) {
                val settings = largest(family)
                setContent {
                    MaterialTheme(typography = rememberNaviampTypography(settings)) {
                        NaviampFontSizeScope(settings.generalFontSize, includeStandardScale = true) {
                            EqualizerCurvePanel(colors, EqualizerSettings(), false) { _, _ -> }
                        }
                    }
                }
                onNodeWithText("16k").assertExists()
                assertNoTextOverflow("$family equalizer")
            }
        }
    }

    @Test
    fun televisionDisplaySettingsFitAtTheLargestSizeWithEveryFont() {
        for (family in InterfaceFontFamily.entries) runDesktopComposeUiTest(960, 540) {
            val settings = largest(family)
            val state = NaviampAppShellUiState(general = NaviampGeneralSettingsUi(interfaceSettings = settings))
            setContent {
                MaterialTheme(typography = rememberNaviampTypography(settings)) {
                    NaviampFontSizeScope(settings.generalFontSize) {
                        TelevisionDisplaySettings(state, NaviampTelevisionColors, NoSettingsValueActions,
                            FocusRequester(), FocusRequester(), null) {}
                    }
                }
            }
            onNodeWithText("Font").assertExists()
            onNodeWithText(family.label()).assertExists()
            assertNoTextOverflow("$family television")
        }
    }

    @OptIn(ExperimentalTextApi::class)
    private fun FontFamily?.describe(): Any? =
        (this as? FontListFontFamily)?.fonts?.map { (it as? SystemFont)?.identity ?: it } ?: this

    @Test
    fun mergedFontPageFitsAndKeepsItsControlsInPlaceAtPhoneWidth() = runDesktopComposeUiTest(412, 1400) {
        val settings = mutableStateOf(largest(InterfaceFontFamily.Inter))
        setContent {
            MaterialTheme(typography = rememberNaviampTypography(settings.value)) {
                NaviampFontSizeScope(settings.value.generalFontSize, includeStandardScale = true) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        FontSettings(colors, settings.value) { settings.value = it }
                    }
                }
            }
        }
        val controls = listOf("general-font-size-value", "now-playing-font-size-value")
        fun positions() = controls.associateWith { onNodeWithTag(it).fetchSemanticsNode().boundsInRoot } +
            InterfaceFontFamily.entries.associate { it.label() to onNodeWithText(it.label()).fetchSemanticsNode().boundsInRoot }
        fun assertReadable(context: String) {
            assertNoTextOverflow(context)
            for (tag in controls) {
                val value = onNode(hasAnyAncestor(hasTestTag(tag)) and SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
                    .fetchSemanticsNode().let { node ->
                        mutableListOf<TextLayoutResult>().also { node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(it) }.single()
                    }
                assertEquals(1, value.lineCount, "$context: ${value.layoutInput.text} must stay on one line")
            }
        }
        assertReadable("150% Inter")
        val before = positions()
        for ((tag, option, expected) in listOf(
            Triple("general-font-size-value", "80%", InterfaceSettings::generalFontSize),
            Triple("now-playing-font-size-value", "80%", InterfaceSettings::nowPlayingFontSize),
            Triple("general-font-size-value", "150%", InterfaceSettings::generalFontSize),
            Triple("now-playing-font-size-value", "100% (default)", InterfaceSettings::nowPlayingFontSize),
        )) {
            onNodeWithTag(tag).performScrollTo().performClick()
            onNode(hasText(option) and hasClickAction() and hasAnyAncestor(isPopup())).performClick()
            waitForIdle()
            runOnIdle { assertEquals(option.substringBefore('%').toInt(), expected(settings.value).percent) }
            assertReadable("after $option")
            positions().forEach { (name, bounds) ->
                assertEquals(before.getValue(name).top, bounds.top, 0.5f, "$name moved when choosing $option")
            }
        }
        onNodeWithText("Nunito Sans").performScrollTo().performClick()
        waitForIdle()
        positions().forEach { (name, bounds) ->
            assertEquals(before.getValue(name).top, bounds.top, 0.5f, "$name moved when choosing a typeface")
        }
    }

    @Test
    fun televisionFontPageFitsAndKeepsItsChoicesInPlaceAtTheLargestSize() {
        for (family in InterfaceFontFamily.entries) runDesktopComposeUiTest(960, 540) {
            val settings = mutableStateOf(largest(family))
            val actions = NoSettingsValueActions.copy(onInterfaceSettingsChanged = { settings.value = it })
            setContent {
                MaterialTheme(typography = rememberNaviampTypography(settings.value)) {
                    NaviampFontSizeScope(settings.value.generalFontSize) {
                        TelevisionFontSettings(
                            NaviampAppShellUiState(general = NaviampGeneralSettingsUi(interfaceSettings = settings.value)),
                            NaviampTelevisionColors, actions, FocusRequester(),
                        )
                    }
                }
            }
            onNodeWithTag("font-preview").assertExists()
            assertNoTextOverflow("$family television font page")
            val rows = InterfaceFontFamily.entries.map { it.label() }
            // The list scrolls to reach a size, so rows are compared against the preview above them.
            fun offsets(): Map<String, Float> {
                val preview = onNodeWithTag("font-preview").fetchSemanticsNode().boundsInRoot.top
                return rows.associateWith { onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top - preview }
            }
            val before = offsets()
            onAllNodes(hasText("80%") and hasClickAction()).onFirst().performScrollTo().performClick()
            runOnIdle { assertEquals(80, settings.value.generalFontSize.percent) }
            onNodeWithTag("font-preview").performScrollTo()
            waitForIdle()
            val after = offsets()
            rows.forEach { assertEquals(before.getValue(it), after.getValue(it), 0.5f, "$it moved on $family") }
            assertNoTextOverflow("$family television font page at 80%")
        }
    }

    private fun largest(family: InterfaceFontFamily) = InterfaceSettings(
        fontFamily = family,
        generalFontSize = InterfaceFontSize(InterfaceFontSize.MaxPercent),
        nowPlayingFontSize = InterfaceFontSize(InterfaceFontSize.MaxPercent),
    )

    private fun InterfaceFontFamily.label() = when (this) {
        InterfaceFontFamily.NunitoSans -> "Nunito Sans"
        InterfaceFontFamily.Inter -> "Inter"
        InterfaceFontFamily.System -> "System"
    }

    private fun ComposeUiTest.layout(text: String): TextLayoutResult =
        textLayouts().first { it.first == text }.second

    private fun ComposeUiTest.textLayouts(): List<Pair<String, TextLayoutResult>> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().flatMap { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.map { it.layoutInput.text.text to it }
            }

    /**
     * Skia reports width overflow for unconstrained single-line text even when it fits, so a clip is
     * a vertical overflow, an ellipsis, or a line wider than its box. Hidden tabs are skipped.
     */
    private fun ComposeUiTest.assertNoTextOverflow(context: String) {
        val clipped = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
            .flatMap { node ->
                val results = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                results.map { it to node.boundsInRoot }
            }
            .filter { (layout, _) ->
                layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                    layout.isLineEllipsized(line) || layout.getLineRight(line) - layout.getLineLeft(line) > layout.size.width + 1f
                }
            }
            .map { (layout, bounds) -> "${layout.layoutInput.text.text} at $bounds" }
        assertTrue(clipped.isEmpty(), "$context clips $clipped")
    }
}

private val NoSettingsValueActions = NaviampSettingsValueActions({}, {}, {}, {}, {}, {})
