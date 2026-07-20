package app.naviamp.domain.playback

data class BassPlaybackExecutionCallbacks(
    val onStateChanged: (PlaybackState) -> Unit,
    val onProgressChanged: (PlaybackProgress) -> Unit,
    val onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
)

class BassPlaybackExecutionCoordinator {
    var currentRequest: PlaybackRequest? = null
        private set
    var callbacks: BassPlaybackExecutionCallbacks? = null
        private set
    private var playbackId: Int = 0

    val currentPlaybackId: Int
        get() = playbackId

    fun attach(
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        currentRequest = request
        callbacks = BassPlaybackExecutionCallbacks(onStateChanged, onProgressChanged, onMetadataChanged)
    }

    fun updateRequest(request: PlaybackRequest) {
        currentRequest = request
    }

    fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    fun isCurrent(playbackId: Int): Boolean = this.playbackId == playbackId

    fun invalidate() {
        nextPlaybackId()
    }

    fun clearRequest() {
        currentRequest = null
    }

    fun clear() {
        invalidate()
        currentRequest = null
        callbacks = null
    }

    fun publishState(state: PlaybackState) = callbacks?.onStateChanged?.invoke(state)

    fun publishProgress(progress: PlaybackProgress) = callbacks?.onProgressChanged?.invoke(progress)

    fun publishMetadata(metadata: PlaybackStreamMetadata) = callbacks?.onMetadataChanged?.invoke(metadata)
}
