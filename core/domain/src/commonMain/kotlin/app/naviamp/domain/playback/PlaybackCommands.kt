package app.naviamp.domain.playback

data class PlaybackVolumeCommand(
    val volumePercent: Int,
    val shouldApplyToEngine: Boolean,
)

sealed interface PlaybackPlayPauseCommand {
    data object Pause : PlaybackPlayPauseCommand
    data object Resume : PlaybackPlayPauseCommand
    data object StartOrRestore : PlaybackPlayPauseCommand
    data object None : PlaybackPlayPauseCommand
}

fun playbackVolumeCommand(
    requestedPercent: Int,
    supportsSoftwareVolume: Boolean,
): PlaybackVolumeCommand =
    PlaybackVolumeCommand(
        volumePercent = if (supportsSoftwareVolume) {
            requestedPercent.coerceIn(0, 100)
        } else {
            100
        },
        shouldApplyToEngine = supportsSoftwareVolume,
    )

fun playbackPlayPauseCommand(
    playbackState: PlaybackState,
    hasPlaybackTarget: Boolean,
): PlaybackPlayPauseCommand =
    when (playbackState) {
        PlaybackState.Playing -> PlaybackPlayPauseCommand.Pause
        PlaybackState.Paused -> PlaybackPlayPauseCommand.Resume
        else -> if (hasPlaybackTarget) {
            PlaybackPlayPauseCommand.StartOrRestore
        } else {
            PlaybackPlayPauseCommand.None
        }
    }
