package app.naviamp.domain.playback

import app.naviamp.domain.bass.BassAudioBackend
import kotlinx.coroutines.CoroutineScope

/** Complete shared focus/lifetime behavior around the shared native audio engine. */
open class FocusedBassPlaybackEngine(
    bass: BassAudioBackend,
    runtime: BassPlaybackEngineRuntime,
    focus: PlaybackFocusEffect,
    wakeLock: PlaybackWakeLockEffect,
) : CoreBassPlaybackEngine(Result.success(bass), runtime) {
    private val focusController = PlaybackFocusController(
        focus, wakeLock,
        pause = { super.pause() }, resume = { super.resume() },
        outputVolumeFactor = ::setTransientOutputVolumeFactor,
    )

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        if (!focusController.requestPlayback()) {
            onStateChanged(PlaybackState.Error("Audio focus is currently held by another app."))
            return
        }
        super.play(scope, request, { state ->
            focusController.onPlaybackState(state)
            onStateChanged(state)
        }, { progress ->
            focusController.onProgress()
            onProgressChanged(progress)
        }, onMetadataChanged)
    }

    override fun pause() {
        focusController.userPausedOrStopped()
        super.pause()
    }

    override fun resume() {
        if (focusController.requestPlayback()) super.resume()
    }

    override fun stop() {
        focusController.userPausedOrStopped()
        super.stop()
    }

    override fun release() {
        focusController.userPausedOrStopped()
        super.release()
    }
}
