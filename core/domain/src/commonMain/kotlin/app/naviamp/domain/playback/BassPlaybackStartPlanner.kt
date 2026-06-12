package app.naviamp.domain.playback

data class BassPlaybackStartPolicy(
    val seekBeforePlay: Boolean,
    val retrySeekAfterPlayWhenInitialSeekFails: Boolean,
    val muteBeforePlayWhenRetryingSeek: Boolean,
) {
    companion object {
        val AndroidService: BassPlaybackStartPolicy =
            BassPlaybackStartPolicy(
                seekBeforePlay = true,
                retrySeekAfterPlayWhenInitialSeekFails = true,
                muteBeforePlayWhenRetryingSeek = true,
            )

        val DesktopEngine: BassPlaybackStartPolicy =
            BassPlaybackStartPolicy(
                seekBeforePlay = true,
                retrySeekAfterPlayWhenInitialSeekFails = false,
                muteBeforePlayWhenRetryingSeek = false,
            )
    }
}

data class BassPlaybackStartPlan(
    val startSeekSeconds: Double?,
    val shouldSeekBeforePlay: Boolean,
    val policy: BassPlaybackStartPolicy,
)

data class BassPlaybackPrePlayPlan(
    val shouldMuteBeforePlay: Boolean,
    val shouldRetrySeekAfterPlay: Boolean,
)

fun planBassPlaybackStart(
    request: PlaybackRequest,
    policy: BassPlaybackStartPolicy,
): BassPlaybackStartPlan =
    BassPlaybackStartPlan(
        startSeekSeconds = playbackStartSeekPosition(request.startPositionSeconds),
        shouldSeekBeforePlay = policy.seekBeforePlay,
        policy = policy,
    )

fun planBassPlaybackPrePlay(
    start: BassPlaybackStartPlan,
    seekedBeforePlay: Boolean,
): BassPlaybackPrePlayPlan {
    val needsRetry = start.startSeekSeconds != null &&
        !seekedBeforePlay &&
        start.policy.retrySeekAfterPlayWhenInitialSeekFails
    return BassPlaybackPrePlayPlan(
        shouldMuteBeforePlay = needsRetry && start.policy.muteBeforePlayWhenRetryingSeek,
        shouldRetrySeekAfterPlay = needsRetry,
    )
}
