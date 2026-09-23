package app.naviamp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class NaviampCacheUpgradeSettingsTest {
    @Test
    fun cacheUpgradeIsAvailableWithoutCellularNetworkControls() = runDesktopComposeUiTest(480, 600) {
        val settings = mutableStateOf(PlaybackSettings())
        setContent {
            Column {
                StreamingQualitySettings(NaviampColors(), settings.value, false) { settings.value = it }
            }
        }
        onNodeWithText("Upgrade cached audio on Wi-Fi").assertIsDisplayed().performClick()
        runOnIdle { assertTrue(settings.value.upgradeCachedAudioOnWifi) }
        onNodeWithText("Upgrade cached audio on Wi-Fi").performClick()
        runOnIdle { assertFalse(settings.value.upgradeCachedAudioOnWifi) }
    }
}
