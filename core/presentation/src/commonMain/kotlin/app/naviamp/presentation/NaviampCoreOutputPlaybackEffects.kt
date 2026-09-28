package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackOutputSelection
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.StreamQuality
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackStreamMetadata
import app.naviamp.domain.playback.PlaybackVisualizerFrame
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode

/** Cast actions are scheduled by the shared product controller; this keeps local audio silent. */
internal interface NaviampCoreCastPlaybackRoute {
    val suppressLocalObservations: Boolean
    fun loadCurrent(positionSeconds: Double? = null): Boolean
    fun navigate(command: PlaybackQueueNavigationCommand)
    fun pause()
    fun resume()
    fun seek(positionSeconds: Double)
    fun stop()
    fun setVolume(percent: Int)
}

/** Shared output router used by every Core playback path, including catalog queue replacement. */
internal class NaviampCoreOutputPlaybackEffects(
    private val local: NaviampCorePlaybackEffectPort,
    private val outputs: NaviampPlaybackOutputSelectionController,
) : NaviampCorePlaybackEffectPort {
    var cast: NaviampCoreCastPlaybackRoute? = null

    private val castSelected: Boolean get() =
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.target?.kind == NaviampRemoteOutputKind.Cast

    override val capabilities: NaviampCorePlaybackCapabilities get() =
        if (castSelected) local.capabilities.copy(
            supportsVisualizer = false,
            supportsSoftwareVolume = true,
        ) else local.capabilities
    override val playbackSource: PlaybackSource get() = local.playbackSource
    override val playbackQuality: StreamQuality? get() = local.playbackQuality

    override fun attach(observer: NaviampCorePlaybackObserver) = local.attach(object : NaviampCorePlaybackObserver {
        private val remoteActive: Boolean get() = castSelected &&
            (outputs.hasRemotePlaybackAuthority() || cast?.suppressLocalObservations == true)
        override fun onStateChanged(state: PlaybackState) {
            if (!remoteActive) observer.onStateChanged(state)
        }
        override fun onProgressChanged(progress: PlaybackProgress) {
            if (!remoteActive) observer.onProgressChanged(progress)
        }
        override fun onMetadataChanged(metadata: PlaybackStreamMetadata) {
            if (!remoteActive) observer.onMetadataChanged(metadata)
        }
        override fun onSourceChanged(source: PlaybackSource, quality: StreamQuality?) {
            if (!remoteActive) observer.onSourceChanged(source, quality)
        }
        override fun onVisualizerFrameChanged(frame: PlaybackVisualizerFrame?) {
            if (!remoteActive) observer.onVisualizerFrameChanged(frame)
        }
    })

    override fun setVisualizerFramesEnabled(enabled: Boolean) =
        local.setVisualizerFramesEnabled(enabled && !castSelected)

    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        if (!castSelected) local.applyQueue(queue, clearPreparedNext)
    }

    override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
        if (!castSelected) local.restoreQueue(queue, startPositionSeconds)
    }

    override fun restoreInternetRadio(station: InternetRadioStation) {
        if (!castSelected) local.restoreInternetRadio(station)
    }

    override fun applyNavigation(command: PlaybackQueueNavigationCommand) {
        if (castSelected) cast?.navigate(command) else local.applyNavigation(command)
    }

    override fun applyAutomaticNavigation(command: PlaybackQueueNavigationCommand) {
        if (castSelected) cast?.navigate(command) else local.applyAutomaticNavigation(command)
    }

    override fun applyRepeatMode(mode: RepeatMode) {
        if (!castSelected) local.applyRepeatMode(mode)
    }

    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        if (castSelected) cast?.loadCurrent() else local.playQueueSelection(queue, index)
    }

    override fun pause() {
        if (castSelected) cast?.pause() else local.pause()
    }

    override fun resume() {
        if (castSelected) cast?.resume() else local.resume()
    }

    override fun startOrRestore(): Boolean =
        if (castSelected) cast?.loadCurrent() == true else local.startOrRestore()

    override fun seek(positionSeconds: Double) {
        if (castSelected) cast?.seek(positionSeconds) else local.seek(positionSeconds)
    }

    override fun replayCurrent(positionSeconds: Double) {
        if (castSelected) cast?.loadCurrent(positionSeconds) else local.replayCurrent(positionSeconds)
    }

    override fun setVolume(percent: Int) {
        if (castSelected) cast?.setVolume(percent) else local.setVolume(percent)
    }

    override fun stop() {
        if (castSelected) cast?.stop() else local.stop()
    }

    override fun diagnostics(): List<Pair<String, String>> = local.diagnostics()
}
