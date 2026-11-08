package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampRadioBatchSettingsTest {
    @Test
    fun slidersKeepIndependentSizesAndAutoplayEnablement() = runDesktopComposeUiTest(480, 800) {
        val settings = mutableStateOf(PlaybackSettings(sonicAutoplayEnabled = true,
            radioMinimumTracks = 20, sonicAutoplayMinimumTracks = 50))
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NaviampExperienceSettingsSection(NaviampColors(), installedVersion = "test",
                    interfaceSettings = InterfaceSettings(), playbackSettings = settings.value,
                    cacheSettings = CacheSettings(), showQueueBehavior = false, showLrclibLyrics = false,
                    showSoftwareVolumePreference = false, supportsSonicSimilarity = true,
                    onInterfaceSettingsChanged = {}, onPlaybackSettingsChanged = { settings.value = it },
                    onCacheSettingsChanged = {})
            }
        }
        onNodeWithText("Radio").performScrollTo().performClick()
        onNodeWithText("Autoplay batch size").assertIsDisplayed()
        onNodeWithText("Radio batch size").assertIsDisplayed()
        val sliders = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        sliders.assertCountEquals(2)
        val radio = sliders[0]
        val autoplay = sliders[1]
        val similarityBounds = onNodeWithText("Sonic Similarity").fetchSemanticsNode().boundsInRoot
        val autoplayBounds = onNodeWithText("Radio Autoplay").fetchSemanticsNode().boundsInRoot
        assertTrue(similarityBounds.bottom < radio.fetchSemanticsNode().boundsInRoot.top)
        assertTrue(radio.fetchSemanticsNode().boundsInRoot.bottom < autoplayBounds.top)
        assertTrue(autoplayBounds.bottom < autoplay.fetchSemanticsNode().boundsInRoot.top)
        // Slider nodes include extra non-drawing interaction space; inspect their tracks in captures.
        // The labels, values and descriptions share the same inset as the visible slider groups.
        for (node in listOf(
            onNodeWithText("Radio batch size"), onNodeWithText("Autoplay batch size"),
            onNodeWithText("20 tracks"), onNodeWithText("50 tracks"),
            onNodeWithText("Related tracks requested when starting Radio.", substring = true),
            onNodeWithText("New tracks requested when the queue ends.", substring = true))) {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= similarityBounds.left, "Slider text $bounds extends past the control $similarityBounds left boundary")
            assertTrue(bounds.right <= similarityBounds.right, "Slider text $bounds extends past the control $similarityBounds right boundary")
        }
        assertEquals(8, autoplay.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)
        autoplay.performSemanticsAction(SemanticsActions.SetProgress) { it(9f) }
        runOnIdle {
            assertEquals(100, settings.value.sonicAutoplayMinimumTracks)
            assertEquals(20, settings.value.radioMinimumTracks)
        }
        radio.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        runOnIdle {
            assertEquals(10, settings.value.radioMinimumTracks)
            assertEquals(100, settings.value.sonicAutoplayMinimumTracks)
        }
        System.getenv("NAVIAMP_RADIO_BATCH_QA_DIR")?.let { path ->
            val directory = java.io.File(path).apply { mkdirs() }
            val image = org.jetbrains.skia.Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
            java.io.File(directory, "radio-batch-settings.png").writeBytes(image.encodeToData()!!.bytes)
        }
        onNodeWithText("Radio Autoplay").performClick()
        autoplay.assertIsNotEnabled()
        radio.assertIsEnabled()
    }
}
