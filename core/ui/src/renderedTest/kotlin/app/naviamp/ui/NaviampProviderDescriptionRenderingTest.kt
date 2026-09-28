package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class NaviampProviderDescriptionRenderingTest {
    @Test
    fun cachedArtistAndAlbumDescriptionsDecodeInCollapsedAndExpandedViews() = runComposeUiTest {
        val content = mutableStateOf("artist" to "<b>Aether Elf&amp;#039;s music</b>\nSecond &amp; third\nCaf&eacute;\nLast &#x1F3B5;")
        setContent {
            Column(Modifier.width(280.dp)) {
                NaviampProviderDescription(content.value.second, content.value.first, NaviampColors())
            }
        }
        val expected = "Aether Elf's music\nSecond & third\nCafé\nLast 🎵"
        onNodeWithText(expected).assertExists()
        onNodeWithText("More…").performClick()
        onNodeWithText(expected).assertIsDisplayed()
        onNodeWithText("Less").performClick()
        onNodeWithText(expected).assertExists()
        // The same raw cache payload is interpreted once for either media presentation.
        runOnIdle { content.value = "album" to "Album&#039;s notes\nSecond\nThird\nFourth &copy;" }
        onNodeWithText("Album's notes\nSecond\nThird\nFourth ©").assertExists()
        onNodeWithText("More…").performClick()
        onNodeWithText("Album's notes\nSecond\nThird\nFourth ©").assertIsDisplayed()
    }
}
