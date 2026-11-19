package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.domain.*
import app.naviamp.domain.cache.PlaybackSessionRepository
import app.naviamp.domain.playback.*
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampVehicleLifecycleTest {
    private val labels = NaviampVehicleBrowseLabels("Home", "Playlists", "Radio", "Empty", "Loading", "Disconnected", "Failed", "Next", "Previous", "Queue", "Play", "Shuffle", "Retry")
    private fun TestScope.core(): Pair<NaviampCore, MutableList<String>> {
        val fake = FakeCoreMediaProvider(); val defaults = fakeCoreServices(fake); val actions = mutableListOf<String>()
        lateinit var observer: NaviampCorePlaybackObserver
        val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
            override fun attach(value: NaviampCorePlaybackObserver) { observer = value }
            override fun pause() { actions += "pause"; observer.onStateChanged(PlaybackState.Paused) }
            override fun resume() { actions += "resume"; observer.onStateChanged(PlaybackState.Playing) }
            override fun startOrRestore(): Boolean { actions += "start"; observer.onStateChanged(PlaybackState.Playing); return true }
            override fun stop() { actions += "stop"; observer.onStateChanged(PlaybackState.Idle) }
        }
        val tracks = listOf(fake.track, fake.track.copy(id = TrackId("second"), title = "Second"))
        val core = NaviampCore.create(backgroundScope, defaults.copy(playback = defaults.playback.copy(effects = effects)),
            initialState = NaviampCoreInitialState(playback = NaviampLivePlaybackState(currentTrack = tracks.first(),
                queue = PlaybackQueue(tracks, 0), playbackState = PlaybackState.Playing, progress = PlaybackProgress(17.0, 180.0))))
        core.stateStore.updateShell { it.copy(connectionSettings = it.connectionSettings.copy(currentSourceId = "source")) }
        return core to actions
    }
    private fun TestScope.scene(core: NaviampCore) = NaviampVehicleBrowseController(backgroundScope, core.state,
        core.externalPlaybackBridge(), { core.execute(it) }, labels, 100, collections = core.vehicleCollections)

    @Test fun phoneAndCarShareTransportAndInterruptionResumesOnce() = runTest {
        val (core, actions) = core(); runCurrent()
        val car = core.externalPlaybackBridge(); val phone = core.externalPlaybackBridge(); val lifecycle = car.lifecycleCoordinator()
        assertTrue(car.snapshot().canPlayPause)
        lifecycle.interruptionBegan(); runCurrent()
        assertEquals(NaviampExternalPlaybackState.Paused, phone.snapshot().state)
        lifecycle.interruptionEnded(true); runCurrent(); lifecycle.interruptionEnded(true); runCurrent()
        assertEquals(listOf("pause", "resume"), actions)
        assertEquals(NaviampExternalPlaybackState.Playing, phone.snapshot().state)
        core.close()
    }
    @Test fun explicitPhonePauseWhileInterruptedOverridesAutomaticResume() = runTest {
        val (core, actions) = core(); runCurrent()
        val car = core.externalPlaybackBridge(); val phone = core.externalPlaybackBridge(); val lifecycle = car.lifecycleCoordinator()
        lifecycle.interruptionBegan(); runCurrent()
        val before = core.state.value.playbackIntentGeneration
        phone.pause()
        assertTrue(core.state.value.playbackIntentGeneration > before, "Intent must be stamped before its queued command executes")
        lifecycle.interruptionEnded(true); runCurrent()
        assertEquals(listOf("pause", "pause"), actions)
        assertEquals(NaviampExternalPlaybackState.Paused, car.snapshot().state); core.close()
    }
    @Test fun queuedPhoneStopCannotBeOvertakenByAnInterruptionEnd() = runTest {
        val (core, actions) = core(); runCurrent()
        val car = core.externalPlaybackBridge(); val lifecycle = car.lifecycleCoordinator()
        lifecycle.interruptionBegan(); runCurrent()
        core.dispatch(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Stop)))
        lifecycle.interruptionEnded(true); runCurrent()
        assertEquals(listOf("pause", "stop"), actions); core.close()
    }
    @Test fun loadingIsCancelledThroughTheRealBridgeEvenWithPauseDisabled() = runTest {
        for (outputLoss in listOf(false, true)) {
            val (core, actions) = core(); runCurrent()
            core.updateLivePlayback { it.copy(playbackState = PlaybackState.Loading) }
            core.stateStore.updateShell { it.copy(nowPlaying = it.nowPlaying?.copy(
                canSeek = false, canChangeVolume = false, hasNext = false, hasPrevious = false)) }
            val bridge = core.externalPlaybackBridge()
            assertFalse(bridge.snapshot().canPlayPause)
            assertFalse(bridge.snapshot().canControl, "Loading cancellation must work even without any ordinary transport capability")
            val lifecycle = bridge.lifecycleCoordinator()
            if (outputLoss) lifecycle.outputDisconnected() else lifecycle.interruptionBegan()
            lifecycle.interruptionEnded(true); runCurrent()
            assertEquals(listOf("stop"), actions)
            core.close()
        }
    }
    @Test fun explicitOutputSelectionInvalidatesAutomaticInterruptionResume() = runTest {
        val (core, actions) = core(); runCurrent()
        val lifecycle = core.externalPlaybackBridge().lifecycleCoordinator()
        lifecycle.interruptionBegan(); runCurrent()
        core.selectLocalPlayback(); lifecycle.interruptionEnded(true); runCurrent()
        assertEquals(listOf("pause"), actions); core.close()
    }

    @Test fun unrelatedMetadataAndVolumeDoNotCancelAnOtherwiseValidResume() = runTest {
        val (core, actions) = core(); runCurrent()
        val lifecycle = core.externalPlaybackBridge().lifecycleCoordinator()
        lifecycle.interruptionBegan(); runCurrent()
        core.execute(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.ChangeVolume, volumePercent = 40)))
        core.stateStore.updateShell { it.copy(nowPlaying = it.nowPlaying?.copy(title = "Updated title")) }
        lifecycle.interruptionEnded(true); runCurrent()
        assertEquals(listOf("pause", "resume"), actions); core.close()
    }
    @Test fun reconnectingScenesObservePhoneQueueAndProgressWithoutRestartingPlayback() = runTest {
        val (core, actions) = core(); runCurrent()
        val first = scene(core); first.openQueue(); runCurrent()
        val old = first.state.value; val oldPage = old.pages.single()
        first.close(); runCurrent()
        core.updateLivePlayback { live -> live.copy(queue = live.queue.copy(currentIndex = 1), currentTrack = live.queue.tracks[1], progress = PlaybackProgress(52.0, 180.0)) }
        val fresh = scene(core); fresh.openQueue(); runCurrent()
        assertEquals("Second", fresh.state.value.pages.single().rows.first().title)
        assertEquals(52_000L, core.externalPlaybackBridge().snapshot().positionMillis)
        val callbacks = mutableListOf<Boolean>()
        first.selectPage(oldPage.id, oldPage.rows.first().id, old.revision, callbacks::add)
        assertEquals(listOf(false), callbacks); assertTrue(actions.isEmpty())
        repeat(10) { val reattached = scene(core); reattached.close() }
        assertTrue(actions.isEmpty()); fresh.close(); core.close()
    }
    @Test fun carFirstAndPhoneFirstBootRestoreOnceWithoutAComposeSurface() = runTest {
        for (carFirst in listOf(false, true)) {
            val fake = FakeCoreMediaProvider(); val defaults = fakeCoreServices(fake)
            var connects = 0; var restores = 0; var starts = 0; var disposals = 0
            val inventory = NaviampCoreConnectionInventory(listOf(NaviampCoreSavedConnectionRecord("saved", "Saved", "https://example.test", "user")), "saved")
            val port = object : NaviampCoreProviderSessionPort by defaults.connection {
                override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
                    connects++; assertEquals(NaviampCoreConnectionRequest.Saved("saved"), request)
                    return NaviampCoreConnectedSession("saved", "Saved", inventory = inventory)
                }
            }
            val effects = object : NaviampCorePlaybackEffectPort by defaults.playback.effects {
                override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) { restores++ }
                override fun startOrRestore(): Boolean { starts++; return true }
            }
            val saved = assertNotNull(PlaybackSessionSettings.fromTracks(listOf(fake.track), 0, positionSeconds = 34.0))
            val sessions = NaviampPlaybackSessionController(object : PlaybackSessionRepository {
                override fun loadPlaybackSession(sourceId: String?) = saved
                override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) = Unit
            })
            val runtime = NaviampApplicationRuntimeOwner(create = { NaviampCore.create(backgroundScope,
                defaults.copy(connection = port, playback = defaults.playback.copy(effects = effects, sessions = sessions)),
                initialState = NaviampCoreInitialState(connectionInventory = inventory)) }, dispose = { disposals++; it.close() })
            val first = runtime.getOrCreate()
            val car = if (carFirst) scene(first) else null
            first.startRuntimeServices(); runCurrent()
            assertEquals(1, connects); assertEquals(1, restores); assertEquals(0, starts)
            val second = runtime.getOrCreate(); assertSame(first, second)
            second.startRuntimeServices()
            val attached = car ?: scene(second)
            attached.openQueue(); runCurrent(); assertEquals(fake.track.title, attached.state.value.pages.single().rows.first().title)
            attached.close(); repeat(5) { val scene = scene(runtime.getOrCreate()); scene.close() }; runCurrent()
            assertEquals(1, connects); assertEquals(1, restores); assertEquals(0, starts)
            runtime.close(); runtime.close(); assertEquals(1, disposals)
        }
    }
    @Test fun shutdownCancelsBootstrapAndCommandsWithoutClosingTheCallersScope() = runTest {
        val defaults = fakeCoreServices(FakeCoreMediaProvider()); val gate = CompletableDeferred<Unit>(); var connected = false
        val port = object : NaviampCoreProviderSessionPort by defaults.connection {
            override suspend fun connect(request: NaviampCoreConnectionRequest, plan: NaviampConnectionAttemptPlan): NaviampCoreConnectedSession {
                withContext(NonCancellable) { gate.await() }; connected = true
                return NaviampCoreConnectedSession("saved", "Saved", inventory = NaviampCoreConnectionInventory(currentSourceId = "saved"))
            }
        }
        val core = NaviampCore.create(backgroundScope, defaults.copy(connection = port), initialState = NaviampCoreInitialState(
            connectionInventory = NaviampCoreConnectionInventory(listOf(NaviampCoreSavedConnectionRecord("saved", "Saved", "https://example.test", "user")), "saved")))
        runCurrent(); val before = core.state.value.sourceGeneration
        core.close(); gate.complete(Unit); runCurrent()
        assertTrue(connected, "The fake deliberately returns after cancellation")
        assertEquals(before, core.state.value.sourceGeneration)
        assertFalse(core.state.value.shell.connectionSettings.connection.connected)
        val intent = core.state.value.playbackIntentGeneration
        core.dispatch(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.PlayCurrent)))
        assertEquals(intent, core.state.value.playbackIntentGeneration)
        assertFailsWith<CancellationException> { core.execute(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.PlayCurrent))) }
        assertTrue(backgroundScope.isActive)
    }
}
