package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTarget
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackQueueNavigationCommand
import app.naviamp.domain.playback.PlaybackSource
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.playback.PlaybackTransitionMode
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.PlaybackQueueGroup
import app.naviamp.domain.queue.RepeatMode
import app.naviamp.ui.NaviampArtistAlbumCommand
import app.naviamp.ui.NaviampLibraryView
import app.naviamp.ui.NaviampMediaItemActionRequest
import app.naviamp.ui.NaviampMediaItemCommand
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingCurrentTrackUiActionRequest
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.NaviampQueueUndoAction
import app.naviamp.ui.NaviampQueueUndoActionRequest
import app.naviamp.ui.NaviampQueueUndoKind
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.ui.NowPlayingQueueAction
import app.naviamp.ui.NowPlayingQueueActionRequest
import app.naviamp.ui.SharedTrackRowAction
import app.naviamp.ui.SharedTrackRowActionRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreQueueUndoTest {
    @Test
    fun undoingAnUpcomingRemovalRestoresTheExactQueueWithoutTouchingPlayback() = undoTest {
        val core = core()
        removeAt(core, 4)

        val pending = assertNotNull(core.state.value.overlays.queueUndo)
        assertEquals(NaviampQueueUndoKind.RemovedTrack, pending.kind)
        assertEquals("b", pending.subject)
        assertEquals(listOf("a", "b", "c", "x", "d"), core.liveQueue().tracks.map { it.id.value })

        undo(core, pending.id)

        assertEquals(original, core.liveQueue())
        assertEquals(originalSnapshot, core.livePlayback().shuffledUpNextSnapshot)
        assertEquals(42.0, core.livePlayback().progress.positionSeconds)
        assertEquals(PlaybackState.Playing, core.livePlayback().playbackState)
        assertEquals(original, effects.appliedQueues.last())
        assertEquals(0, effects.stops)
        assertEquals(0, effects.startOrRestoreCount)
        assertTrue(effects.restored.isEmpty())
        assertTrue(effects.selections.isEmpty())
        assertNull(core.state.value.overlays.queueUndo)
    }

    @Test
    fun undoingClearQueueRestoresHistoryPlayNextAndGroups() = undoTest {
        val core = core()
        core.execute(NaviampCoreCommand.NowPlaying.Queue(NowPlayingQueueActionRequest(NowPlayingQueueAction.EmptyQueue)))
        assertEquals(PlaybackQueue(listOf(track("b")), currentIndex = 0), core.liveQueue())
        val pending = assertNotNull(core.state.value.overlays.queueUndo)
        assertEquals(NaviampQueueUndoKind.ClearedQueue, pending.kind)

        undo(core, pending.id)

        assertEquals(original, core.liveQueue())
        assertEquals(1, core.liveQueue().playNextCount)
        assertEquals(original.groups, core.liveQueue().groups)
        assertEquals(0, effects.stops)
        assertTrue(effects.restored.isEmpty())
    }

    @Test
    fun undoingAReplacementResumesThePreviousTrackAtItsCapturedPosition() = undoTest {
        val provider = FakeCoreMediaProvider()
        val core = core(provider)
        core.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
        val song = core.state.value.shell.library.songs.tracks.single()
        core.execute(NaviampCoreCommand.Library.TrackAction(SharedTrackRowActionRequest(song, SharedTrackRowAction.Select)))
        assertEquals(listOf(provider.track.id), core.liveQueue().tracks.map { it.id })
        val pending = assertNotNull(core.state.value.overlays.queueUndo)
        assertEquals(NaviampQueueUndoKind.ReplacedQueue, pending.kind)
        assertEquals("b", pending.subject)

        undo(core, pending.id)

        assertEquals(original, core.liveQueue())
        assertEquals("b", core.livePlayback().currentTrack?.id?.value)
        assertEquals(listOf<Pair<PlaybackQueue, Double?>>(original to 42.0), effects.restored)
        assertEquals(0, effects.stops)
        assertEquals(1, effects.startOrRestoreCount)
        assertEquals(RepeatMode.Queue, core.livePlayback().repeatMode)
    }

    @Test
    fun aNewerQueueChangeOrTrackAdvanceInvalidatesThePendingUndo() = undoTest {
        val core = core()
        removeAt(core, 4)
        val stale = assertNotNull(core.state.value.overlays.queueUndo).id
        core.execute(NaviampCoreCommand.NowPlaying.Queue(NowPlayingQueueActionRequest(NowPlayingQueueAction.MoveToNext, queueIndex = 4)))
        val newer = core.liveQueue()
        assertNull(core.state.value.overlays.queueUndo)

        undo(core, stale)
        assertEquals(newer, core.liveQueue())

        removeAt(core, 4)
        val afterRemoval = core.liveQueue()
        val pending = assertNotNull(core.state.value.overlays.queueUndo).id
        effects.observer?.onStateChanged(PlaybackState.Finished)
        assertEquals(afterRemoval.currentIndex + 1, core.liveQueue().currentIndex)
        assertNull(core.state.value.overlays.queueUndo)
        undo(core, pending)
        assertEquals(afterRemoval.currentIndex + 1, core.liveQueue().currentIndex)
    }

    @Test
    fun repeatedOrRapidUndoIsSingleLevelAndDeterministic() = undoTest {
        val core = core()
        removeAt(core, 4)
        val afterFirst = core.liveQueue()
        removeAt(core, 2)
        val pending = assertNotNull(core.state.value.overlays.queueUndo)
        assertEquals("c", pending.subject)

        undo(core, pending.id)
        assertEquals(afterFirst, core.liveQueue())
        undo(core, pending.id)
        assertEquals(afterFirst, core.liveQueue())
        assertNull(core.state.value.overlays.queueUndo)
    }

    @Test
    fun undoExpiresAfterItsWindowUnlessThePresentationNeedsMoreTimeOrHoldsFocus() = undoTest {
        val core = core()
        removeAt(core, 4)
        advanceTimeBy(9_999)
        runCurrent()
        assertNotNull(core.state.value.overlays.queueUndo)
        advanceTimeBy(2)
        runCurrent()
        assertNull(core.state.value.overlays.queueUndo)

        removeAt(core, 3)
        val accessible = assertNotNull(core.state.value.overlays.queueUndo).id
        notice(core, accessible, NaviampQueueUndoAction.Presented, timeoutMillis = 30_000)
        advanceTimeBy(29_000)
        runCurrent()
        assertNotNull(core.state.value.overlays.queueUndo)
        notice(core, accessible, NaviampQueueUndoAction.FocusGained)
        advanceTimeBy(120_000)
        runCurrent()
        assertNotNull(core.state.value.overlays.queueUndo)
        notice(core, accessible, NaviampQueueUndoAction.FocusLost)
        advanceTimeBy(10_001)
        runCurrent()
        assertNull(core.state.value.overlays.queueUndo)

        removeAt(core, 2)
        val dismissed = assertNotNull(core.state.value.overlays.queueUndo).id
        val afterDismissedRemoval = core.liveQueue()
        notice(core, dismissed, NaviampQueueUndoAction.Dismiss)
        assertNull(core.state.value.overlays.queueUndo)
        undo(core, dismissed)
        assertEquals(afterDismissedRemoval, core.liveQueue())
    }

    @Test
    fun removingTheCurrentTrackOrReplacingAQueueWithNothingUpcomingOffersNoUndo() = undoTest {
        val core = core()
        removeAt(core, 1)
        assertNull(core.state.value.overlays.queueUndo)

        val provider = FakeCoreMediaProvider()
        val single = core(provider, PlaybackQueue(listOf(track("solo")), currentIndex = 0))
        single.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
        val song = single.state.value.shell.library.songs.tracks.single()
        single.execute(NaviampCoreCommand.Library.TrackAction(SharedTrackRowActionRequest(song, SharedTrackRowAction.Select)))
        assertNull(single.state.value.overlays.queueUndo)
    }

    @Test
    fun undoingARadioStartFollowsTheRefillAndRestoresThePreviousSession() = undoTest {
        val provider = radioProvider()
        val core = core(provider)
        val album = FakeCoreMediaProvider().album.toSharedMediaItemUi({ null })
        core.execute(NaviampCoreCommand.Media.ItemAction(
            NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.Album(NaviampArtistAlbumCommand.Select))))
        runCurrent()
        core.execute(NaviampCoreCommand.Media.ItemAction(
            NaviampMediaItemActionRequest(album, NaviampMediaItemCommand.PlayAlbumMix)))
        runCurrent()
        assertTrue(core.liveQueue().tracks.size > 1, "the Radio refill landed")
        val pending = assertNotNull(core.state.value.overlays.queueUndo)
        assertEquals(NaviampQueueUndoKind.ReplacedQueue, pending.kind)

        undo(core, pending.id)

        assertEquals(original, core.liveQueue())
        assertEquals(listOf<Pair<PlaybackQueue, Double?>>(original to 42.0), effects.restored)
    }

    @Test
    fun undoingRadioFromTheCurrentTrackRestoresUpcomingTracksWithoutRestarting() = undoTest {
        val core = core(radioProvider())
        core.execute(NaviampCoreCommand.NowPlaying.CurrentTrack(
            NowPlayingCurrentTrackUiActionRequest(NowPlayingCurrentTrackAction.StartRadio)))
        runCurrent()
        assertEquals("b", core.liveQueue().current?.id?.value)
        val pending = assertNotNull(core.state.value.overlays.queueUndo)
        assertEquals(NaviampQueueUndoKind.ReplacedUpcoming, pending.kind)

        undo(core, pending.id)

        assertEquals(original, core.liveQueue())
        assertEquals(0, effects.stops)
        assertTrue(effects.restored.isEmpty())
        assertEquals(42.0, core.livePlayback().progress.positionSeconds)
    }

    private fun radioProvider(): app.naviamp.domain.provider.MediaProvider {
        val original = FakeCoreMediaProvider()
        val similar = (1..4).map { track("similar-$it").copy(albumId = app.naviamp.domain.AlbumId("other-$it")) }
        return object : app.naviamp.domain.provider.MediaProvider by original {
            override val capabilities = original.capabilities.copy(supportsTrackRadio = true)
            override suspend fun album(albumId: app.naviamp.domain.AlbumId) =
                app.naviamp.domain.AlbumDetails(original.album, listOf(original.track))
            override suspend fun instantMixTracks(trackId: TrackId, count: Int) = similar
        }
    }

    @Test
    fun undoingAReplacementOverAPausedQueueKeepsAPausedSessionAtTheCapturedPosition() = undoTest {
        val native = nativeCore(pauseOriginal = true)
        native.replaceWithLibrarySong()
        val undo = assertNotNull(native.core.state.value.overlays.queueUndo)
        native.states.clear()
        val playsBeforeUndo = native.engine.plays.size

        undo(native.core, undo.id)
        runCurrent()

        assertTrue(native.states.none { it == PlaybackState.Stopped || it == PlaybackState.Idle }, "states ${native.states}")
        val restored = native.core.livePlayback()
        assertEquals(PlaybackState.Paused, restored.playbackState)
        assertEquals(original, restored.queue)
        assertEquals("b", restored.currentTrack?.id?.value)
        assertEquals(42.0, restored.progress.positionSeconds)
        assertEquals(0, native.engine.stops)

        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(playsBeforeUndo, native.engine.plays.size, "nothing plays until the user presses play")
        assertEquals(PlaybackState.Paused, native.core.livePlayback().playbackState)
        assertEquals(1, native.core.livePlayback().queue.currentIndex)

        native.core.execute(NaviampCoreCommand.NowPlaying.Playback(
            NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.TogglePlayPause)))
        runCurrent()
        val resumed = native.engine.plays.last()
        assertEquals("b", resumed.mediaId)
        assertEquals(42.0, resumed.startPositionSeconds)
        assertEquals(PlaybackState.Playing, native.core.livePlayback().playbackState)
    }

    @Test
    fun undoingAReplacementOverAPlayingQueueResumesTheOriginalTrackAtItsPosition() = undoTest {
        val native = nativeCore(pauseOriginal = false)
        native.replaceWithLibrarySong()
        val undo = assertNotNull(native.core.state.value.overlays.queueUndo)
        native.states.clear()

        undo(native.core, undo.id)
        runCurrent()

        assertTrue(native.states.none { it == PlaybackState.Stopped || it == PlaybackState.Idle }, "states ${native.states}")
        val resumed = native.engine.plays.last()
        assertEquals("b", resumed.mediaId)
        assertEquals(42.0, resumed.startPositionSeconds)
        val restored = native.core.livePlayback()
        assertEquals(PlaybackState.Playing, restored.playbackState)
        assertEquals(original, restored.queue)
        assertEquals(42.0, restored.progress.positionSeconds)
    }

    private inner class NativeUndoFixture(
        val core: NaviampCore,
        val engine: UndoNativeEngine,
        val states: MutableList<PlaybackState>,
    ) {
        suspend fun replaceWithLibrarySong() {
            core.execute(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
            val song = core.state.value.shell.library.songs.tracks.single()
            core.execute(NaviampCoreCommand.Library.TrackAction(SharedTrackRowActionRequest(song, SharedTrackRowAction.Select)))
            scope.runCurrent()
            assertEquals("core-track", engine.plays.last().mediaId)
            // The replacement plays for a while before Undo, as it does on a device.
            engine.emitProgress(PlaybackProgress(7.0, 180.0))
            scope.runCurrent()
        }
    }

    /** The shared engine adapter over a native fake, primed like a device that played and maybe paused b at 0:42. */
    private suspend fun nativeCore(pauseOriginal: Boolean): NativeUndoFixture {
        val provider = FakeCoreMediaProvider()
        val engine = UndoNativeEngine()
        val adapter = NaviampCorePlaybackEngineAdapter(scope, engine,
            NaviampCoreMediaProviderSource { provider }, settings = { app.naviamp.domain.settings.PlaybackSettings() })
        val states = mutableListOf<PlaybackState>()
        val recording = object : NaviampCorePlaybackEffectPort by adapter {
            override fun attach(observer: NaviampCorePlaybackObserver) = adapter.attach(object : NaviampCorePlaybackObserver by observer {
                override fun onStateChanged(state: PlaybackState) {
                    states += state
                    observer.onStateChanged(state)
                }
            })
        }
        val defaults = fakeCoreServices(provider)
        val core = NaviampCore.create(
            scope,
            defaults.copy(playback = defaults.playback.copy(effects = recording)),
            NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(currentTrack = original.current, queue = original),
                connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "source"),
            ).let { initial ->
                initial.copy(product = initial.product.copy(shell = initial.product.shell.copy(
                    connectionSettings = initial.product.shell.connectionSettings.copy(currentSourceId = "source"),
                )))
            },
        ).also(cores::add)
        recording.playQueueSelection(original, original.currentIndex)
        scope.runCurrent()
        engine.emitProgress(PlaybackProgress(42.0, 180.0))
        if (pauseOriginal) {
            core.execute(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.Pause)))
            scope.runCurrent()
            assertEquals(PlaybackState.Paused, core.livePlayback().playbackState)
        }
        assertEquals(42.0, core.livePlayback().progress.positionSeconds)
        return NativeUndoFixture(core, engine, states)
    }

    private val group = PlaybackQueueGroup(
        id = "Album:album-1",
        target = PlaybackProfileTarget(PlaybackProfileTargetType.Album, "album-1"),
        label = "Album",
        startIndex = 2,
        endIndexExclusive = 6,
        profile = PlaybackProfile(transitionMode = PlaybackTransitionMode.Gapless),
    )
    private val original = PlaybackQueue(
        tracks = listOf("a", "b", "c", "x", "b", "d").map(::track),
        currentIndex = 1,
        playNextCount = 1,
        groups = listOf(group),
    )
    private val originalSnapshot = listOf("x", "c", "b", "d").map(::track)
    private lateinit var effects: UndoPlaybackEffects
    private lateinit var scope: TestScope
    private val cores = mutableListOf<NaviampCore>()

    private fun undoTest(body: suspend TestScope.() -> Unit) = runTest {
        scope = this
        effects = UndoPlaybackEffects()
        try { body() } finally { cores.forEach(NaviampCore::close) }
    }

    private fun core(
        provider: app.naviamp.domain.provider.MediaProvider? = null,
        queue: PlaybackQueue = original,
    ): NaviampCore {
        val defaults = fakeCoreServices(provider)
        return NaviampCore.create(
            scope,
            defaults.copy(playback = defaults.playback.copy(effects = effects)),
            NaviampCoreInitialState(
                playback = NaviampLivePlaybackState(
                    currentTrack = queue.current,
                    queue = queue,
                    progress = PlaybackProgress(42.0, 180.0),
                    playbackState = PlaybackState.Playing,
                    repeatMode = RepeatMode.Queue,
                    shuffledUpNextSnapshot = originalSnapshot.takeIf { queue == original },
                ),
                connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "source"),
            ).let { initial ->
                initial.copy(product = initial.product.copy(shell = initial.product.shell.copy(
                    connectionSettings = initial.product.shell.connectionSettings.copy(currentSourceId = "source"),
                )))
            },
        ).also(cores::add)
    }

    private suspend fun removeAt(core: NaviampCore, index: Int) {
        core.execute(NaviampCoreCommand.NowPlaying.Queue(
            NowPlayingQueueActionRequest(NowPlayingQueueAction.RemoveFromQueue, queueIndex = index),
        ))
    }

    private fun undo(core: NaviampCore, id: Long) = notice(core, id, NaviampQueueUndoAction.Undo)

    private fun notice(core: NaviampCore, id: Long, action: NaviampQueueUndoAction, timeoutMillis: Long? = null) =
        core.dispatch(NaviampCoreCommand.NowPlaying.QueueUndo(NaviampQueueUndoActionRequest(id, action, timeoutMillis)))

    private fun NaviampCore.livePlayback(): NaviampLivePlaybackState {
        var live: NaviampLivePlaybackState? = null
        updateLivePlayback { current -> current.also { live = it } }
        return checkNotNull(live)
    }
    private fun NaviampCore.liveQueue() = livePlayback().queue
}

private class UndoNativeEngine : app.naviamp.domain.playback.PlaybackEngine {
    override val name = "Native"
    override val supportsPause = true
    override val supportsSeek = true
    override val supportsGapless = false
    override val supportsCrossfade = false
    override val supportsReplayGain = false
    override val supportsSoftwareVolume = true
    override val prefersOriginalStream = true
    val plays = mutableListOf<app.naviamp.domain.playback.PlaybackRequest>()
    var stops = 0
    private var state: ((PlaybackState) -> Unit)? = null
    private var progress: ((PlaybackProgress) -> Unit)? = null

    override fun play(
        scope: kotlinx.coroutines.CoroutineScope,
        request: app.naviamp.domain.playback.PlaybackRequest,
        onStateChanged: (PlaybackState) -> Unit,
        onProgressChanged: (PlaybackProgress) -> Unit,
        onMetadataChanged: (app.naviamp.domain.playback.PlaybackStreamMetadata) -> Unit,
    ) {
        plays += request
        playScope = scope
        state = onStateChanged
        progress = onProgressChanged
        onStateChanged(PlaybackState.Playing)
        emitProgress(PlaybackProgress(request.startPositionSeconds ?: 0.0, 180.0))
    }

    // Like the device engine, pausing later reports where the loaded stream stopped.
    override fun pause() {
        state?.invoke(PlaybackState.Paused)
        val report = progress
        val last = lastProgress ?: return
        playScope?.launch { report?.invoke(last) }
    }
    override fun resume() { state?.invoke(PlaybackState.Playing) }
    override fun seek(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() { stops += 1 }
    private var lastProgress: PlaybackProgress? = null
    private var playScope: kotlinx.coroutines.CoroutineScope? = null
    fun emitProgress(value: PlaybackProgress) {
        lastProgress = value
        progress?.invoke(value)
    }
}

private class UndoPlaybackEffects : NaviampCorePlaybackEffectPort {
    override val capabilities = NaviampCorePlaybackCapabilities()
    override val playbackSource = PlaybackSource.ProviderStream
    val appliedQueues = mutableListOf<PlaybackQueue>()
    val selections = mutableListOf<PlaybackQueue>()
    val restored = mutableListOf<Pair<PlaybackQueue, Double?>>()
    var stops = 0
    var startOrRestoreCount = 0
    var observer: NaviampCorePlaybackObserver? = null

    override fun attach(observer: NaviampCorePlaybackObserver) { this.observer = observer }
    override fun pause() = Unit
    override fun resume() = Unit
    override fun startOrRestore(): Boolean { startOrRestoreCount += 1; return true }
    override fun seek(positionSeconds: Double) = Unit
    override fun replayCurrent(positionSeconds: Double) = Unit
    override fun setVolume(percent: Int) = Unit
    override fun stop() { stops += 1 }
    override fun applyQueue(queue: PlaybackQueue, clearPreparedNext: Boolean) { appliedQueues += queue }
    override fun restoreQueue(queue: PlaybackQueue, startPositionSeconds: Double?) { restored += queue to startPositionSeconds }
    override fun applyNavigation(command: PlaybackQueueNavigationCommand) = Unit
    override fun applyRepeatMode(mode: RepeatMode) = Unit
    override fun playQueueSelection(queue: PlaybackQueue, index: Int) { selections += queue }
}

private fun track(id: String) = Track(
    id = TrackId(id),
    title = id,
    artistName = "Artist",
    albumTitle = "Album",
    durationSeconds = 180,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
)
