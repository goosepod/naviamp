package app.naviamp.android.playback

object AndroidPlaybackNotificationControls {
    @Volatile
    var isPlaying: Boolean = false

    @Volatile
    var canFavorite: Boolean = false

    @Volatile
    var isFavorite: Boolean = false

    @Volatile
    var positionMillis: Long? = null

    @Volatile
    var durationMillis: Long? = null

    @Volatile
    var onPlayPause: (() -> Unit)? = null

    @Volatile
    var onPrevious: (() -> Unit)? = null

    @Volatile
    var onNext: (() -> Unit)? = null

    @Volatile
    var onToggleFavorite: (() -> Unit)? = null

    @Volatile
    var onStop: (() -> Unit)? = null

    @Volatile
    var onSeekTo: ((Long) -> Unit)? = null

    fun clear() {
        isPlaying = false
        canFavorite = false
        isFavorite = false
        positionMillis = null
        durationMillis = null
        onPlayPause = null
        onPrevious = null
        onNext = null
        onToggleFavorite = null
        onStop = null
        onSeekTo = null
    }
}

data class AndroidPlaybackNotificationMetadata(
    val title: String? = null,
    val subtitle: String? = null,
    val coverArtUrl: String? = null,
)
