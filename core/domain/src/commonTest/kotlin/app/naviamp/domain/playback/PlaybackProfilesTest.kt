package app.naviamp.domain.playback

import app.naviamp.domain.settings.PlaybackSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackProfilesTest {
    @Test
    fun inheritedProfileLeavesGlobalPlaybackSettingsAlone() {
        val global = PlaybackSettings(
            gaplessEnabled = false,
            crossfadeDurationSeconds = 8,
            replayGainMode = ReplayGainMode.Track,
        )

        assertEquals(global, PlaybackProfile().resolveAgainst(global))
        assertTrue(PlaybackProfile().isInherited)
    }

    @Test
    fun gaplessAndAlbumGainOverrideOnlyTheirOwnedFields() {
        val global = PlaybackSettings(
            gaplessEnabled = false,
            crossfadeDurationSeconds = 8,
            replayGainMode = ReplayGainMode.Track,
            volumePercent = 43,
        )

        val resolved = PlaybackProfile(
            transitionMode = PlaybackTransitionMode.Gapless,
            replayGainMode = PlaybackReplayGainMode.Album,
        ).resolveAgainst(global)

        assertTrue(resolved.gaplessEnabled)
        assertEquals(0, resolved.crossfadeDurationSeconds)
        assertEquals(ReplayGainMode.Album, resolved.replayGainMode)
        assertEquals(43, resolved.volumePercent)
    }

    @Test
    fun crossfadeUsesExplicitOrSensibleDefaultDuration() {
        val explicit = PlaybackProfile(
            transitionMode = PlaybackTransitionMode.Crossfade,
            crossfadeDurationSeconds = 99,
        ).normalized()
        assertEquals(MaxPlaybackProfileCrossfadeSeconds, explicit.crossfadeDurationSeconds)

        val resolved = PlaybackProfile(
            transitionMode = PlaybackTransitionMode.Crossfade,
        ).resolveAgainst(PlaybackSettings(crossfadeDurationSeconds = 0))
        assertFalse(resolved.gaplessEnabled)
        assertEquals(DefaultPlaybackProfileCrossfadeSeconds, resolved.crossfadeDurationSeconds)
    }

    @Test
    fun irrelevantCrossfadeDurationAndBlankTargetsAreDiscarded() {
        assertNull(
            PlaybackProfile(
                transitionMode = PlaybackTransitionMode.Pause,
                crossfadeDurationSeconds = 7,
            ).normalized().crossfadeDurationSeconds,
        )
        assertNull(PlaybackProfileTarget(PlaybackProfileTargetType.Album, "  ").normalized())
        assertEquals(
            "album-1",
            PlaybackProfileTarget(PlaybackProfileTargetType.Album, " album-1 ").normalized()?.id,
        )
    }
}
