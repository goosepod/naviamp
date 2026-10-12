package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class NaviampRadioMethodSettingsTest {
    @Test fun radioIsVisibleWithoutSonicOrDiagnosticsAndUnavailableSonicCannotBeSelected() = runDesktopComposeUiTest(480, 800) {
        val settings = mutableStateOf(PlaybackSettings())
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NaviampExperienceSettingsSection(NaviampColors(), installedVersion = "test",
                    interfaceSettings = InterfaceSettings(), playbackSettings = settings.value,
                    cacheSettings = CacheSettings(), showQueueBehavior = false, showLrclibLyrics = false,
                    showSoftwareVolumePreference = false, supportsSonicSimilarity = false,
                    onInterfaceSettingsChanged = {}, onPlaybackSettingsChanged = { settings.value = it },
                    onCacheSettingsChanged = {})
            }
        }
        onNodeWithText("Radio").performScrollTo().performClick()
        onNodeWithText("Sonic Similarity").assertIsNotEnabled()
        onNodeWithText("Instant Mix").assertIsSelected().assertIsEnabled()
        onNodeWithText("Radio Autoplay").performScrollTo().performClick()
        runOnIdle { assertTrue(settings.value.sonicAutoplayEnabled); assertFalse(settings.value.sonicSimilarityEnabled) }
        onNodeWithText("Autoplay batch size").assertIsDisplayed()
    }

    @Test fun methodsAreExclusiveAndCapabilityLossDoesNotSilentlyChangeTheSelection() = runDesktopComposeUiTest(480, 800) {
        val settings = mutableStateOf(PlaybackSettings())
        val supportsSonic = mutableStateOf(true)
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RadioSettingsSection(NaviampColors(), settings.value, supportsSonic.value,
                    NaviampSimilarityTestUi(), null, {}, { settings.value = it })
            }
        }
        onNodeWithText("Instant Mix").assertIsSelected()
        onNodeWithText("Sonic Similarity").performClick().assertIsSelected()
        onNodeWithText("Instant Mix").assertIsNotSelected()
        runOnIdle { supportsSonic.value = false }
        onNodeWithText("Sonic Similarity").assertIsSelected().assertIsNotEnabled()
        onNodeWithText("Instant Mix").performClick().assertIsSelected()
        runOnIdle { assertFalse(settings.value.effectiveSonicSimilarityEnabled()) }
        System.getenv("NAVIAMP_RADIO_METHOD_QA_DIR")?.let { path ->
            val directory = java.io.File(path).apply { mkdirs() }
            val image = org.jetbrains.skia.Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
            java.io.File(directory, "radio-method-settings.png").writeBytes(image.encodeToData()!!.bytes)
        }
    }
}
