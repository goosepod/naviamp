package app.naviamp.ui

import androidx.compose.ui.test.*
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.settings.NowPlayingDisplaySettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RadioStreamQualityUiTest {
    private fun radio() = InternetRadioStation("rp", "Radio Paradise", "https://stream.radioparadise.com/aac-320")
        .toRadioNowPlayingUi(PlaybackStreamMetadata(properties = mapOf("Content-Type" to "audio/aac", "icy-br" to "320")),
            PlaybackState.Paused, 50, emptyList(), emptyMap())

    @Test fun streamQualityFlowsIntoTheSharedRadioPresentation() {
        assertEquals("AAC  320 kbps", radio().audioInfo)
    }

    @Test fun existingAudioInfoSettingControlsRadioAcrossPlayerLayouts() {
        data class Layout(val width: Int, val height: Int, val fill: Boolean)
        for (layout in listOf(Layout(320, 720, true), Layout(412, 818, false), Layout(876, 728, false))) {
            for (enabled in listOf(true, false)) runDesktopComposeUiTest(width = layout.width, height = layout.height) {
                setContent {
                    NaviampNowPlayingPanel(radio(), colors = NaviampColors.Dark,
                        actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                        displaySettings = NowPlayingDisplaySettings(fillArtworkArea = layout.fill, showAudioInfo = enabled))
                }
                onAllNodesWithText("320 kbps", substring = true).assertCountEquals(if (enabled) 1 else 0)
                onNodeWithContentDescription("Favorite").assertDoesNotExist()
            }
        }
    }

    @Test fun televisionRespectsTheSameAudioInfoSettingForRadio() {
        for (enabled in listOf(true, false)) runComposeUiTest {
            setContent {
                TelevisionNowPlaying(radio(), null, NaviampColors.Dark,
                    actions = NaviampNowPlayingActions({}, {}, {}, {}, {}, {}, {}),
                    onClose = {}, onOpenSettings = {},
                    displaySettings = NowPlayingDisplaySettings(showAudioInfo = enabled))
            }
            onAllNodesWithText("320 kbps", substring = true).assertCountEquals(if (enabled) 1 else 0)
        }
    }
}
