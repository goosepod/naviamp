package app.naviamp.desktop.playback

import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private var job: Job? = null
    private var progressJob: Job? = null
    private var metadataJob: Job? = null
    private var process: Process? = null
    private var ipcEndpoint: MpvIpcEndpoint? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var playbackId = 0
    private var volumePercent = 100
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
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
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
            var currentMetadataJob: Job? = null
            try {
                val endpoint = createIpcEndpoint()
                currentEndpoint = endpoint
                ipcEndpoint = endpoint
                val args = mutableListOf(
                    executable,
                    "--no-video",
                    "--really-quiet",
                    "--gapless-audio=yes",
                    "--replaygain=${request.replayGainMode.mpvValue()}",
                    "--volume=$volumePercent",
                    "--input-ipc-server=${endpoint.mpvPath}",
                )
                request.startPositionSeconds
                    ?.takeIf { it > 0.0 }
                    ?.let { args += "--start=$it" }
                args += request.url
                currentProcess = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start()
                process = currentProcess
                endpoint.waitUntilReady()
                onStateChanged(PlaybackState.Playing)
                val metadataState = StreamMetadataState()
                currentMetadataJob = scope.launch(Dispatchers.IO) {
                    endpoint.listenForEvents(
                        commands = listOf(
                            """{"command":["observe_property",1,"metadata"]}""",
                            """{"command":["observe_property",2,"media-title"]}""",
                        ),
                    ) { line ->
                        if (!currentProcess.isAlive || !isCurrentPlayback(currentPlaybackId)) return@listenForEvents false
                        val event = parseIpcEvent(line) ?: return@listenForEvents true
                        if (!isCurrentPlayback(currentPlaybackId)) return@listenForEvents false
                        val metadata = when (event.propertyName) {
                            "metadata" -> metadataState.updateProperties(event.data?.metadataProperties().orEmpty())
                            "media-title" -> metadataState.updateMediaTitle(event.data?.metadataString())
                            else -> null
                        }
                        metadata?.let(onMetadataChanged)
                        true
                    }
                }
                metadataJob = currentMetadataJob
                currentProgressJob = scope.launch(Dispatchers.IO) {
                    var lastMetadata = PlaybackStreamMetadata()
                    while (currentProcess.isAlive) {
                        onProgressChanged(
                            PlaybackProgress(
                                positionSeconds = queryDouble("time-pos"),
                                durationSeconds = queryDouble("duration"),
                            ),
                        )
                        val metadata = queryStreamMetadata()
                        if (metadata != lastMetadata) {
                            lastMetadata = metadata
                            onMetadataChanged(metadata)
                        }
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
                currentMetadataJob?.cancel()
                if (progressJob == currentProgressJob) {
                    progressJob = null
                }
                if (metadataJob == currentMetadataJob) {
                    metadataJob = null
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
        if (sendIpcCommand("""{"command":["set_property","pause",true]}""", reportErrors = false) != null) {
            onStateChanged?.invoke(PlaybackState.Paused)
        }
    }

    override fun resume() {
        if (sendIpcCommand("""{"command":["set_property","pause",false]}""", reportErrors = false) != null) {
            onStateChanged?.invoke(PlaybackState.Playing)
        }
    }

    override fun seek(positionSeconds: Double) {
        sendIpcCommand("""{"command":["seek",$positionSeconds,"absolute+exact"]}""")
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        sendIpcCommand("""{"command":["set_property","volume",$volumePercent]}""", reportErrors = false)
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
        metadataJob?.cancel()
        progressJob = null
        metadataJob = null
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
        val data = queryProperty(property) ?: return null
        return data.doubleValue()
    }

    private fun queryStreamMetadata(): PlaybackStreamMetadata {
        val properties = queryProperty("metadata")
            ?.metadataProperties()
            .orEmpty()
        val mediaTitle = queryProperty("media-title")?.metadataString()
        return PlaybackStreamMetadata.fromProperties(
            properties = properties + listOfNotNull(mediaTitle?.let { "media-title" to it }),
            fallbackTitle = mediaTitle,
        )
    }

    private fun queryProperty(property: String): JsonElement? {
        val response = sendIpcCommand(
            command = """{"command":["get_property","$property"]}""",
            reportErrors = false,
        ) ?: return null
        if (response.isBlank()) return null

        val data = runCatching {
            json.parseToJsonElement(response).jsonObject["data"]
        }.getOrNull() ?: return null

        return data
    }

    private fun sendIpcCommand(command: String, reportErrors: Boolean = true): String? {
        val endpoint = ipcEndpoint ?: return null
        return try {
            endpoint.send("$command\n", expectResponse = true)
        } catch (exception: Throwable) {
            if (reportErrors) {
                onStateChanged?.invoke(PlaybackState.Error(exception.message ?: "mpv command failed."))
            }
            null
        }
    }

    private fun parseIpcEvent(line: String): MpvIpcEvent? {
        if (line.isBlank()) return null
        return runCatching {
            val jsonObject = json.parseToJsonElement(line).jsonObject
            if (jsonObject["event"]?.metadataString() != "property-change") return@runCatching null
            MpvIpcEvent(
                propertyName = jsonObject["name"]?.metadataString(),
                data = jsonObject["data"],
            )
        }.getOrNull()
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

    fun send(command: String, expectResponse: Boolean): String

    fun readEventLine(): String

    fun listenForEvents(commands: List<String>, onLine: (String) -> Boolean)

    fun delete()

    data class UnixSocket(
        val socket: File,
    ) : MpvIpcEndpoint {
        override val mpvPath: String = socket.absolutePath

        override fun waitUntilReady() {
            repeat(160) {
                if (socket.exists()) return
                Thread.sleep(25)
            }
        }

        override fun send(command: String, expectResponse: Boolean): String {
            waitUntilReady()
            return SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                channel.write(ByteBuffer.wrap(command.toByteArray(StandardCharsets.UTF_8)))
                if (expectResponse) channel.readLine() else ""
            }
        }

        override fun readEventLine(): String {
            waitUntilReady()
            return SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                channel.readLine()
            }
        }

        override fun listenForEvents(commands: List<String>, onLine: (String) -> Boolean) {
            waitUntilReady()
            SocketChannel.open(UnixDomainSocketAddress.of(socket.toPath())).use { channel ->
                commands.forEach { command ->
                    channel.write(ByteBuffer.wrap("$command\n".toByteArray(StandardCharsets.UTF_8)))
                }
                while (true) {
                    val line = channel.readLine()
                    if (line.isBlank() || !onLine(line)) return
                }
            }
        }

        override fun delete() {
            socket.delete()
        }
    }

    data class WindowsNamedPipe(
        override val mpvPath: String,
    ) : MpvIpcEndpoint {
        private val lock = Any()

        override fun waitUntilReady() {
            repeat(160) {
                runCatching {
                    RandomAccessFile(mpvPath, "rw").use { }
                }.onSuccess {
                    return
                }
                Thread.sleep(25)
            }
        }

        override fun send(command: String, expectResponse: Boolean): String {
            synchronized(lock) {
                waitUntilReady()
                var lastException: Throwable? = null
                repeat(20) {
                    try {
                        return RandomAccessFile(mpvPath, "rw").use { pipe ->
                            pipe.write(command.toByteArray(StandardCharsets.UTF_8))
                            if (expectResponse) pipe.readUtf8Line() else ""
                        }
                    } catch (exception: Throwable) {
                        lastException = exception
                        if (!exception.isWindowsPipeBusy()) {
                            throw exception
                        }
                        Thread.sleep(25)
                    }
                }
                throw lastException ?: IllegalStateException("mpv pipe was busy.")
            }
        }

        override fun readEventLine(): String =
            synchronized(lock) {
                waitUntilReady()
                RandomAccessFile(mpvPath, "rw").use { pipe ->
                    pipe.readUtf8Line()
                }
            }

        override fun listenForEvents(commands: List<String>, onLine: (String) -> Boolean) {
            // mpv's Windows named-pipe IPC is effectively single-client for this use.
            // Keeping an observe_property connection open can make later command opens fail
            // with "All pipe instances are busy" during rapid track changes. Metadata is
            // still refreshed by the polling path, so avoid the long-lived event handle here.
        }

        override fun delete() = Unit
    }
}

private data class MpvIpcEvent(
    val propertyName: String?,
    val data: JsonElement?,
)

private class StreamMetadataState {
    private var properties: Map<String, String> = emptyMap()
    private var mediaTitle: String? = null
    private var lastMetadata = PlaybackStreamMetadata()

    fun updateProperties(properties: Map<String, String>): PlaybackStreamMetadata? {
        this.properties = properties
        return nextMetadata()
    }

    fun updateMediaTitle(mediaTitle: String?): PlaybackStreamMetadata? {
        this.mediaTitle = mediaTitle
        return nextMetadata()
    }

    private fun nextMetadata(): PlaybackStreamMetadata? {
        val metadata = PlaybackStreamMetadata.fromProperties(
            properties = properties + listOfNotNull(mediaTitle?.let { "media-title" to it }),
            fallbackTitle = mediaTitle,
        )
        if (metadata == lastMetadata) return null
        lastMetadata = metadata
        return metadata
    }
}

private fun Throwable.isWindowsPipeBusy(): Boolean =
    message?.contains("pipe instances are busy", ignoreCase = true) == true

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

private fun JsonElement.metadataString(): String? =
    runCatching { jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() } }.getOrNull()

private fun JsonElement.metadataProperties(): Map<String, String> =
    jsonObjectOrNull()
        ?.mapNotNull { (key, value) ->
            value.metadataString()?.let { key to it }
        }
        ?.toMap()
        .orEmpty()

private fun JsonElement.jsonObjectOrNull(): JsonObject? =
    runCatching { jsonObject }.getOrNull()

private fun ReplayGainMode.mpvValue(): String =
    when (this) {
        ReplayGainMode.Off -> "no"
        ReplayGainMode.Track -> "track"
        ReplayGainMode.Album -> "album"
    }
