package app.naviamp.ui

import app.naviamp.domain.settings.LyricsDisplayPreference
import app.naviamp.domain.settings.LyricsTimingPreference
import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampTelevisionSettingsPolicyTest {
    @Test
    fun televisionExposesOnlyUsefulAvailableTopLevelCategories() {
        val current = televisionSettingsCategories(controllersAvailable = false)

        assertEquals(
            listOf(
                TelevisionSettingsCategory.Sources,
                TelevisionSettingsCategory.Home,
                TelevisionSettingsCategory.Display,
                TelevisionSettingsCategory.Playback,
                TelevisionSettingsCategory.Lyrics,
                TelevisionSettingsCategory.Diagnostics,
                TelevisionSettingsCategory.About,
            ),
            current,
        )
        assertFalse(TelevisionSettingsCategory.Controllers in current)
        assertTrue(
            TelevisionSettingsCategory.Controllers in televisionSettingsCategories(controllersAvailable = true),
        )
    }

    @Test
    fun televisionChoiceLabelsAreTenFootFriendly() {
        assertEquals("Off", televisionCrossfadeLabel(0))
        assertEquals("5 seconds", televisionCrossfadeLabel(5))
        assertEquals("First available", televisionLyricsTimingLabel(LyricsTimingPreference.FirstAvailable))
        assertEquals("Word synced", televisionLyricsTimingLabel(LyricsTimingPreference.WordSynced))
        assertEquals(
            "Match preferred lyrics",
            televisionLyricsDisplayLabel(LyricsDisplayPreference.MatchDownload),
        )
        assertEquals("320 steps", televisionWaveformDensityLabel(320))
    }

    @Test
    fun televisionGaplessAndCrossfadeSelectionsAreMutuallyExclusive() {
        val gapless = PlaybackSettings(gaplessEnabled = true, crossfadeDurationSeconds = 0)

        val crossfade = televisionPlaybackSettingsWithCrossfade(gapless, seconds = 8)
        assertFalse(crossfade.gaplessEnabled)
        assertEquals(8, crossfade.crossfadeDurationSeconds)

        val gaplessAgain = televisionPlaybackSettingsWithGapless(crossfade, enabled = true)
        assertTrue(gaplessAgain.gaplessEnabled)
        assertEquals(0, gaplessAgain.crossfadeDurationSeconds)

        val gaplessOff = televisionPlaybackSettingsWithGapless(gaplessAgain, enabled = false)
        assertFalse(gaplessOff.gaplessEnabled)
        assertEquals(0, gaplessOff.crossfadeDurationSeconds)
    }

    @Test
    fun televisionDisplaySlidersStepAndClampForRemoteInput() {
        val range = 8f..48f

        assertEquals(30f, televisionSteppedSettingsValue(28f, 2f, range, increase = true))
        assertEquals(26f, televisionSteppedSettingsValue(28f, 2f, range, increase = false))
        assertEquals(48f, televisionSteppedSettingsValue(48f, 2f, range, increase = true))
        assertEquals(8f, televisionSteppedSettingsValue(8f, 2f, range, increase = false))
    }

    @Test
    fun televisionSettingsBackdropKeepsLiveDisplayChangesVisible() {
        assertEquals(0.03f, TelevisionSettingsBackdropDimAlpha)
    }

    @Test
    fun connectPairingCodeUsesReadableTelevisionGrouping() {
        assertEquals("123 456", formatNaviampConnectPairingCode("123456"))
        assertEquals("123 456", formatNaviampConnectPairingCode("123-456"))
    }

    @Test
    fun connectRoleControlsWhichSettingsFlowsAreAvailable() {
        val target = NaviampConnectSettingsUi(role = NaviampConnectUiRole.Target)
        val controller = NaviampConnectSettingsUi(role = NaviampConnectUiRole.Controller)
        val both = NaviampConnectSettingsUi(role = NaviampConnectUiRole.ControllerAndTarget)

        assertTrue(target.canAdvertise)
        assertFalse(target.canDiscover)
        assertFalse(controller.canAdvertise)
        assertTrue(controller.canDiscover)
        assertTrue(both.canAdvertise)
        assertTrue(both.canDiscover)
    }
}
