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
        pause = { if (previewActive) super.pausePreview() else super.pause() },
        resume = { if (previewActive) super.resumePreview() else super.resume() },
        outputVolumeFactor = ::setTransientOutputVolumeFactor,
    )

    override fun play(
        scope: CoroutineScope,
        request: PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (PlaybackStreamMetadata) -> Unit,
    ) {
        stopPreview()
        if (!focusController.requestPlayback()) {
            onStateChanged(PlaybackState.Error("Audio focus is currently held by another app."))
            return
        }
        super.play(scope, request, { state ->
            if (!previewActive) focusController.onPlaybackState(state)
            onStateChanged(state)
        }, { progress ->
            if (!previewActive) focusController.onProgress()
            onProgressChanged(progress)
        }, onMetadataChanged)
    }

    override fun pause() {
        focusController.userPausedOrStopped()
        super.pause()
    }

    override fun resume() {
        stopPreview()
        if (focusController.requestPlayback()) super.resume()
    }

    override fun playPreview(scope: CoroutineScope, request: PlaybackRequest, verifyNetworkCertificates: Boolean,
        volumePercent: Int, onStateChanged: (PlaybackState) -> Unit) {
        stopPreview()
        if (!focusController.requestPlayback()) {
            onStateChanged(PlaybackState.Error("Audio focus is currently held by another app."))
            return
        }
        super.playPreview(scope, request, verifyNetworkCertificates, volumePercent, { state ->
            focusController.onPlaybackState(state)
            onStateChanged(state)
        })
    }

    override fun pausePreview() {
        if (previewActive) focusController.userPausedOrStopped()
        super.pausePreview()
    }

    override fun resumePreview() {
        if (focusController.requestPlayback()) super.resumePreview()
    }

    override fun stopPreview() {
        if (previewActive) focusController.userPausedOrStopped()
        super.stopPreview()
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
