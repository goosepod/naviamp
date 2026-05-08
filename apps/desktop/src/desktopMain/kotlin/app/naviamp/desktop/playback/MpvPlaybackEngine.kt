package app.naviamp.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MpvPlaybackEngine(
    private val executable: String,
) : PlaybackEngine {
    override val name: String = "mpv"
    override val supportsPause: Boolean = true

    private var job: Job? = null
    private var process: Process? = null
    private var ipcSocket: File? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
    ) {
        stop()
        this.onStateChanged = onStateChanged
        onStateChanged(PlaybackState.Loading)

        job = scope.launch(Dispatchers.IO) {
            try {
                val socket = createIpcSocketFile()
                ipcSocket = socket
                val currentProcess = ProcessBuilder(
                    executable,
                    "--no-video",
                    "--really-quiet",
                    "--input-ipc-server=${socket.absolutePath}",
                    request.url,
                )
                    .redirectErrorStream(true)
                    .start()
                process = currentProcess
                onStateChanged(PlaybackState.Playing)

                val exitCode = currentProcess.waitFor()
                if (exitCode == 0) {
                    onStateChanged(PlaybackState.Finished)
                } else if (job?.isCancelled != true) {
                    onStateChanged(PlaybackState.Error("mpv exited with code $exitCode."))
                }
            } catch (exception: Throwable) {
                if (job?.isCancelled != true) {
                    onStateChanged(PlaybackState.Error(exception.message ?: "mpv playback failed."))
                }
            } finally {
                ipcSocket?.delete()
                ipcSocket = null
                process = null
                this@MpvPlaybackEngine.onStateChanged = null
            }
        }
    }

    override fun pause() {
        if (command("""{"command":["set_property","pause",true]}""")) {
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    override fun resume() {
        if (command("""{"command":["set_property","pause",false]}""")) {
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    override fun stop() {
        job?.cancel()
        process?.stop()
        process = null
        ipcSocket?.delete()
        ipcSocket = null
        onStateChanged = null
    }

    private fun command(command: String): Boolean {
        val socket = ipcSocket ?: return false
        return try {
            waitForSocket(socket)
            SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                channel.write(ByteBuffer.wrap("$command\n".toByteArray(StandardCharsets.UTF_8)))
            }
            true
        } catch (exception: Throwable) {
            onStateChanged?.invoke(PlaybackState.Error(exception.message ?: "mpv command failed."))
            false
        }
    }

    private fun createIpcSocketFile(): File {
        val socket = File(
            System.getProperty("java.io.tmpdir"),
            "naviamp-mpv-${System.nanoTime()}.sock",
        )
        socket.delete()
        return socket
    }

    private fun waitForSocket(socket: File) {
        repeat(20) {
            if (socket.exists()) return
            Thread.sleep(25)
        }
    }

    private fun Process.stop() {
        destroy()
        if (!waitFor(1, TimeUnit.SECONDS)) {
            destroyForcibly()
        }
    }
}

object PlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val mpv = findExecutable("mpv")
        return if (mpv != null) {
            MpvPlaybackEngine(mpv)
        } else {
            JLayerPlaybackEngine()
        }
    }

    private fun findExecutable(name: String): String? {
        val path = System.getenv("PATH") ?: return null
        return path
            .split(File.pathSeparator)
            .map { File(it, name) }
            .firstOrNull { it.canExecute() }
            ?.absolutePath
    }
}
