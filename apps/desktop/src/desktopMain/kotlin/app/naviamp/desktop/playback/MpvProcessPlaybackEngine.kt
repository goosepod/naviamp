package app.naviamp.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MpvProcessPlaybackEngine(
    private val executable: String,
) : PlaybackEngine {
    override val name: String = "mpv"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = false
    override val prefersOriginalStream: Boolean = true

    private var job: Job? = null
    private var progressJob: Job? = null
    private var process: Process? = null
    private var ipcSocket: File? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var playbackId = 0
    private val json = Json { ignoreUnknownKeys = true }

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                stop()
            },
        )
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ) {
        stop()
        val currentPlaybackId = nextPlaybackId()
        this.onStateChanged = onStateChanged
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        job = scope.launch(Dispatchers.IO) {
            var currentProcess: Process? = null
            var currentSocket: File? = null
            var currentProgressJob: Job? = null
            try {
                val socket = createIpcSocketFile()
                currentSocket = socket
                ipcSocket = socket
                currentProcess = ProcessBuilder(
                    executable,
                    "--no-video",
                    "--really-quiet",
                    "--gapless-audio=yes",
                    "--input-ipc-server=${socket.absolutePath}",
                    request.url,
                )
                    .redirectErrorStream(true)
                    .start()
                process = currentProcess
                onStateChanged(PlaybackState.Playing)
                currentProgressJob = scope.launch(Dispatchers.IO) {
                    waitForSocket(socket)
                    while (currentProcess.isAlive) {
                        onProgressChanged(
                            PlaybackProgress(
                                positionSeconds = queryDouble("time-pos"),
                                durationSeconds = queryDouble("duration"),
                            ),
                        )
                        delay(500)
                    }
                }
                progressJob = currentProgressJob

                val exitCode = currentProcess.waitFor()
                if (!isCurrentPlayback(currentPlaybackId)) {
                    return@launch
                }
                if (exitCode == 0) {
                    onStateChanged(PlaybackState.Finished)
                } else if (job?.isCancelled != true) {
                    onStateChanged(PlaybackState.Error("mpv exited with code $exitCode."))
                }
            } catch (exception: Throwable) {
                if (isCurrentPlayback(currentPlaybackId) && job?.isCancelled != true) {
                    onStateChanged(PlaybackState.Error(exception.message ?: "mpv playback failed."))
                }
            } finally {
                currentProgressJob?.cancel()
                if (progressJob == currentProgressJob) {
                    progressJob = null
                }
                if (isCurrentPlayback(currentPlaybackId)) {
                    onProgressChanged(PlaybackProgress.Unknown)
                }
                currentSocket?.delete()
                if (ipcSocket == currentSocket) {
                    ipcSocket = null
                }
                if (isCurrentPlayback(currentPlaybackId) && process == currentProcess) {
                    process = null
                    this@MpvProcessPlaybackEngine.onStateChanged = null
                }
            }
        }
    }

    override fun pause() {
        if (sendIpcCommand("""{"command":["set_property","pause",true]}""") != null) {
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    override fun resume() {
        if (sendIpcCommand("""{"command":["set_property","pause",false]}""") != null) {
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    override fun seek(positionSeconds: Double) {
        sendIpcCommand("""{"command":["seek",$positionSeconds,"absolute+exact"]}""")
    }

    override fun stop() {
        playbackId += 1
        val currentProcess = process
        if (currentProcess != null && currentProcess.isAlive) {
            sendIpcCommand("""{"command":["quit"]}""", reportErrors = false)
            if (!currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                currentProcess.destroy()
            }
            if (!currentProcess.waitFor(1, TimeUnit.SECONDS)) {
                currentProcess.destroyForcibly()
            }
        }

        job?.cancel()
        progressJob?.cancel()
        progressJob = null
        process = null
        ipcSocket?.delete()
        ipcSocket = null
        onStateChanged = null
    }

    private fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    private fun isCurrentPlayback(id: Int): Boolean =
        playbackId == id

    private fun queryDouble(property: String): Double? {
        val response = sendIpcCommand("""{"command":["get_property","$property"]}""") ?: return null
        val data = json.parseToJsonElement(response)
            .jsonObject["data"]
            ?: return null

        return data.doubleValue()
    }

    private fun sendIpcCommand(command: String, reportErrors: Boolean = true): String? {
        val socket = ipcSocket ?: return null
        return try {
            waitForSocket(socket)
            SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                channel.write(ByteBuffer.wrap("$command\n".toByteArray(StandardCharsets.UTF_8)))
                channel.readLine()
            }
        } catch (exception: Throwable) {
            if (reportErrors) {
                onStateChanged?.invoke(PlaybackState.Error(exception.message ?: "mpv command failed."))
            }
            null
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

}

private fun SocketChannel.readLine(): String {
    val buffer = ByteBuffer.allocate(4096)
    val builder = StringBuilder()

    while (read(buffer) > 0) {
        buffer.flip()
        builder.append(StandardCharsets.UTF_8.decode(buffer).toString())
        if (builder.contains('\n')) break
        buffer.clear()
    }

    return builder.toString().lineSequence().firstOrNull().orEmpty()
}

private fun JsonElement.doubleValue(): Double? =
    runCatching { jsonPrimitive.doubleOrNull }.getOrNull()

object PlaybackEngineFactory {
    fun createDefault(): PlaybackEngine {
        val mpv = findExecutable("mpv")
        return if (mpv != null) {
            MpvProcessPlaybackEngine(mpv)
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
