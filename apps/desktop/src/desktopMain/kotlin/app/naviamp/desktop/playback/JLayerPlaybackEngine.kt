package app.naviamp.desktop.playback

import javazoom.jl.player.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.URI

class JLayerPlaybackEngine : PlaybackEngine {
    override val name: String = "JLayer"
    override val supportsPause: Boolean = false
    override val supportsSeek: Boolean = false
    override val supportsGapless: Boolean = false
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = false
    override val prefersOriginalStream: Boolean = false

    private var job: Job? = null
    private var stream: InputStream? = null
    private var player: Player? = null
    private var playbackId = 0

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ) {
        stop()
        val currentPlaybackId = nextPlaybackId()
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        job = scope.launch(Dispatchers.IO) {
            var currentStream: InputStream? = null
            var currentPlayer: Player? = null
            try {
                val inputStream = URI.create(request.url).toURL().openStream().buffered()
                currentStream = inputStream
                stream = inputStream
                currentPlayer = Player(inputStream)
                player = currentPlayer
                onStateChanged(PlaybackState.Playing)
                currentPlayer.play()
                if (isCurrentPlayback(currentPlaybackId)) {
                    onStateChanged(PlaybackState.Finished)
                }
            } catch (exception: Throwable) {
                if (isCurrentPlayback(currentPlaybackId) && job?.isCancelled != true) {
                    onStateChanged(PlaybackState.Error(exception.message ?: "Playback failed."))
                }
            } finally {
                closePlayback(currentPlayer, currentStream)
            }
        }
    }

    override fun stop() {
        playbackId += 1
        job?.cancel()
        closeCurrent()
    }

    override fun pause() = Unit

    override fun resume() = Unit

    override fun seek(positionSeconds: Double) = Unit

    private fun closeCurrent() {
        closePlayback(player, stream)
    }

    private fun closePlayback(currentPlayer: Player?, currentStream: InputStream?) {
        currentPlayer?.close()
        currentStream?.close()
        if (player == currentPlayer) {
            player = null
        }
        if (stream == currentStream) {
            stream = null
        }
    }

    private fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    private fun isCurrentPlayback(id: Int): Boolean =
        playbackId == id
}
