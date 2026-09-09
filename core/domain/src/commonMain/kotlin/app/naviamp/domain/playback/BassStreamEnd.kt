package app.naviamp.domain.playback

/** Only a disconnected, incomplete finite download is an error; tags after audio may be absent. */
fun bassStreamEndState(
    fileSizeBytes: Long?,
    audioEndBytes: Long?,
    downloadedBytes: Long?,
    connected: Long?,
): PlaybackState = if (
    fileSizeBytes != null && fileSizeBytes > 0 &&
    audioEndBytes != null && audioEndBytes > 0 && audioEndBytes <= fileSizeBytes &&
    downloadedBytes != null && downloadedBytes >= 0 && downloadedBytes < audioEndBytes && connected == 0L
) PlaybackState.Error("BASS playback failed.") else PlaybackState.Finished
