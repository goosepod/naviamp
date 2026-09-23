package app.naviamp.presentation

import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.app.NaviampRemoteOutputTarget
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreOutputPlaybackEffectsTest {
    @Test
    fun castSelectionRoutesQueueAndCommandsWithoutStartingLocalAudio() {
        val outputs = NaviampPlaybackOutputSelectionController()
        val local = RecordingLocalEffects()
        val routed = NaviampCoreOutputPlaybackEffects(local, outputs)
        val cast = RecordingCastRoute()
        routed.cast = cast
        val selection = outputs.select(NaviampRemoteOutputTarget(NaviampRemoteOutputKind.Cast, "tv", "TV"))
        outputs.connected(selection)
        val queue = PlaybackQueue()

        routed.applyQueue(queue, true)
        routed.playQueueSelection(queue, 0)
        routed.applyNavigation(PlaybackQueueNavigationCommand.Next)
        routed.pause()
        routed.seek(15.0)
        assertTrue(routed.startOrRestore())

        assertEquals(0, local.queueSelections)
        assertEquals(0, local.starts)
        assertEquals(2, cast.loads)
        assertEquals(1, cast.pauses)
        assertEquals(15.0, cast.seekPosition)
        assertFalse(routed.capabilities.supportsVisualizer)

        outputs.selectLocal()
        routed.playQueueSelection(queue, 0)
        assertEquals(1, local.queueSelections)
    }

    @Test
    fun localEngineCallbacksCannotOverwriteReceiverAuthority() {
        val outputs = NaviampPlaybackOutputSelectionController()
        val local = RecordingLocalEffects()
        val routed = NaviampCoreOutputPlaybackEffects(local, outputs)
        val observed = mutableListOf<PlaybackState>()
        routed.attach(object : NaviampCorePlaybackObserver {
            override fun onStateChanged(state: PlaybackState) { observed += state }
            override fun onProgressChanged(progress: PlaybackProgress) = Unit
            override fun onMetadataChanged(metadata: app.naviamp.domain.playback.PlaybackStreamMetadata) = Unit
        })
        val selection = outputs.select(NaviampRemoteOutputTarget(NaviampRemoteOutputKind.Cast, "tv", "TV"))
        outputs.connected(selection)
        local.observer?.onStateChanged(PlaybackState.Playing)
        outputs.activatePlaybackAuthority(selection)
        local.observer?.onStateChanged(PlaybackState.Stopped)
        assertEquals(listOf<PlaybackState>(PlaybackState.Playing), observed)
    }

    private class RecordingCastRoute : NaviampCoreCastPlaybackRoute {
        override val suppressLocalObservations = false
        var loads = 0
        var pauses = 0
        var seekPosition: Double? = null
        override fun loadCurrent(positionSeconds: Double?): Boolean { loads++; return true }
        override fun navigate(command: PlaybackQueueNavigationCommand) = Unit
        override fun pause() { pauses++ }
        override fun resume() = Unit
        override fun seek(positionSeconds: Double) { seekPosition = positionSeconds }
        override fun stop() = Unit
        override fun setVolume(percent: Int) = Unit
    }

    private class RecordingLocalEffects : NaviampCorePlaybackEffectPort {
        override val capabilities = NaviampCorePlaybackCapabilities(supportsVisualizer = true)
        override val playbackSource = PlaybackSource.ProviderStream
        var queueSelections = 0
        var starts = 0
        var observer: NaviampCorePlaybackObserver? = null
        override fun attach(observer: NaviampCorePlaybackObserver) { this.observer = observer }
        override fun pause() = Unit
        override fun resume() = Unit
        override fun startOrRestore(): Boolean { starts++; return true }
        override fun seek(positionSeconds: Double) = Unit
        override fun replayCurrent(positionSeconds: Double) = Unit
        override fun setVolume(percent: Int) = Unit
        override fun stop() = Unit
        override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) = Unit
        override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
        override fun applyRepeatMode(mode: RepeatMode) = Unit
        override fun playQueueSelection(queue: PlaybackQueue, index: Int) { queueSelections++ }
    }
}
