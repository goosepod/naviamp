package app.naviamp.desktop.playback

import app.naviamp.domain.StreamQuality
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PlaylistEngine(
    private val playbackEngine: PlaybackEngine,
) {
    private var provider: MediaProvider? = null
    private var streamQuality: StreamQuality? = null
    private var callbacks: PlaylistCallbacks? = null
    private var sessionId = 0

    var queue: PlaybackQueue = PlaybackQueue()
        private set

    fun playFrom(
        scope: CoroutineScope,
        provider: MediaProvider,
        tracks: List<Track>,
        index: Int,
        quality: StreamQuality,
        callbacks: PlaylistCallbacks,
    ) {
        this.provider = provider
        this.streamQuality = quality
        this.callbacks = callbacks
        sessionId += 1

        playQueueIndex(scope, tracks, index, sessionId)
    }

    fun clear() {
        sessionId += 1
        queue = PlaybackQueue()
        playbackEngine.stop()
        callbacks?.onQueueChanged(queue)
    }

    private fun playQueueIndex(
        scope: CoroutineScope,
        tracks: List<Track>,
        index: Int,
        activeSessionId: Int,
    ) {
        val track = tracks.getOrNull(index) ?: return
        val currentProvider = provider ?: return
        val currentQuality = streamQuality ?: return
        val currentCallbacks = callbacks ?: return

        queue = PlaybackQueue(tracks = tracks, currentIndex = index)
        currentCallbacks.onQueueChanged(queue)
        currentCallbacks.onPlaybackProgressChanged(PlaybackProgress.Unknown)

        scope.launch {
            try {
                val streamUrl = currentProvider.streamUrl(
                    StreamRequest(
                        trackId = track.id,
                        quality = currentQuality,
                    ),
                )
                val coverArtUrl = track.coverArtId?.let { currentProvider.coverArtUrl(it) }
                currentCallbacks.onTrackStarted(track, coverArtUrl)
                playbackEngine.play(
                    scope = scope,
                    request = PlaybackRequest(streamUrl),
                    onStateChanged = { state ->
                        scope.launch {
                            handlePlaybackState(scope, state, activeSessionId)
                        }
                    },
                    onProgressChanged = { progress ->
                        scope.launch {
                            if (activeSessionId == sessionId) {
                                callbacks?.onPlaybackProgressChanged(progress)
                            }
                        }
                    },
                )
            } catch (exception: Exception) {
                if (activeSessionId == sessionId) {
                    currentCallbacks.onPlaybackStateChanged(
                        PlaybackState.Error(exception.message ?: "Playback failed."),
                    )
                }
            }
        }
    }

    private fun handlePlaybackState(
        scope: CoroutineScope,
        state: PlaybackState,
        activeSessionId: Int,
    ) {
        if (activeSessionId != sessionId) return

        if (state == PlaybackState.Finished && queue.hasNext()) {
            playQueueIndex(scope, queue.tracks, queue.currentIndex + 1, activeSessionId)
        } else {
            callbacks?.onPlaybackStateChanged(state)
        }
    }
}

data class PlaylistCallbacks(
    val onTrackStarted: (Track, String?) -> Unit,
    val onQueueChanged: (PlaybackQueue) -> Unit,
    val onPlaybackStateChanged: (PlaybackState) -> Unit,
    val onPlaybackProgressChanged: (PlaybackProgress) -> Unit,
)

data class PlaybackQueue(
    val tracks: List<Track> = emptyList(),
    val currentIndex: Int = -1,
) {
    fun upNext(): List<Track> =
        tracks.drop(currentIndex + 1)

    fun hasNext(): Boolean =
        currentIndex + 1 < tracks.size
}
