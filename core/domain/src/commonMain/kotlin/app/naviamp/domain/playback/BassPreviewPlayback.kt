package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassAudioBackend
import app.naviamp.domain.bass.BassStreamHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns one extra BASS channel, never the device, main mixer, queue or prepared streams. */
internal class BassPreviewPlayback(
    private val runtime: BassPlaybackEngineRuntime,
    private val prepareBackend: (Boolean) -> BassAudioBackend,
) {
    private var generation = 0L
    private var job: Job? = null
    private var backend: BassAudioBackend? = null
    private var stream: BassStreamHandle? = null
    private var observer: ((PlaybackState) -> Unit)? = null
    private var volume = 1f
    val active: Boolean get() = observer != null

    fun play(scope: CoroutineScope, request: PlaybackRequest, verify: Boolean, volumePercent: Int,
        onStateChanged: (PlaybackState) -> Unit) {
        stop()
        val epoch = generation
        observer = onStateChanged
        volume = volumePercent.coerceIn(0, 100) / 100f
        onStateChanged(PlaybackState.Loading)
        job = scope.launch {
            var created: BassStreamHandle? = null
            var bass: BassAudioBackend? = null
            try {
                withContext(runtime.workContext) {
                    bass = prepareBackend(verify)
                    created = request.randomAccess?.let { bass!!.createRandomAccessStream(it, decode = false) }
                        ?.getOrThrow() ?: bass!!.createUrlStream(request.url).getOrThrow()
                    bass!!.setVolume(checkNotNull(created), volume).getOrThrow()
                }
                if (epoch != generation) return@launch
                val channel = checkNotNull(created)
                val output = checkNotNull(bass)
                backend = output
                stream = channel
                created = null
                output.setEndSync(channel) {
                    scope.launch {
                        if (epoch == generation && stream == channel) {
                            val failure = request.randomAccess?.failure
                            finish(if (failure == null) PlaybackState.Finished else
                                PlaybackState.Error(failure.message.orEmpty()))
                        }
                    }
                }.getOrThrow()
                output.play(channel).getOrThrow()
                onStateChanged(PlaybackState.Playing)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (epoch == generation) finish(PlaybackState.Error(error.message.orEmpty()))
            } finally {
                // A canceled, blocking native open can still return a channel: release that exact handle.
                created?.let { bass?.freeStream(it) }
            }
        }
    }

    fun pause() {
        val channel = stream ?: return
        backend?.pause(channel)?.fold(
            onSuccess = { observer?.invoke(PlaybackState.Paused) },
            onFailure = { finish(PlaybackState.Error(it.message.orEmpty())) })
    }

    fun resume() {
        val channel = stream ?: return
        backend?.play(channel)?.fold(
            onSuccess = { observer?.invoke(PlaybackState.Playing) },
            onFailure = { finish(PlaybackState.Error(it.message.orEmpty())) })
    }

    fun setVolume(factor: Float) {
        volume = factor.coerceIn(0f, 1f)
        stream?.let { backend?.setVolume(it, volume) }
    }

    fun stop() {
        generation++
        job?.cancel()
        job = null
        finish(PlaybackState.Stopped)
    }

    private fun finish(state: PlaybackState) {
        val notify = observer
        observer = null
        val channel = stream
        stream = null
        if (channel != null) {
            backend?.stop(channel)
            backend?.freeStream(channel)
        }
        backend = null
        notify?.invoke(state)
    }
}
