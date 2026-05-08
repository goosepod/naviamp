package app.naviamp.desktop

import javazoom.jl.player.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.URI

class DesktopAudioPlayer {
    private var job: Job? = null
    private var stream: InputStream? = null
    private var player: Player? = null

    fun play(
        scope: CoroutineScope,
        url: String,
        onFinished: () -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        stop()

        job = scope.launch(Dispatchers.IO) {
            try {
                val inputStream = URI.create(url).toURL().openStream().buffered()
                stream = inputStream
                val currentPlayer = Player(inputStream)
                player = currentPlayer
                currentPlayer.play()
                onFinished()
            } catch (exception: Throwable) {
                if (job?.isCancelled != true) {
                    onError(exception)
                }
            } finally {
                closeCurrent()
            }
        }
    }

    fun stop() {
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
