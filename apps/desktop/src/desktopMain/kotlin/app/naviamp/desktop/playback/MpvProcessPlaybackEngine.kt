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
import java.io.RandomAccessFile
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class MpvProcessPlaybackEngine(
    private val executable: String,
    private val platform: MpvIpcPlatform = MpvIpcPlatform.current(),
) : PlaybackEngine {
    override val name: String = "mpv"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = false
    override val supportsReplayGain: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private var job: Job? = null
    private var progressJob: Job? = null
    private var process: Process? = null
    private var ipcEndpoint: MpvIpcEndpoint? = null
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
            var currentEndpoint: MpvIpcEndpoint? = null
            var currentProgressJob: Job? = null
            try {
                val endpoint = createIpcEndpoint()
                currentEndpoint = endpoint
                ipcEndpoint = endpoint
                currentProcess = ProcessBuilder(
                    executable,
                    "--no-video",
                    "--really-quiet",
                    "--gapless-audio=yes",
                    "--replaygain=${request.replayGainMode.mpvValue()}",
                    "--input-ipc-server=${endpoint.mpvPath}",
                    request.url,
                )
                    .redirectErrorStream(true)
                    .start()
                process = currentProcess
                onStateChanged(PlaybackState.Playing)
                currentProgressJob = scope.launch(Dispatchers.IO) {
                    endpoint.waitUntilReady()
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
                currentEndpoint?.delete()
                if (ipcEndpoint == currentEndpoint) {
                    ipcEndpoint = null
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
        ipcEndpoint?.delete()
        ipcEndpoint = null
        onStateChanged = null
    }

    private fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    private fun isCurrentPlayback(id: Int): Boolean =
        playbackId == id

    private fun queryDouble(property: String): Double? {
        val response = sendIpcCommand(
            command = """{"command":["get_property","$property"]}""",
            reportErrors = false,
        ) ?: return null
        if (response.isBlank()) return null

        val data = runCatching {
            json.parseToJsonElement(response).jsonObject["data"]
        }.getOrNull() ?: return null

        return data.doubleValue()
    }

    private fun sendIpcCommand(command: String, reportErrors: Boolean = true): String? {
        val endpoint = ipcEndpoint ?: return null
        return try {
            endpoint.send("$command\n")
        } catch (exception: Throwable) {
            if (reportErrors) {
                onStateChanged?.invoke(PlaybackState.Error(exception.message ?: "mpv command failed."))
            }
            null
        }
    }

    private fun createIpcEndpoint(): MpvIpcEndpoint =
        when (platform) {
            MpvIpcPlatform.Windows -> {
                val pipeName = "naviamp-mpv-${System.nanoTime()}"
                MpvIpcEndpoint.WindowsNamedPipe("\\\\.\\pipe\\$pipeName")
            }
            MpvIpcPlatform.Unix -> {
                val socket = File(
                    System.getProperty("java.io.tmpdir"),
                    "naviamp-mpv-${System.nanoTime()}.sock",
                )
                socket.delete()
                MpvIpcEndpoint.UnixSocket(socket)
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

enum class MpvIpcPlatform {
    Windows,
    Unix,
    ;

    companion object {
        fun current(): MpvIpcPlatform =
            if (System.getProperty("os.name").contains("win", ignoreCase = true)) {
                Windows
            } else {
                Unix
            }
    }
}

sealed interface MpvIpcEndpoint {
    val mpvPath: String

    fun waitUntilReady()

    fun send(command: String): String

    fun delete()

    data class UnixSocket(
        val socket: File,
    ) : MpvIpcEndpoint {
        override val mpvPath: String = socket.absolutePath

        override fun waitUntilReady() {
            repeat(40) {
                if (socket.exists()) return
                Thread.sleep(25)
            }
        }

        override fun send(command: String): String {
            waitUntilReady()
            return SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                channel.write(ByteBuffer.wrap(command.toByteArray(StandardCharsets.UTF_8)))
                channel.readLine()
            }
        }

        override fun delete() {
            socket.delete()
        }
    }

    data class WindowsNamedPipe(
        override val mpvPath: String,
    ) : MpvIpcEndpoint {
        override fun waitUntilReady() {
            repeat(40) {
                runCatching {
                    RandomAccessFile(mpvPath, "rw").use { }
                }.onSuccess {
                    return
                }
                Thread.sleep(25)
            }
        }

        override fun send(command: String): String {
            waitUntilReady()
            return RandomAccessFile(mpvPath, "rw").use { pipe ->
                pipe.write(command.toByteArray(StandardCharsets.UTF_8))
                pipe.readUtf8Line()
            }
        }

        override fun delete() = Unit
    }
}

private fun RandomAccessFile.readUtf8Line(): String {
    val bytes = mutableListOf<Byte>()
    while (true) {
        val value = read()
        if (value == -1 || value == '\n'.code) break
        bytes.add(value.toByte())
    }
    return bytes.toByteArray().toString(StandardCharsets.UTF_8)
}

private fun JsonElement.doubleValue(): Double? =
    runCatching { jsonPrimitive.doubleOrNull }.getOrNull()

private fun ReplayGainMode.mpvValue(): String =
    when (this) {
        ReplayGainMode.Off -> "no"
        ReplayGainMode.Track -> "track"
        ReplayGainMode.Album -> "album"
    }
