package app.naviamp.desktop.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class MpvPlaybackEngine(
    private val executable: String,
) : PlaybackEngine {
    override val name: String = "mpv"

    private var job: Job? = null
    private var process: Process? = null

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
    ) {
        stop()
        onStateChanged(PlaybackState.Loading)

        job = scope.launch(Dispatchers.IO) {
            try {
                val currentProcess = ProcessBuilder(
                    executable,
                    "--no-video",
                    "--really-quiet",
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
                process = null
            }
        }
    }

    override fun stop() {
        job?.cancel()
        process?.stop()
        process = null
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
