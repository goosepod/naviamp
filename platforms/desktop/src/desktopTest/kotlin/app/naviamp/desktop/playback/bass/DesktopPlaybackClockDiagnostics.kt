package app.naviamp.desktop.playback.bass

import app.naviamp.domain.bass.BassStreamHandle
import com.sun.jna.NativeLibrary
import java.io.File

/** Test-only observations. Never initializes, starts, updates, drains, or selects a native device. */
internal class DesktopPlaybackClockDiagnostics {
    private val started = System.nanoTime()
    private val observations = ArrayDeque<String>()

    @Synchronized
    fun record(message: String) {
        if (observations.size == 64) observations.removeFirst()
        observations.addLast("${(System.nanoTime() - started) / 1_000_000}ms [${Thread.currentThread().name}] $message")
    }

    fun report(binding: DesktopBassJniBinding, playback: BassStreamHandle?, source: BassStreamHandle?): String = buildString {
        appendLine("Native clock diagnostics; elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        appendLine("os=${System.getProperty("os.name")}; arch=${System.getProperty("os.arch")}; playback=$playback; source=$source")
        appendLine("Recent existing backend calls and callbacks (no independent polling):")
        synchronized(this@DesktopPlaybackClockDiagnostics) { observations.forEach { appendLine(it) } }
        appendLine(runCatching { nativeSnapshot(binding, playback, source) }.getOrElse { "Native snapshot unavailable: $it" })
        appendLine("Thread snapshots after result (at most 24 threads, 12 frames each):")
        Thread.getAllStackTraces().entries.sortedBy { it.key.name }.take(24).forEach { (thread, stack) ->
            appendLine("${thread.name}: ${thread.state}")
            stack.take(12).forEach { appendLine("  $it") }
        }
    }

    private fun nativeSnapshot(binding: DesktopBassJniBinding, playback: BassStreamHandle?, source: BassStreamHandle?): String = buildString {
        appendLine("bass=${binding.version}; mixer=${binding.mixerVersion}; devices=${binding.outputDevices().toList()}")
        // Read the same already-loaded library, only after failure or explicit positive capture.
        // Each getter's error is read immediately, on this thread. No BASS_SetDevice: current
        // thread device/output state must not be mistaken for the channel's device/output state.
        val library = NativeLibrary.getInstance(File(binding.libraryDirectory, System.mapLibraryName("bass")).absolutePath)
        fun query(name: String, vararg arguments: Any?) {
            val result = runCatching {
                val value = library.getFunction(name).invokeInt(arguments)
                val error = library.getFunction("BASS_ErrorGetCode").invokeInt(emptyArray<Any>())
                "$value (error=$error)"
            }.fold({ it }, { "unavailable: $it" })
            appendLine("$name${arguments.toList()}=$result")
        }
        query("BASS_GetDevice")
        query("BASS_IsStarted")
        playback?.let {
            query("BASS_ChannelGetDevice", it.value)
            // BASS_DATA_AVAILABLE=0 with NULL returns buffered bytes without consuming audio.
            query("BASS_ChannelGetData", it.value, com.sun.jna.Pointer.NULL, 0)
            query("BASS_ChannelIsActive", it.value)
        }
        appendLine("Fresh JNI source=${source?.let { binding.positionSeconds(it.value) }}; audible=${playback?.let { binding.audiblePositionSeconds(it.value, source?.value ?: it.value) }}")
    }
}
