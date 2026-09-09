package app.naviamp.domain.playback

/** Live sources cannot finish normally. Finite sources require evidence of truncation. */
fun bassStreamEndState(
    fileSizeBytes: Long?,
    audioEndBytes: Long?,
    downloadedBytes: Long?,
    connected: Long?,
    isLive: Boolean = false,
): PlaybackState = if (isLive || (
    fileSizeBytes != null && fileSizeBytes > 0 &&
    audioEndBytes != null && audioEndBytes > 0 && audioEndBytes <= fileSizeBytes &&
    downloadedBytes != null && downloadedBytes >= 0 && downloadedBytes < audioEndBytes && connected == 0L
)) PlaybackState.Error("BASS playback failed.") else PlaybackState.Finished
