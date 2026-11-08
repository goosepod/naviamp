package app.naviamp.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asSkiaBitmap
import app.naviamp.domain.smartplaylist.SmartPlaylistEditingResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSmartPlaylistInspectionLayoutTest {
    @Test
    fun narrowInspectorAtLargeTextKeepsCloseAccessibleAndOriginalSelectable() = runDesktopComposeUiTest(360, 640) {
        var closed = false
        val original = "{\n" + (1..50).joinToString(",\n") { "  \"unknownField$it\": \"preserve this rule\"" } + "\n}"
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                SmartPlaylistInspectionDialog(SmartPlaylistEditingResult.ReadOnly(original), NaviampColors(), { closed = true })
            }
        }
        onNodeWithTag("smart-playlist-original-definition").assertTextEquals(original)
        assertEquals(0, onAllNodesWithText("Save").fetchSemanticsNodes().size)
        onNodeWithText("Close").assertIsDisplayed().assertHasClickAction()
        val output = File("build/reports/smart-playlist-inspector-narrow.png")
        output.parentFile.mkdirs()
        org.jetbrains.skia.Image.makeFromBitmap(onAllNodes(isRoot()).onLast().captureToImage().asSkiaBitmap()).use {
            output.writeBytes(requireNotNull(it.encodeToData()).bytes)
        }
        onNodeWithText("Close").performClick()
        runOnIdle { assertEquals(true, closed) }
    }
}
