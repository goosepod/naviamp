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

    private var job: Job? = null
    private var stream: InputStream? = null
    private var player: Player? = null

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
    ) {
        stop()
        onStateChanged(PlaybackState.Loading)

        job = scope.launch(Dispatchers.IO) {
            try {
                val inputStream = URI.create(request.url).toURL().openStream().buffered()
                stream = inputStream
                val currentPlayer = Player(inputStream)
                player = currentPlayer
                onStateChanged(PlaybackState.Playing)
                currentPlayer.play()
                onStateChanged(PlaybackState.Finished)
            } catch (exception: Throwable) {
                if (job?.isCancelled != true) {
                    onStateChanged(PlaybackState.Error(exception.message ?: "Playback failed."))
                }
            } finally {
                closeCurrent()
            }
        }
    }

    override fun stop() {
        job?.cancel()
        closeCurrent()
    }

    private fun closeCurrent() {
        player?.close()
        player = null
        stream?.close()
        stream = null
    }
}
