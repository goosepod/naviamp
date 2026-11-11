package app.naviamp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import kotlin.test.Test
import java.io.File
import javax.imageio.ImageIO

@OptIn(ExperimentalTestApi::class)
class NaviampStreamDetailsUiTest {
    private val station = InternetRadioStation("rp", "Radio Paradise", "https://stream.radioparadise.com/aac-320")
    private fun radio(title: String? = null) = station.toRadioNowPlayingUi(
        PlaybackStreamMetadata(title, mapOf("icy-genre" to "Eclectic", "icy-br" to "320", "Content-Type" to "audio/aac",
            "icy-description" to "Independent music and discoveries. ".repeat(20), "DJ" to "Guest presenter")),
        PlaybackState.Paused, 50, emptyList(), emptyMap())

    @Test fun radioMenuOpensLiveDetailsIndependentlyOfTheCompactAudioSetting() = runDesktopComposeUiTest(900, 750) {
        val current = mutableStateOf(radio("First song"))
        setContent {
            StreamDetailsTestTheme {
            NaviampNowPlayingPanel(current.value, colors = NaviampColors.Dark,
                actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                displaySettings = NowPlayingDisplaySettings(showAudioInfo = false))
            }
        }
        onNodeWithContentDescription("Stream actions").performClick()
        onNodeWithText("Stream Details").assertIsEnabled().performClick()
        onNodeWithText("Stream Details").assertIsDisplayed()
        onNodeWithText("Genre").assertExists()
        onNodeWithText("Eclectic").assertExists()
        System.getenv("NAVIAMP_STREAM_DETAILS_CAPTURE")?.let { path ->
            ImageIO.write(onNodeWithTag("now-playing-details").captureToImage().toAwtImage(), "png", File(path))
        }
        onNodeWithText("320 kbps").performScrollTo().assertIsDisplayed()
        onNodeWithText("Guest presenter").performScrollTo().assertIsDisplayed()
        runOnIdle { current.value = radio("Second song") }
        onAllNodesWithText("First song").assertCountEquals(0)
        onNode(hasText("Second song") and hasAnyAncestor(hasTestTag("now-playing-details")))
            .performScrollTo().assertIsDisplayed()
        runOnIdle { current.value = station.copy(id = "another", name = "Another station")
            .toRadioNowPlayingUi(PlaybackStreamMetadata(), PlaybackState.Paused, 50, emptyList(), emptyMap()) }
        onNodeWithText("Stream Details").assertDoesNotExist()
        onNodeWithContentDescription("Stream actions").performClick()
        onNodeWithText("Stream Details").performClick()
        onNode(hasText("Another station") and hasAnyAncestor(hasTestTag("now-playing-details"))).assertExists()
    }

    @Test fun libraryTracksKeepTheTrackDetailsMenuAndDialog() = runDesktopComposeUiTest(900, 750) {
        setContent {
            StreamDetailsTestTheme {
            NaviampNowPlayingPanel(NowPlayingUi(id = "track", title = "Library song", subtitle = "Artist", stateLabel = "Paused", menuEnabled = true,
                detailSections = listOf(NaviampDetailSectionUi("Song", listOf("Title" to "Library song")))),
                colors = NaviampColors.Dark, actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}))
            }
        }
        onNodeWithContentDescription("Track actions").performClick()
        onNodeWithText("Track Details").performClick()
        onNodeWithText("Track Details").assertIsDisplayed()
        onNodeWithText("Stream Details").assertDoesNotExist()
        onNodeWithText("Close").performClick()
        onNodeWithText("Track Details").assertDoesNotExist()
    }
}

@Composable
private fun StreamDetailsTestTheme(content: @Composable () -> Unit) {
    val colors = NaviampColors.Dark
    MaterialTheme(colorScheme = darkColorScheme(background = colors.background, surface = colors.controlSurface,
        primary = colors.accent, onPrimary = colors.onAccent, onBackground = colors.primaryText,
        onSurface = colors.primaryText), typography = rememberNaviampTypography(), content = content)
}
