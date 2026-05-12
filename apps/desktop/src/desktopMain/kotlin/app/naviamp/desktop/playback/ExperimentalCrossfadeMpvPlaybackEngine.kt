package app.naviamp.desktop.playback

import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackRequest
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.QueueAwarePlaybackEngine
import app.naviamp.domain.playback.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

/**
 * Opt-in crossfade prototype. Keep the stable MpvProcessPlaybackEngine as the default
 * until this class has been exercised against real streams.
 */
class ExperimentalCrossfadeMpvPlaybackEngine(
    private val executable: String,
    private val platform: MpvIpcPlatform = MpvIpcPlatform.current(),
    private val trace: PlaybackTrace = PlaybackTrace.default(),
) : QueueAwarePlaybackEngine {
    override val name: String = "mpv crossfade prototype"
    override val supportsPause: Boolean = true
    override val supportsSeek: Boolean = true
    override val supportsGapless: Boolean = true
    override val supportsCrossfade: Boolean = true
    override val supportsReplayGain: Boolean = true
    override val supportsSoftwareVolume: Boolean = true
    override val prefersOriginalStream: Boolean = true

    private val json = Json { ignoreUnknownKeys = true }
    private var activeSlot: Slot? = null
    private var nextSlot: Slot? = null
    private var fadingOutSlot: Slot? = null
    private var fadingInSlot: Slot? = null
    private var progressJob: Job? = null
    private var fadeJob: Job? = null
    private var scope: CoroutineScope? = null
    private var onStateChanged: ((PlaybackState) -> Unit)? = null
    private var onProgressChanged: ((PlaybackProgress) -> Unit)? = null
    private var playbackId = 0
    private var crossfadeDurationSeconds = 0
    private var volumePercent = MaxVolume
    private var isTransitioning = false
    private var transitionProgress = 0.0

    init {
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
        trace.log("prototype init")
    }

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        this.scope = scope
        this.onStateChanged = onStateChanged
        this.onProgressChanged = onProgressChanged

        val active = activeSlot
        if (active?.matches(request) == true) {
            trace.log("play adopts active slot=${active.id}")
            if (!isTransitioning) {
                active.setVolume(volumePercent)
            }
            active.resume()
            onStateChanged(PlaybackState.Playing)
            startProgressPolling(scope, active, onProgressChanged)
            return
        }

        val fadingIn = fadingInSlot
        if (fadingIn?.matches(request) == true) {
            trace.log("play promotes fading slot=${fadingIn.id}")
            promoteFadingIn(scope, fadingIn, onStateChanged, onProgressChanged)
            return
        }

        val prepared = nextSlot
        if (prepared?.matches(request) == true) {
            trace.log("play promotes prepared slot=${prepared.id}")
            promotePrepared(scope, prepared, onStateChanged, onProgressChanged)
            return
        }

        trace.log("play starts fresh")
        stopSlots(clearCallbacks = false)
        val currentPlaybackId = nextPlaybackId()
        onStateChanged(PlaybackState.Loading)
        onProgressChanged(PlaybackProgress.Unknown)

        scope.launch(Dispatchers.IO) {
            try {
                val slot = startSlot(scope, request, startPaused = false, volume = volumePercent)
                if (!isCurrentPlayback(currentPlaybackId)) {
                    slot.stop()
                    return@launch
                }
                activeSlot = slot
                onStateChanged(PlaybackState.Playing)
                startProgressPolling(scope, slot, onProgressChanged)
            } catch (exception: Throwable) {
                trace.log("play failed: ${exception.message}")
                if (isCurrentPlayback(currentPlaybackId)) {
                    onStateChanged(PlaybackState.Error(exception.message ?: "mpv playback failed."))
                }
            }
        }
    }

    override fun pause() {
        trace.log("pause")
        resetTransition()
        activeSlot?.pause()
        nextSlot?.pause()
        onStateChanged?.invoke(PlaybackState.Paused)
    }

    override fun resume() {
        trace.log("resume")
        activeSlot?.resume()
        onStateChanged?.invoke(PlaybackState.Playing)
    }

    override fun seek(positionSeconds: Double) {
        trace.log("seek $positionSeconds")
        resetTransition()
        stopAsync(nextSlot)
        nextSlot = null
        activeSlot?.seek(positionSeconds)
    }

    override fun setVolume(percent: Int) {
        volumePercent = percent.coerceIn(0, MaxVolume)
        trace.log("setVolume $volumePercent")
        if (isTransitioning) {
            fadingOutSlot?.setVolume((equalPowerFadeOut(transitionProgress) * volumePercent).toInt())
            fadingInSlot?.setVolume((equalPowerFadeIn(transitionProgress) * volumePercent).toInt())
        } else {
            activeSlot?.setVolume(volumePercent)
            nextSlot?.setVolume(0)
        }
    }

    override fun stop() {
        trace.log("stop")
        playbackId += 1
        runCatching {
            stopSlots(clearCallbacks = true)
        }.onFailure { exception ->
            trace.log("stop failed: ${exception.message}")
            activeSlot = null
            nextSlot = null
            fadingOutSlot = null
            fadingInSlot = null
            isTransitioning = false
            transitionProgress = 0.0
            fadeJob = null
            progressJob = null
            scope = null
            onStateChanged = null
            onProgressChanged = null
        }
    }

    override fun setCrossfadeDuration(seconds: Int) {
        crossfadeDurationSeconds = seconds.coerceAtLeast(0)
        trace.log("setCrossfadeDuration $crossfadeDurationSeconds")
        if (crossfadeDurationSeconds == 0) {
            stopAsync(nextSlot)
            nextSlot = null
            resetTransition()
        }
    }

    override fun prepareNext(request: PlaybackRequest) {
        if (crossfadeDurationSeconds <= 0) return
        if (nextSlot?.matches(request) == true) return
        val currentScope = scope ?: return
        trace.log("prepareNext requested")
        stopAsync(nextSlot)
        nextSlot = null
        currentScope.launch(Dispatchers.IO) {
            try {
                val slot = startSlot(currentScope, request, startPaused = true, volume = 0)
                if (activeSlot?.matches(request) == true) {
                    slot.stop()
                    return@launch
                }
                nextSlot = slot
                trace.log("prepareNext ready slot=${slot.id}")
            } catch (exception: Throwable) {
                trace.log("prepareNext failed: ${exception.message}")
                nextSlot = null
            }
        }
    }

    private fun startProgressPolling(
        scope: CoroutineScope,
        slot: Slot,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ) {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.IO) {
            trace.log("progress polling slot=${slot.id}")
            while (activeSlot == slot && slot.process.isAlive) {
                val progress = PlaybackProgress(
                    positionSeconds = slot.queryDouble("time-pos"),
                    durationSeconds = slot.queryDouble("duration"),
                )
                onProgressChanged(progress)
                maybeStartCrossfade(scope, slot, progress)
                delay(250)
            }
        }
    }

    private fun maybeStartCrossfade(
        scope: CoroutineScope,
        slot: Slot,
        progress: PlaybackProgress,
    ) {
        if (isTransitioning || crossfadeDurationSeconds <= 0) return
        val next = nextSlot ?: return
        val position = progress.positionSeconds ?: return
        val duration = progress.durationSeconds ?: return
        if (duration - position > crossfadeDurationSeconds) return

        trace.log("fade start active=${slot.id} next=${next.id} position=$position duration=$duration")
        isTransitioning = true
        fadingOutSlot = slot
        fadingInSlot = next
        nextSlot = null
        next.setVolume(0)
        next.resume()
        fadeJob?.cancel()
        fadeJob = scope.launch(Dispatchers.IO) {
            runFade(scope, fadingOut = slot, fadingIn = next)
        }
    }

    private suspend fun runFade(
        scope: CoroutineScope,
        fadingOut: Slot,
        fadingIn: Slot,
    ) {
        val steps = (crossfadeDurationSeconds * FadeTicksPerSecond).coerceAtLeast(1)
        repeat(steps + 1) { step ->
            if (!isTransitioning || fadingOutSlot != fadingOut) return
            val progress = step.toDouble() / steps.toDouble()
            transitionProgress = progress
            val fadeIn = equalPowerFadeIn(progress)
            val fadeOut = equalPowerFadeOut(progress)
            if (fadingOut.isAlive()) {
                fadingOut.setVolume((fadeOut * volumePercent).toInt())
            }
            fadingIn.setVolume((fadeIn * volumePercent).toInt())
            delay(FadeTickMillis)
        }

        trace.log("fade complete active=${fadingOut.id} next=${fadingIn.id}")
        completeCrossfade(scope, fadingOut = fadingOut, fadingIn = fadingIn)
    }

    private fun completeCrossfade(
        scope: CoroutineScope,
        fadingOut: Slot,
        fadingIn: Slot,
    ) {
        if (!isTransitioning || fadingInSlot != fadingIn) return
        trace.log("fade handoff active=${fadingOut.id} next=${fadingIn.id}")
        fadeJob?.cancel()
        fadeJob = null
        fadingIn.setVolume(volumePercent)
        fadingIn.resume()
        activeSlot = fadingIn
        fadingOutSlot = null
        fadingInSlot = null
        isTransitioning = false
        transitionProgress = 0.0
        onProgressChanged?.let { startProgressPolling(scope, fadingIn, it) }
        onStateChanged?.invoke(PlaybackState.Finished)
        stopAsync(fadingOut)
    }

    private fun promotePrepared(
        scope: CoroutineScope,
        prepared: Slot,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ) {
        trace.log("promote prepared slot=${prepared.id}")
        val previous = activeSlot
        nextSlot = null
        activeSlot = prepared
        prepared.setVolume(volumePercent)
        prepared.resume()
        if (previous != null && previous != prepared) {
            stopAsync(previous)
        }
        resetTransition()
        onStateChanged(PlaybackState.Playing)
        startProgressPolling(scope, prepared, onProgressChanged)
    }

    private fun promoteFadingIn(
        scope: CoroutineScope,
        fadingIn: Slot,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
    ) {
        val fadingOut = fadingOutSlot
        fadeJob?.cancel()
        fadeJob = null
        fadingOutSlot = null
        fadingInSlot = null
        nextSlot = null
        activeSlot = fadingIn
        isTransitioning = false
        transitionProgress = 0.0
        fadingIn.setVolume(volumePercent)
        fadingIn.resume()
        stopAsync(fadingOut)
        onStateChanged(PlaybackState.Playing)
        startProgressPolling(scope, fadingIn, onProgressChanged)
    }

    private fun resetTransition() {
        if (!isTransitioning) return
        trace.log("transition reset")
        fadeJob?.cancel()
        fadeJob = null
        fadingOutSlot?.setVolume(volumePercent)
        stopAsync(fadingInSlot)
        fadingOutSlot = null
        fadingInSlot = null
        isTransitioning = false
        transitionProgress = 0.0
    }

    private fun handleSlotExit(slot: Slot, exitCode: Int) {
        trace.log("slot exit id=${slot.id} code=$exitCode")
        if (slot == fadingOutSlot) {
            trace.log("fading out slot ended id=${slot.id}")
            val currentScope = scope
            val fadingIn = fadingInSlot
            if (currentScope != null && fadingIn != null) {
                completeCrossfade(currentScope, fadingOut = slot, fadingIn = fadingIn)
            }
            slot.deleteEndpoint()
            return
        }
        if (slot == activeSlot) {
            activeSlot = null
            progressJob?.cancel()
            progressJob = null
            onProgressChanged?.invoke(PlaybackProgress.Unknown)
            if (exitCode == 0) {
                onStateChanged?.invoke(PlaybackState.Finished)
            } else {
                onStateChanged?.invoke(PlaybackState.Error("mpv exited with code $exitCode."))
            }
        }
        if (slot == nextSlot) {
            nextSlot = null
        }
        slot.deleteEndpoint()
    }

    private fun stopSlots(clearCallbacks: Boolean) {
        fadeJob?.cancel()
        progressJob?.cancel()
        listOfNotNull(activeSlot, nextSlot, fadingOutSlot, fadingInSlot).distinct().forEach { it.stop() }
        activeSlot = null
        nextSlot = null
        fadingOutSlot = null
        fadingInSlot = null
        isTransitioning = false
        transitionProgress = 0.0
        fadeJob = null
        progressJob = null
        if (clearCallbacks) {
            scope = null
            onStateChanged = null
            onProgressChanged = null
        }
    }

    private fun startSlot(
        scope: CoroutineScope,
        request: PlaybackRequest,
        startPaused: Boolean,
        volume: Int,
    ): Slot {
        val endpoint = createIpcEndpoint()
        val args = mutableListOf(
            executable,
            "--no-video",
            "--really-quiet",
            "--gapless-audio=yes",
            "--replaygain=${request.replayGainMode.mpvValueForPrototype()}",
            "--volume=$volume",
            "--input-ipc-server=${endpoint.mpvPath}",
        )
        if (startPaused) args += "--pause=yes"
        request.startPositionSeconds
            ?.takeIf { it > 0.0 }
            ?.let { args += "--start=$it" }
        args += request.url
        trace.log("slot start paused=$startPaused volume=$volume")
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
        val slot = Slot(
            id = nextSlotId(),
            request = request,
            process = process,
            endpoint = endpoint,
        )
        endpoint.waitUntilReady()
        slot.monitorJob = scope.launch(Dispatchers.IO) {
            val exitCode = process.waitFor()
            handleSlotExit(slot, exitCode)
        }
        return slot
    }

    private fun createIpcEndpoint(): MpvIpcEndpoint =
        when (platform) {
            MpvIpcPlatform.Windows -> {
                val pipeName = "naviamp-mpv-xfade-${System.nanoTime()}"
                MpvIpcEndpoint.WindowsNamedPipe("\\\\.\\pipe\\$pipeName")
            }
            MpvIpcPlatform.Unix -> {
                val socket = File(
                    System.getProperty("java.io.tmpdir"),
                    "naviamp-mpv-xfade-${System.nanoTime()}.sock",
                )
                socket.delete()
                MpvIpcEndpoint.UnixSocket(socket)
            }
        }

    private fun nextPlaybackId(): Int {
        playbackId += 1
        return playbackId
    }

    private fun isCurrentPlayback(id: Int): Boolean =
        playbackId == id

    private fun nextSlotId(): Int =
        ++slotId

    private inner class Slot(
        val id: Int,
        val request: PlaybackRequest,
        val process: Process,
        val endpoint: MpvIpcEndpoint,
    ) {
        var monitorJob: Job? = null

        fun pause() {
            send("""{"command":["set_property","pause",true]}""", reportErrors = false)
        }

        fun resume() {
            send("""{"command":["set_property","pause",false]}""", reportErrors = false)
        }

        fun seek(positionSeconds: Double) {
            send("""{"command":["seek",$positionSeconds,"absolute+exact"]}""")
        }

        fun setVolume(volume: Int) {
            send("""{"command":["set_property","volume",${volume.coerceIn(0, MaxVolume)}]}""", reportErrors = false)
        }

        fun isAlive(): Boolean =
            process.isAlive

        fun matches(other: PlaybackRequest): Boolean =
            request.mediaId?.let { mediaId ->
                mediaId == other.mediaId
            } ?: (request.url == other.url)

        fun queryDouble(property: String): Double? {
            val response = send("""{"command":["get_property","$property"]}""", reportErrors = false)
                ?: return null
            if (response.isBlank()) return null
            val data = runCatching {
                json.parseToJsonElement(response).jsonObject["data"]
            }.getOrNull() ?: return null
            return data.doubleValueForPrototype()
        }

        fun stop() {
            monitorJob?.cancel()
            if (process.isAlive) {
                send("""{"command":["quit"]}""", reportErrors = false)
                if (!process.waitFor(150, TimeUnit.MILLISECONDS)) process.destroy()
                if (!process.waitFor(150, TimeUnit.MILLISECONDS)) process.destroyForcibly()
            }
            deleteEndpoint()
        }

        fun deleteEndpoint() {
            endpoint.delete()
        }

        private fun send(command: String, reportErrors: Boolean = true): String? =
            try {
                if (!process.isAlive) return null
                endpoint.send("$command\n")
            } catch (exception: Throwable) {
                trace.log("slot=$id command failed: ${exception.message}")
                if (reportErrors) {
                    onStateChanged?.invoke(PlaybackState.Error(exception.message ?: "mpv command failed."))
                }
                null
            }
    }

    private companion object {
        private const val MaxVolume = 100
        private const val FadeTicksPerSecond = 5
        private const val FadeTickMillis = 1000L / FadeTicksPerSecond
        private var slotId = 0
    }

    private fun stopAsync(slot: Slot?) {
        val slotToStop = slot ?: return
        scope?.launch(Dispatchers.IO) {
            slotToStop.stop()
        } ?: slotToStop.stop()
    }
}

class PlaybackTrace(
    private val path: Path?,
) {
    fun log(message: String) {
        val tracePath = path ?: return
        if (!isEnabled()) return
        runCatching {
            Files.createDirectories(tracePath.parent)
            Files.writeString(
                tracePath,
                "${System.currentTimeMillis()} $message${System.lineSeparator()}",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }

    companion object {
        @Volatile
        private var defaultEnabled = System.getProperty("naviamp.playback.trace") == "true" ||
            System.getenv("NAVIAMP_PLAYBACK_TRACE") == "true"

        fun setDefaultEnabled(enabled: Boolean) {
            defaultEnabled = enabled
        }

        fun isEnabled(): Boolean =
            defaultEnabled

        fun default(): PlaybackTrace =
            PlaybackTrace(
                Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "naviamp",
                    "mpv-crossfade-prototype.log",
                ),
            )
    }
}

private fun equalPowerFadeIn(progress: Double): Double =
    sin((Math.PI / 2.0) * progress.coerceIn(0.0, 1.0))

private fun equalPowerFadeOut(progress: Double): Double =
    cos((Math.PI / 2.0) * progress.coerceIn(0.0, 1.0))

private fun JsonElement.doubleValueForPrototype(): Double? =
    runCatching { jsonPrimitive.doubleOrNull }.getOrNull()

private fun ReplayGainMode.mpvValueForPrototype(): String =
    when (this) {
        ReplayGainMode.Off -> "no"
        ReplayGainMode.Track -> "track"
        ReplayGainMode.Album -> "album"
    }
