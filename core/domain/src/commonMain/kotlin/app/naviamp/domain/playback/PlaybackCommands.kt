package app.naviamp.domain.playback

data class PlaybackVolumeCommand(
    val volumePercent: Int,
    val shouldApplyToEngine: Boolean,
)

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
