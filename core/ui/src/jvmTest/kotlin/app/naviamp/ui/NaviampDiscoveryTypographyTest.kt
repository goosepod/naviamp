package app.naviamp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import app.naviamp.domain.provider.*
import app.naviamp.domain.settings.InterfaceFontSize
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NaviampDiscoveryTypographyTest {
    @Test fun aurralBodyMatchesLibraryTypographyAndBothRespectFontSizeSetting() = runDesktopComposeUiTest(1100, 850) {
        val font = mutableStateOf(InterfaceFontSize.Small)
        val state = mutableStateOf(discoveryPageFixture().copy(artistPage = false, artistDetail = null,
            browser = DiscoveryBrowserState(page = DiscoveryBrowsePage.Trending, charts = ProviderDiscoveryCharts(
                ProviderDiscoveryStatus.Ready, songs = listOf(DiscoveryChartSong("key", "Scale song", "Scale artist"))))))
        setContent { MaterialTheme { NaviampFontSizeScope(font.value, includeStandardScale = true) { Column {
            TrackRow(SharedTrackRowUi("reference", "Library reference", "", canToggleFavorite = false), NaviampColors.Dark,
                null, false, false, false, false, false, showCoverArt = false)
            Box(Modifier.weight(1f)) { NaviampDiscoveryBrowserUi(state.value, NaviampColors.Dark, discoveryPageFixtureActions(state, mutableListOf())) }
        } } } }
        fun layout(label: String): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }
        val small = layout("Scale song"); val library = layout("Library reference")
        val smallScale = small.layoutInput.density.fontScale
        assertEquals(library.layoutInput.style.fontSize, small.layoutInput.style.fontSize)
        runOnIdle { font.value = InterfaceFontSize.Large }
        val large = layout("Scale song")
        assertTrue(large.size.height > small.size.height)
        assertTrue(large.layoutInput.density.fontScale > smallScale, "Font scale must increase from the captured Small setting")
    }
}
