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
    fun adminMetadataAndDisabledSystemAreDistinctFromAnEmptyInventory() = runDesktopComposeUiTest(480, 800) {
        val seed = Track(TrackId("seed"), "Admin test", artistName = "Artist", albumTitle = null,
            durationSeconds = 180, coverArtId = null, audioInfo = null, replayGain = null)
        val admin = mutableStateOf(SimilarityAdminDiagnostics(SimilarityAdminStatus.Available,
            pluginsEnabled = true, externalServicesEnabled = false, agents = listOf("audiomuseai", "lastfm"),
            plugins = listOf(SimilarityPluginInfo("audiomuseai", "AudioMuse", "10.0.0", true, true))))
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                NaviampSimilarityTestContent(NaviampColors.Dark,
                    NaviampSimilarityTestUi(SimilarityTestState.Complete, report = SimilarityDiagnostics(seed,
                        SimilaritySupport.Advertised, null, SimilarityEndpointResult(SimilarityResultKind.Empty),
                        SimilarityEndpointResult(SimilarityResultKind.Empty), admin.value)), {})
            }
        }
        onNodeWithText("Test similarity").performClick()
        onNodeWithText("Plugin system enabled.").assertIsDisplayed()
        onNodeWithText("External services disabled.").assertIsDisplayed()
        onNodeWithText("Metadata agents: audiomuseai, lastfm").assertIsDisplayed()
        onNodeWithText("AudioMuse").assertIsDisplayed()
        onNodeWithText("audiomuseai · 10.0.0").assertIsDisplayed()
        onNodeWithText("Enabled").assertIsDisplayed()
        onNodeWithText("The server reports a plugin error. Check the Navidrome plugin settings and logs.").performScrollTo().assertIsDisplayed()
        System.getenv("NAVIAMP_SIMILARITY_QA_DIR")?.let { path ->
            val directory = java.io.File(path).apply { mkdirs() }
            val image = org.jetbrains.skia.Image.makeFromBitmap(onNode(isDialog()).captureToImage().asSkiaBitmap())
            val png = checkNotNull(image.encodeToData())
            java.io.File(directory, "similarity-admin.png").writeBytes(png.bytes)
            png.close(); image.close()
        }
        runOnIdle { admin.value = SimilarityAdminDiagnostics(SimilarityAdminStatus.Available, pluginsEnabled = false) }
        onNodeWithText("Plugin system disabled. Installed plugins cannot be listed.").performScrollTo().assertIsDisplayed()
        onNodeWithText("No installed plugins were returned.").assertDoesNotExist()
        runOnIdle { admin.value = SimilarityAdminDiagnostics(SimilarityAdminStatus.Available, pluginsEnabled = true, plugins = emptyList()) }
        onNodeWithText("No installed plugins were returned.").assertIsDisplayed()
        onNodeWithText("Close").performClick()
    }

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
                        tracks = (1..20).map { SimilarityTrackInfo("Returned song $it", "Returned artist $it") }),
                    admin = SimilarityAdminDiagnostics(SimilarityAdminStatus.NotAdmin))
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
                onNodeWithText("Plugin details require administrator access.").assertIsDisplayed()
                onNodeWithText("HTTP: 503; API: 0").performScrollTo().assertIsDisplayed()
                onNodeWithText("20 matches").performScrollTo().assertIsDisplayed()
                onNodeWithText("Returned song 1").performScrollTo().assertIsDisplayed()
                onNodeWithText("Returned artist 1").performScrollTo().assertIsDisplayed()
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
