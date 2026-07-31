package app.naviamp.presentation

import app.naviamp.ui.NaviampAppShellUiState
import app.naviamp.ui.NaviampNowPlayingItemUi
import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NaviampHomeScreenUi
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingUi
import app.naviamp.ui.SharedHomeUi
import app.naviamp.ui.SharedTrackRowUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NaviampCoreExternalPlaybackBridgeTest {
    @Test
    fun playbackServiceRetentionIgnoresTransientIdleDuringTrackAdvance() = runTest {
        val snapshots = MutableSharedFlow<NaviampExternalPlaybackSnapshot>()
        val decisions = mutableListOf<Boolean>()
        backgroundScope.launch { snapshots.playbackServiceRetentionDecisions().toList(decisions) }
        runCurrent()
        val current = NaviampExternalMediaItem("track", "Track", "Artist")

        snapshots.emit(
            NaviampExternalPlaybackSnapshot(
                state = NaviampExternalPlaybackState.Playing,
                current = current,
            ),
        )
        runCurrent()
        snapshots.emit(NaviampExternalPlaybackSnapshot())
        advanceTimeBy(999L)
        snapshots.emit(
            NaviampExternalPlaybackSnapshot(
                state = NaviampExternalPlaybackState.Loading,
                current = current.copy(mediaId = "next"),
            ),
        )
        runCurrent()

        assertEquals(listOf(true), decisions)
    }

    @Test
    fun playbackServiceRetentionReleasesAfterStableIdleGracePeriod() = runTest {
        val snapshots = MutableSharedFlow<NaviampExternalPlaybackSnapshot>()
        val decisions = mutableListOf<Boolean>()
        backgroundScope.launch { snapshots.playbackServiceRetentionDecisions().toList(decisions) }
        runCurrent()
        val current = NaviampExternalMediaItem("track", "Track", "Artist")

        snapshots.emit(
            NaviampExternalPlaybackSnapshot(
                state = NaviampExternalPlaybackState.Playing,
                current = current,
            ),
        )
        runCurrent()
        snapshots.emit(NaviampExternalPlaybackSnapshot())
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals(listOf(true, false), decisions)
    }

    @Test
    fun projectsCanonicalQueueAndPlaybackStateForNativeSurfaces() {
        val bridge = bridge()

        val snapshot = bridge.snapshot()

        assertEquals(NaviampExternalPlaybackState.Playing, snapshot.state)
        assertEquals(listOf("Earlier", "Current", "Later"), snapshot.queue.map { it.title })
        assertEquals(1, snapshot.currentQueueIndex)
        assertEquals("Current", snapshot.current?.title)
        assertEquals("https://example.test/current-art", snapshot.current?.artworkUrl)
        assertEquals(12_500L, snapshot.positionMillis)
        assertEquals(180_000L, snapshot.durationMillis)
        assertTrue(snapshot.shouldRetainPlaybackService)
    }

    @Test
    fun nativeTransportAndQueueSelectionEmitTypedCoreCommands() {
        val commands = mutableListOf<NaviampCoreCommand>()
        val bridge = bridge(commands)

        bridge.pause()
        bridge.seekTo(9_750L)
        bridge.selectQueueItem(2)

        assertEquals(
            NowPlayingPlaybackAction.Pause,
            assertIs<NaviampCoreCommand.NowPlaying.Playback>(commands[0]).request.action,
        )
        assertEquals(
            9.75,
            assertIs<NaviampCoreCommand.NowPlaying.Playback>(commands[1]).request.seekSeconds,
        )
        assertEquals(
            "later",
            assertIs<NaviampCoreCommand.NowPlaying.Selection>(commands[2]).request.item.id,
        )
    }

    @Test
    fun nativeRelativeSeekUsesSharedStepAndTrackBounds() {
        val commands = mutableListOf<NaviampCoreCommand>()
        val bridge = bridge(commands)

        bridge.rewind()
        bridge.fastForward()
        bridge.seekBy(200_000L)

        assertEquals(
            listOf(2.5, 22.5, 180.0),
            commands.map { assertIs<NaviampCoreCommand.NowPlaying.Playback>(it).request.seekSeconds },
        )
    }

    @Test
    fun automotiveShuffleDispatchesTheSharedPlaybackCommand() {
        val commands = mutableListOf<NaviampCoreCommand>()
        val bridge = bridge(commands)

        bridge.toggleShuffle()

        assertEquals(
            NowPlayingPlaybackAction.ToggleShuffle,
            assertIs<NaviampCoreCommand.NowPlaying.Playback>(commands.single()).request.action,
        )
    }

    @Test
    fun nativeShuffleAndRepeatSelectionsMapToSharedPlaybackCommands() {
        val commands = mutableListOf<NaviampCoreCommand>()
        val bridge = bridge(commands)

        bridge.setShuffleActive(true)
        bridge.setRepeatMode(NaviampRepeatMode.Track)

        assertEquals(
            listOf(
                NowPlayingPlaybackAction.ToggleShuffle,
                NowPlayingPlaybackAction.CycleRepeatMode,
                NowPlayingPlaybackAction.CycleRepeatMode,
            ),
            commands.map { assertIs<NaviampCoreCommand.NowPlaying.Playback>(it).request.action },
        )
    }

    @Test
    fun idleProjectionDoesNotRetainNativePlaybackService() {
        val state = MutableStateFlow(NaviampCoreState())
        val snapshot = NaviampCoreExternalPlaybackBridge(state) {}.snapshot()

        assertFalse(snapshot.shouldRetainPlaybackService)
        assertEquals(emptyList(), snapshot.queue)
    }

    @Test
    fun automotiveCatalogAndSelectionAreOwnedByCore() {
        val commands = mutableListOf<NaviampCoreCommand>()
        val track = SharedTrackRowUi("recent", "Recent Track", "Artist")
        val state = MutableStateFlow(
            NaviampCoreState(
                shell = NaviampAppShellUiState(
                    home = NaviampHomeScreenUi(
                        SharedHomeUi(recentlyPlayedTracks = listOf(track)),
                    ),
                ),
            ),
        )
        val bridge = NaviampCoreExternalPlaybackBridge(state, commands::add)

        assertTrue(
            bridge.browseChildren(NaviampExternalMediaRootId)
                .any { it.mediaId == NaviampExternalRecentTracksId && !it.playable },
        )
        val playable = bridge.browseChildren(NaviampExternalRecentTracksId).single()
        assertTrue(bridge.playMediaId(playable.mediaId))

        val command = assertIs<NaviampCoreCommand.Media.TrackAction>(commands.single())
        assertEquals("recent", command.request.track.id)
        assertEquals(app.naviamp.ui.SharedTrackRowAction.Select, command.request.action)
    }

    @Test
    fun automotiveQueueStartsAtCurrentAndRetainsPlaybackHistory() {
        val bridge = bridge()

        val queue = bridge.browseChildren(NaviampExternalQueueId)

        assertEquals(listOf("Current", "Later", "Earlier"), queue.map(NaviampExternalMediaItem::title))
        assertEquals(listOf(1, 2, 0), queue.map(NaviampExternalMediaItem::queueIndex))
    }

    @Test
    fun automotiveVoiceSearchSelectsThroughTheCommonCatalog() {
        val commands = mutableListOf<NaviampCoreCommand>()
        val track = SharedTrackRowUi("recent", "Recent Track", "Artist")
        val state = MutableStateFlow(
            NaviampCoreState(
                shell = NaviampAppShellUiState(
                    home = NaviampHomeScreenUi(
                        SharedHomeUi(recentlyPlayedTracks = listOf(track)),
                    ),
                ),
            ),
        )
        val bridge = NaviampCoreExternalPlaybackBridge(state, commands::add)

        assertTrue(bridge.playSearch("Recent"))
        assertFalse(bridge.playSearch("Missing"))
        assertIs<NaviampCoreCommand.Media.TrackAction>(commands.single())
    }

    @Test
    fun externalPublicationPlannerSuppressesProgressTickNativeChurn() {
        val planner = NaviampExternalPlaybackPublicationPlanner(maximumNaturalPositionStepMillis = 2_500L)
        val current = NaviampExternalMediaItem("track", "Track", "Artist")
        val initial = NaviampExternalPlaybackSnapshot(
            state = NaviampExternalPlaybackState.Playing,
            current = current,
            queue = listOf(current),
            positionMillis = 10_000L,
        )

        val first = planner.plan(initial)
        val progressTick = planner.plan(initial.copy(positionMillis = 10_250L))
        val routineTicks = (11_250L..15_250L step 1_000L).map { position ->
            planner.plan(initial.copy(positionMillis = position))
        }
        val seekRefresh = planner.plan(initial.copy(positionMillis = 45_000L))

        assertTrue(first.sessionContent && first.playbackState && first.browseCatalog && first.notification)
        assertFalse(progressTick.sessionContent)
        assertFalse(progressTick.playbackState)
        assertFalse(progressTick.browseCatalog)
        assertFalse(progressTick.notification)
        assertTrue(routineTicks.all { publication -> !publication.playbackState })
        assertTrue(seekRefresh.playbackState)
        assertFalse(seekRefresh.sessionContent)
        assertFalse(seekRefresh.notification)
    }

    @Test
    fun externalPublicationPlannerPublishesSemanticChangesImmediately() {
        val planner = NaviampExternalPlaybackPublicationPlanner()
        val current = NaviampExternalMediaItem("track", "Track", "Artist")
        val playing = NaviampExternalPlaybackSnapshot(
            state = NaviampExternalPlaybackState.Playing,
            current = current,
            queue = listOf(current),
            positionMillis = 10_000L,
        )
        planner.plan(playing)

        val paused = planner.plan(playing.copy(state = NaviampExternalPlaybackState.Paused, positionMillis = 10_100L))

        assertTrue(paused.playbackState)
        assertTrue(paused.notification)
        assertFalse(paused.sessionContent)
        assertFalse(paused.browseCatalog)
    }
}

private fun bridge(
    commands: MutableList<NaviampCoreCommand> = mutableListOf(),
): NaviampCoreExternalPlaybackBridge {
    val nowPlaying = NowPlayingUi(
        id = "current",
        title = "Current",
        subtitle = "Artist",
        coverArtUrl = "https://example.test/current-art",
        stateLabel = "Playing",
        isPlaying = true,
        canPlayPause = true,
        positionSeconds = 12.5,
        durationSeconds = 180.0,
        hasPrevious = true,
        hasNext = true,
        backTo = listOf(NaviampNowPlayingItemUi("earlier", "Earlier", "Artist")),
        upNext = listOf(NaviampNowPlayingItemUi("later", "Later", "Artist")),
    )
    val state = MutableStateFlow(
        NaviampCoreState(shell = NaviampAppShellUiState(nowPlaying = nowPlaying)),
    )
    return NaviampCoreExternalPlaybackBridge(state, commands::add)
}
