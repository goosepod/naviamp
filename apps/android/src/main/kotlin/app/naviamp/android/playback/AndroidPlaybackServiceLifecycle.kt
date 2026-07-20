package app.naviamp.android.playback

internal enum class AndroidPlaybackServiceRetention {
    KeepAlive,
    Stop,
}

internal fun androidPlaybackServiceRetention(ownsPlayback: Boolean): AndroidPlaybackServiceRetention =
    if (ownsPlayback) AndroidPlaybackServiceRetention.KeepAlive else AndroidPlaybackServiceRetention.Stop

internal data class AndroidPlaybackServiceStartPlan(
    val stickyRestart: Boolean,
    val publishNotification: Boolean,
    val republishMediaSession: Boolean,
)

internal fun planAndroidPlaybackServiceStart(intentPresent: Boolean): AndroidPlaybackServiceStartPlan =
    AndroidPlaybackServiceStartPlan(
        stickyRestart = !intentPresent,
        publishNotification = true,
        republishMediaSession = !intentPresent,
    )
