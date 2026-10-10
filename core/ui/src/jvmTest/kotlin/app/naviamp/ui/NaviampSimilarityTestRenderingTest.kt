package app.naviamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.radio.*
import app.naviamp.domain.settings.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NaviampSimilarityTestRenderingTest {
    @Test
    fun missingSonicSupportStillAllowsTestingAndRunningTestCannotBeStartedAgain() = runDesktopComposeUiTest(480, 800) {
        var requests = 0
        val test = mutableStateOf(NaviampSimilarityTestUi())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Column(Modifier.fillMaxSize().background(NaviampColors.Dark.background).verticalScroll(rememberScrollState())) {
                    NaviampExperienceSettingsSection(
                        colors = NaviampColors.Dark, installedVersion = "test",
                        interfaceSettings = InterfaceSettings(), playbackSettings = PlaybackSettings(), cacheSettings = CacheSettings(),
                        showQueueBehavior = false, showLrclibLyrics = false, showSoftwareVolumePreference = false,
                        supportsSonicSimilarity = false, similarityTest = test.value,
                        onTestSimilarity = { requests++; test.value = NaviampSimilarityTestUi(SimilarityTestState.Running) },
                        onInterfaceSettingsChanged = {}, onPlaybackSettingsChanged = {}, onCacheSettingsChanged = {},
                    )
                }
            }
        }
        onNodeWithText("Related Tracks").performClick()
        onNodeWithText("Test similarity").performScrollTo().performClick()
        assertEquals(1, requests)
        onNode(isDialog()).assertExists()
        onAllNodesWithText("Loading…").filter(hasClickAction()).onFirst().assertIsNotEnabled()
        onNodeWithText("Close").performClick()
        onNode(isDialog()).assertDoesNotExist()
        runOnIdle { test.value = NaviampSimilarityTestUi() }
        onNodeWithText("Test similarity").assertIsEnabled()
    }

    @Test
    fun failedSonicAndWorkingRegularRemainSeparateAtPhoneAndDesktopWidths() {
        listOf(480 to 800, 1100 to 800).forEach { (width, height) ->
            runDesktopComposeUiTest(width, height) {
                val seed = Track(TrackId("seed"), "Test song", artistName = "Artist", albumTitle = null,
                    durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
                val report = SimilarityDiagnostics(seed, SimilaritySupport.Advertised, null,
                    SimilarityEndpointResult(SimilarityResultKind.Failed, httpStatus = 503, serverCode = 0),
                    SimilarityEndpointResult(SimilarityResultKind.Matches, 20,
                        tracks = (1..20).map { SimilarityTrackInfo("Returned song $it", "Returned artist $it") }))
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                        Column(Modifier.fillMaxSize().background(NaviampColors.Dark.background).verticalScroll(rememberScrollState())) {
                            NaviampSimilarityTestContent(NaviampColors.Dark,
                                NaviampSimilarityTestUi(SimilarityTestState.Complete, report = report), {},
                                RadioBuildDiagnostics(RadioBuildOutcome.Fallback, SonicRadioFallbackReason.Failed))
                        }
                    }
                }
                onNodeWithText("Test track: Test song").assertDoesNotExist()
                onNodeWithText("Test similarity").performClick()
                onNode(isDialog()).assertExists()
                onNodeWithText("Test track: Test song").assertIsDisplayed()
                onNodeWithText("Artist").assertIsDisplayed()
                onNodeWithText("Sonic support is advertised.").assertIsDisplayed()
                onNodeWithText("HTTP: 503; API: 0").assertIsDisplayed()
                onNodeWithText("20 matches").assertIsDisplayed()
                onNodeWithText("Returned song 1").assertIsDisplayed()
                onNodeWithText("Returned artist 1").assertIsDisplayed()
                onNodeWithText("Returned song 20").performScrollTo().assertIsDisplayed()
                onNodeWithText("Returned artist 20").performScrollTo().assertIsDisplayed()
                onNodeWithText("Sonic similarity was unavailable. Radio used its fallback.").performScrollTo().assertIsDisplayed()
                val directory = System.getenv("NAVIAMP_SIMILARITY_QA_DIR")?.let { java.io.File(it) }
                directory?.let {
                    it.mkdirs()
                    val image = org.jetbrains.skia.Image.makeFromBitmap(onNode(isDialog()).captureToImage().asSkiaBitmap())
                    val png = checkNotNull(image.encodeToData())
                    java.io.File(it, "similarity-$width.png").writeBytes(png.bytes)
                    png.close(); image.close()
                }
                onNodeWithText("Close").performClick()
                onNode(isDialog()).assertDoesNotExist()
            }
        }
    }
}
