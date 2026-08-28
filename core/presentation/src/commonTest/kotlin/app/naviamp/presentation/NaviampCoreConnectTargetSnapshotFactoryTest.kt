package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NaviampCoreConnectTargetSnapshotFactoryTest {
    private val factory = NaviampCoreConnectTargetSnapshotFactory(
        target = NaviampConnectDevice("tv", "Living Room", NaviampConnectDeviceRole.Target),
        capabilities = setOf(
            NaviampConnectCapability.TransportControls,
            NaviampConnectCapability.QueueRead,
        ),
    )

    @Test
    fun projectsCanonicalPlaybackAndQueueState() {
        val first = track("same", "First", favorite = true)
        val second = track("same", "Second")
        val snapshot = factory.create(
            revision = 7,
            live = NaviampLivePlaybackState(
                currentTrack = second,
                queue = PlaybackQueue(listOf(first, second), currentIndex = 1),
                progress = PlaybackProgress(12.345, 61.0),
                playbackState = PlaybackState.Playing,
                repeatMode = RepeatMode.Track,
                shuffledUpNextSnapshot = emptyList(),
            ),
            volumePercent = 125,
        )

        assertEquals(7, snapshot.revision)
        assertEquals(NaviampConnectPlaybackState.Playing, snapshot.playback.state)
        assertEquals(12_345, snapshot.playback.positionMillis)
        assertEquals(61_000, snapshot.playback.durationMillis)
        assertEquals(NaviampConnectRepeatMode.One, snapshot.playback.repeatMode)
        assertEquals(true, snapshot.playback.shuffled)
        assertEquals(100, snapshot.playback.volumePercent)
        assertEquals(1, snapshot.queue.currentIndex)
        assertEquals(snapshot.queue.occurrences[1].occurrenceId, snapshot.playback.currentOccurrenceId)
        assertNotEquals(snapshot.queue.occurrences[0].occurrenceId, snapshot.queue.occurrences[1].occurrenceId)
        assertEquals(true, snapshot.queue.occurrences[0].favorite)
        assertEquals(false, snapshot.queue.occurrences[1].favorite)
    }

    @Test
    fun normalizesInvalidEmptyPlaybackValues() {
        val snapshot = factory.create(
            revision = 0,
            live = NaviampLivePlaybackState(
                progress = PlaybackProgress(Double.NaN, -4.0),
                playbackState = PlaybackState.Stopped,
            ),
            volumePercent = -20,
        )

        assertEquals(NaviampConnectPlaybackState.Idle, snapshot.playback.state)
        assertEquals(0, snapshot.playback.positionMillis)
        assertEquals(null, snapshot.playback.durationMillis)
        assertEquals(0, snapshot.playback.volumePercent)
        assertEquals(-1, snapshot.queue.currentIndex)
        assertEquals(emptyList(), snapshot.queue.occurrences)
    }

    private fun track(id: String, title: String, favorite: Boolean = false) = Track(
        id = TrackId(id),
        title = title,
        artistName = "Artist",
        albumId = AlbumId("album"),
        albumTitle = "Album",
        durationSeconds = 61,
        coverArtId = "cover",
        audioInfo = null,
        replayGain = null,
        favoritedAtIso8601 = "2026-08-28T00:00:00Z".takeIf { favorite },
    )
}
