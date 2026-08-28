package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectCommand
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectPlaybackSnapshot
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectQueueSnapshot
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.connect.NaviampConnectSeek
import app.naviamp.domain.connect.NaviampConnectSelectQueueOccurrence
import app.naviamp.domain.connect.NaviampConnectSetFavorite
import app.naviamp.domain.connect.NaviampConnectSetRepeat
import app.naviamp.domain.connect.NaviampConnectSetShuffle
import app.naviamp.domain.connect.NaviampConnectTargetSnapshot
import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NowPlayingCurrentTrackAction
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingSelectionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreConnectRemoteNowPlayingTest {
    @Test
    fun projectsRemoteQueueAndCapabilitiesIntoSharedNowPlayingUi() {
        val ui = snapshot().toRemoteNowPlayingUi("Living Room TV")

        assertEquals("Current", ui.title)
        assertEquals("Playing on Living Room TV", ui.stateLabel)
        assertEquals(12.5, ui.positionSeconds)
        assertEquals(NaviampRepeatMode.Queue, ui.repeatMode)
        assertTrue(ui.canFavorite)
        assertTrue(ui.favoriteActive)
        assertFalse(ui.hasPrevious)
        assertTrue(ui.hasNext)
        assertEquals(listOf("queue:1", "queue:2"), ui.upNext.map { it.id })
        assertTrue(ui.upNext.first().playNextPriority)
    }

    @Test
    fun sharedNowPlayingActionsProduceAbsoluteRemoteCommands() {
        val snapshot = snapshot()
        val sent = mutableListOf<NaviampConnectCommand>()
        val actions = createNaviampCoreConnectRemoteNowPlayingActions({ snapshot }, sent::add)

        actions.seek(42.25)
        actions.playback(NowPlayingPlaybackAction.ToggleShuffle)
        actions.playback(NowPlayingPlaybackAction.CycleRepeatMode)
        actions.currentTrack(NowPlayingCurrentTrackAction.ToggleFavorite)
        actions.selectItem(snapshot.toRemoteNowPlayingUi("TV").upNext.last(), NowPlayingSelectionAction.SelectQueueItem)

        assertEquals(NaviampConnectSeek(42_250), sent[0])
        assertEquals(NaviampConnectSetShuffle(true), sent[1])
        assertEquals(NaviampConnectSetRepeat(NaviampConnectRepeatMode.One), sent[2])
        assertEquals(NaviampConnectSetFavorite("current", false), sent[3])
        assertEquals(NaviampConnectSelectQueueOccurrence("2:third"), sent[4])
    }

    private fun snapshot() = NaviampConnectTargetSnapshot(
        revision = 4,
        target = NaviampConnectDevice("tv", "Living Room TV", NaviampConnectDeviceRole.Target),
        capabilities = setOf(
            NaviampConnectCapability.TransportControls,
            NaviampConnectCapability.Seeking,
            NaviampConnectCapability.Favorites,
            NaviampConnectCapability.Repeat,
            NaviampConnectCapability.Shuffle,
            NaviampConnectCapability.QueueRead,
            NaviampConnectCapability.QueueSelect,
            NaviampConnectCapability.QueueEdit,
            NaviampConnectCapability.QueueReorder,
        ),
        playback = NaviampConnectPlaybackSnapshot(
            state = NaviampConnectPlaybackState.Playing,
            currentOccurrenceId = "0:current",
            positionMillis = 12_500,
            durationMillis = 180_000,
            repeatMode = NaviampConnectRepeatMode.All,
        ),
        queue = NaviampConnectQueueSnapshot(
            occurrences = listOf(
                occurrence("0:current", "current", "Current", favorite = true),
                occurrence("1:next", "next", "Next"),
                occurrence("2:third", "third", "Third"),
            ),
            currentIndex = 0,
            playNextCount = 1,
        ),
    )

    private fun occurrence(id: String, mediaId: String, title: String, favorite: Boolean = false) =
        NaviampConnectQueueOccurrence(
            occurrenceId = id,
            mediaId = mediaId,
            title = title,
            artistName = "Artist",
            albumTitle = "Album",
            favorite = favorite,
        )
}
