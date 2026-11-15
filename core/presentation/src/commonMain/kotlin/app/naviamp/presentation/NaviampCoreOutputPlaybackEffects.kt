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

/** Protocol-independent actions scheduled by shared Core; hosts never choose the output. */
internal interface NaviampCoreRemotePlaybackRoute {
    val outputKind: NaviampRemoteOutputKind
    val supportsVolume: Boolean get() = false
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
    private val routes = mutableMapOf<NaviampRemoteOutputKind, NaviampCoreRemotePlaybackRoute>()

    fun register(route: NaviampCoreRemotePlaybackRoute) {
        // Connect delegates through its existing playback destination, rather than this route.
        require(route.outputKind != NaviampRemoteOutputKind.Connect)
        routes[route.outputKind] = route
    }

    var beforeStart: () -> Unit = {}

    private val selectedKind: NaviampRemoteOutputKind? get() =
        (outputs.state.value as? NaviampPlaybackOutputSelection.Remote)?.target?.kind
    private val remoteSelected: Boolean get() =
        selectedKind == NaviampRemoteOutputKind.Cast || selectedKind == NaviampRemoteOutputKind.Upnp
    private val route: NaviampCoreRemotePlaybackRoute? get() = routes[selectedKind]

    override val capabilities: NaviampCorePlaybackCapabilities get() =
        if (remoteSelected) local.capabilities.copy(
            supportsVisualizer = false,
            supportsSoftwareVolume = route?.supportsVolume == true,
        ) else local.capabilities
    override val playbackSource: PlaybackSource get() = local.playbackSource
    override val playbackQuality: StreamQuality? get() = local.playbackQuality

    override fun attach(observer: NaviampCorePlaybackObserver) = local.attach(object : NaviampCorePlaybackObserver {
        private val remoteActive: Boolean get() = remoteSelected &&
            (outputs.hasRemotePlaybackAuthority() || route?.suppressLocalObservations == true)
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
        local.setVisualizerFramesEnabled(enabled && !remoteSelected)

    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) {
        if (!remoteSelected) local.applyQueue(queue, clearPreparedNext)
    }

    override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
        if (!remoteSelected) local.restoreQueue(queue, startPositionSeconds)
    }

    override fun restoreInternetRadio(station: InternetRadioStation) {
        if (!remoteSelected) local.restoreInternetRadio(station)
    }

    override fun applyNavigation(command: PlaybackQueueNavigationCommand) {
        beforeStart()
        if (remoteSelected) route?.navigate(command) else local.applyNavigation(command)
    }

    override fun applyAutomaticNavigation(command: PlaybackQueueNavigationCommand) {
        beforeStart()
        if (remoteSelected) route?.navigate(command) else local.applyAutomaticNavigation(command)
    }

    override fun applyRepeatMode(mode: RepeatMode) {
        if (!remoteSelected) local.applyRepeatMode(mode)
    }

    override fun playQueueSelection(queue: PlaybackQueue, index: Int) {
        beforeStart()
        if (remoteSelected) route?.loadCurrent() else local.playQueueSelection(queue, index)
    }

    override fun prepareForPreview() = local.prepareForPreview()

    override fun pause() {
        if (remoteSelected) route?.pause() else local.pause()
    }

    override fun resume() {
        beforeStart()
        if (remoteSelected) route?.resume() else local.resume()
    }

    override fun startOrRestore(): Boolean {
        beforeStart()
        return if (remoteSelected) route?.loadCurrent() == true else local.startOrRestore()
    }

    override fun seek(positionSeconds: Double) {
        if (remoteSelected) route?.seek(positionSeconds) else local.seek(positionSeconds)
    }

    override fun replayCurrent(positionSeconds: Double) {
        beforeStart()
        if (remoteSelected) route?.loadCurrent(positionSeconds) else local.replayCurrent(positionSeconds)
    }

    override fun setVolume(percent: Int) {
        if (remoteSelected) {
            route?.takeIf { it.supportsVolume }?.setVolume(percent)
        } else local.setVolume(percent)
    }

    override fun stop() {
        beforeStart()
        if (remoteSelected) route?.stop() else local.stop()
    }

    override fun diagnostics(): List<Pair<String, String>> = local.diagnostics()
}
