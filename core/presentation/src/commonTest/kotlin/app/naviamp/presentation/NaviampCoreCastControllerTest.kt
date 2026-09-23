package app.naviamp.presentation

import app.naviamp.app.NaviampCastHttpRequest
import app.naviamp.app.NaviampCastHttpResponse
import app.naviamp.app.NaviampCastHttpServerEffect
import app.naviamp.app.NaviampCastMediaEndpointController
import app.naviamp.app.NaviampCastMediaLeaseController
import app.naviamp.app.NaviampCastReceiverCommand
import app.naviamp.app.NaviampCastReceiverMedia
import app.naviamp.app.NaviampCastReceiverPlayerState
import app.naviamp.app.NaviampCastReceiverStatus
import app.naviamp.app.NaviampCastSecureTokenSource
import app.naviamp.app.NaviampCastSessionController
import app.naviamp.app.NaviampCastSessionEffect
import app.naviamp.app.NaviampCastSessionListener
import app.naviamp.app.NaviampCastTarget
import app.naviamp.app.NaviampLivePlaybackController
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.app.NaviampPlaybackOutputSelectionController
import app.naviamp.app.NaviampPlaybackQueueCoordinator
import app.naviamp.app.NaviampRemoteOutputKind
import app.naviamp.app.NaviampRemoteOutputTarget
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreCastControllerTest {
    @Test
    fun receiverFinishLoadsNextSharedQueueTrackAndKeepsPlaying() = runTest {
        val provider = FakeCoreMediaProvider(supportsStreamingTranscode = true)
        val nextTrack = provider.track.copy(id = TrackId("next"), title = "Next")
        val queueValue = PlaybackQueue(listOf(provider.track, nextTrack), 0)
        val live = NaviampLivePlaybackController(NaviampLivePlaybackState(
            currentTrack = provider.track,
            queue = queueValue,
            progress = PlaybackProgress(0.0, 180.0),
            playbackState = PlaybackState.Playing,
        ))
        val queue = NaviampPlaybackQueueCoordinator(live).also { it.restoreQueue(queueValue) }
        val outputs = NaviampPlaybackOutputSelectionController()
        val native = FakeSessionEffect()
        val sessions = NaviampCastSessionController(native, outputs)
        val castScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var token = 0
        val cast = NaviampCoreCastController(
            scope = castScope,
            sessions = sessions,
            outputs = outputs,
            endpoint = NaviampCastMediaEndpointController(
                server = object : NaviampCastHttpServerEffect {
                    override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit) =
                        "http://192.0.2.1:1234"
                    override suspend fun stop() = Unit
                },
                leases = NaviampCastMediaLeaseController(
                    tokens = NaviampCastSecureTokenSource { "casttoken${(++token).toString().padStart(32, '0')}" },
                    nowEpochMillis = { 0L },
                ),
                source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider }),
            ),
            providers = NaviampCoreMediaProviderSource { provider },
            playback = live,
            queue = queue,
            local = FakeLocalEffects(),
            publishNowPlaying = {},
        )
        cast.start()
        val selection = sessions.onTargetSelected(NaviampCastTarget("tv", "TV"))
        sessions.onConnected(selection, "TV")
        advanceUntilIdle()
        val firstUrl = native.loaded.single().mediaUrl
        assertEquals(1, native.commands.count { it == NaviampCastReceiverCommand.Play })

        sessions.onMediaStatus(selection, NaviampCastReceiverStatus(
            NaviampCastReceiverPlayerState.Idle, 180_000, 180_000, 75,
            finished = true, mediaUrl = firstUrl,
        ))
        advanceUntilIdle()

        assertEquals(nextTrack.id, live.state.value.queue.current?.id)
        assertEquals(2, native.loaded.size)
        assertEquals(2, native.commands.count { it == NaviampCastReceiverCommand.Play })
        castScope.cancel()
    }

    @Test
    fun acceptedLoadStopsLocalAndReceiverProgressReturnsToLocal() = runTest {
        val provider = FakeCoreMediaProvider(supportsStreamingTranscode = true)
        val queueValue = PlaybackQueue(listOf(provider.track), 0)
        val live = NaviampLivePlaybackController(NaviampLivePlaybackState(
            currentTrack = provider.track,
            queue = queueValue,
            progress = PlaybackProgress(12.0, 180.0),
            playbackState = PlaybackState.Playing,
        ))
        val queue = NaviampPlaybackQueueCoordinator(live).also { it.restoreQueue(queueValue) }
        val outputs = NaviampPlaybackOutputSelectionController()
        val native = FakeSessionEffect()
        val sessions = NaviampCastSessionController(native, outputs)
        val local = FakeLocalEffects()
        val castScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        var token = 0
        val endpoint = NaviampCastMediaEndpointController(
            server = object : NaviampCastHttpServerEffect {
                override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit) =
                    "http://192.0.2.1:1234"
                override suspend fun stop() = Unit
            },
            leases = NaviampCastMediaLeaseController(
                tokens = NaviampCastSecureTokenSource { "casttoken${(++token).toString().padStart(32, '0')}" },
                nowEpochMillis = { 0L },
            ),
            source = NaviampCoreCastMediaByteSource(NaviampCoreMediaProviderSource { provider }),
        )
        val cast = NaviampCoreCastController(
            scope = castScope,
            sessions = sessions,
            outputs = outputs,
            endpoint = endpoint,
            providers = NaviampCoreMediaProviderSource { provider },
            playback = live,
            queue = queue,
            local = local,
            publishNowPlaying = {},
        )
        cast.start()
        native.loadResult = false
        val selection = sessions.onTargetSelected(NaviampCastTarget("tv", "TV"))
        sessions.onConnected(selection, "TV")
        advanceUntilIdle()
        assertEquals(0, local.stops)
        assertFalse(outputs.hasRemotePlaybackAuthority())

        native.loadResult = true
        assertTrue(cast.loadCurrent())
        advanceUntilIdle()
        assertTrue(outputs.hasRemotePlaybackAuthority())
        assertEquals(1, local.stops)
        assertEquals(12_000, native.loaded.singleOrNull()?.positionMillis ?: native.loaded.last().positionMillis)
        val url = native.loaded.last().mediaUrl
        sessions.onMediaStatus(selection, NaviampCastReceiverStatus(
            NaviampCastReceiverPlayerState.Playing, 30_000, 180_000, 75, mediaUrl = url,
        ))
        advanceUntilIdle()
        assertEquals(30.0, live.state.value.progress.positionSeconds)
        assertEquals(PlaybackState.Playing, live.state.value.playbackState)

        sessions.onDisconnected(selection)
        advanceUntilIdle()
        assertFalse(outputs.hasRemotePlaybackAuthority())
        assertEquals(PlaybackState.Paused, live.state.value.playbackState)
        sessions.onConnected(selection, "TV")
        advanceUntilIdle()
        assertTrue(outputs.hasRemotePlaybackAuthority())
        assertEquals(30_000, native.loaded.last().positionMillis)

        cast.selectLocal()
        advanceUntilIdle()
        assertEquals(30.0, local.restoredAt)
        assertEquals(1, local.starts)
        assertTrue(native.disconnects > 0)

        val secondSelection = sessions.onTargetSelected(NaviampCastTarget("tv", "TV"))
        sessions.onConnected(secondSelection, "TV")
        advanceUntilIdle()
        outputs.select(NaviampRemoteOutputTarget(NaviampRemoteOutputKind.Connect, "speaker", "Speaker"))
        advanceUntilIdle()
        assertEquals(1, local.starts)
        assertTrue(native.disconnects > 1)
        castScope.cancel()
    }

    private class FakeSessionEffect : NaviampCastSessionEffect {
        var loadResult = true
        var disconnects = 0
        val loaded = mutableListOf<NaviampCastReceiverMedia>()
        val commands = mutableListOf<NaviampCastReceiverCommand>()
        override fun start(listener: NaviampCastSessionListener) = Unit
        override fun stop() = Unit
        override fun disconnect() { disconnects++ }
        override suspend fun load(selectionId: Long, media: NaviampCastReceiverMedia): Boolean {
            loaded += media
            return loadResult
        }
        override suspend fun command(selectionId: Long, command: NaviampCastReceiverCommand): Boolean {
            commands += command
            return true
        }
    }

    private class FakeLocalEffects : NaviampCorePlaybackEffectPort {
        override val capabilities = NaviampCorePlaybackCapabilities()
        override val playbackSource = PlaybackSource.ProviderStream
        var stops = 0
        var starts = 0
        var restoredAt: Double? = null
        override fun pause() = Unit
        override fun resume() = Unit
        override fun startOrRestore(): Boolean { starts++; return true }
        override fun seek(positionSeconds: Double) = Unit
        override fun replayCurrent(positionSeconds: Double) = Unit
        override fun setVolume(percent: Int) = Unit
        override fun stop() { stops++ }
        override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) = Unit
        override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) {
            restoredAt = startPositionSeconds
        }
        override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
        override fun applyRepeatMode(mode: RepeatMode) = Unit
        override fun playQueueSelection(queue: PlaybackQueue, index: Int) = Unit
    }
}
