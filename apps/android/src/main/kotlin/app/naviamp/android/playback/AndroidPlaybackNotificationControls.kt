package app.naviamp.android.playback

object AndroidPlaybackNotificationControls {
    @Volatile
    var isPlaying: Boolean = false

    @Volatile
    var onPlayPause: (() -> Unit)? = null

    @Volatile
    var onPrevious: (() -> Unit)? = null

    @Volatile
    var onNext: (() -> Unit)? = null

    @Volatile
    var onStop: (() -> Unit)? = null

    fun clear() {
        isPlaying = false
        onPlayPause = null
        onPrevious = null
        onNext = null
        onStop = null
    }
}

data class AndroidPlaybackNotificationMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val coverArtUrl: String? = null,
)
