package app.naviamp.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackCommandsTest {
    @Test
    fun playbackVolumeCommandClampsSupportedSoftwareVolume() {
        assertEquals(
            PlaybackVolumeCommand(volumePercent = 0, shouldApplyToEngine = true),
            playbackVolumeCommand(requestedPercent = -10, supportsSoftwareVolume = true),
        )
        assertEquals(
            PlaybackVolumeCommand(volumePercent = 100, shouldApplyToEngine = true),
            playbackVolumeCommand(requestedPercent = 120, supportsSoftwareVolume = true),
        )
    }

    @Test
    fun playbackVolumeCommandUsesFullVolumeWhenSoftwareVolumeIsUnsupported() {
        assertEquals(
            PlaybackVolumeCommand(volumePercent = 100, shouldApplyToEngine = false),
            playbackVolumeCommand(requestedPercent = 25, supportsSoftwareVolume = false),
        )
    }

    @Test
    fun playbackPlayPauseCommandMapsStateAndAvailableTargets() {
        assertEquals(
            PlaybackPlayPauseCommand.Pause,
            playbackPlayPauseCommand(PlaybackState.Playing, hasPlaybackTarget = false),
        )
        assertEquals(
            PlaybackPlayPauseCommand.Resume,
            playbackPlayPauseCommand(PlaybackState.Paused, hasPlaybackTarget = false),
        )
        assertEquals(
            PlaybackPlayPauseCommand.StartOrRestore,
            playbackPlayPauseCommand(PlaybackState.Stopped, hasPlaybackTarget = true),
        )
        assertEquals(
            PlaybackPlayPauseCommand.None,
            playbackPlayPauseCommand(PlaybackState.Idle, hasPlaybackTarget = false),
        )
    }
}
