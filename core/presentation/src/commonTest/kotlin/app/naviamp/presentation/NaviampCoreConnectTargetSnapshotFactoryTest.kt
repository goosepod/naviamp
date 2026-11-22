package app.naviamp.presentation

import app.naviamp.app.NaviampLivePlaybackState
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.connect.NaviampConnectCapability
import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectPlaybackState
import app.naviamp.domain.connect.NaviampConnectPause
import app.naviamp.domain.connect.NaviampConnectPlay
import app.naviamp.domain.connect.NaviampConnectTogglePlayPause
import app.naviamp.domain.connect.NaviampConnectRepeatMode
import app.naviamp.domain.connect.NaviampConnectSourceIdentity
import app.naviamp.domain.playback.PlaybackProgress
import app.naviamp.domain.playback.PlaybackState
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.queue.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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

    @Test
    fun suppressesProgressOnlyTargetSnapshotsButPublishesStructuralPlaybackChanges() {
        val current = track("current", "Current")
        val initial = NaviampLivePlaybackState(
            currentTrack = current,
            queue = PlaybackQueue(listOf(current), currentIndex = 0),
            progress = PlaybackProgress(10.0, 60.0),
            playbackState = PlaybackState.Playing,
        )

        assertEquals(
            false,
            shouldPublishNaviampConnectTargetSnapshot(
                initial,
                initial.copy(progress = PlaybackProgress(11.0, 60.0)),
            ),
        )
        assertEquals(
            true,
            shouldPublishNaviampConnectTargetSnapshot(
                initial,
                initial.copy(playbackState = PlaybackState.Paused),
            ),
        )
        assertEquals(
            true,
            shouldPublishNaviampConnectTargetSnapshot(
                initial,
                initial.copy(currentTrack = track("next", "Next")),
            ),
        )
    }

    private fun playingAt(positionSeconds: Double, state: PlaybackState = PlaybackState.Playing): NaviampLivePlaybackState {
        val current = track("current", "Current")
        return NaviampLivePlaybackState(
            currentTrack = current,
            queue = PlaybackQueue(listOf(current), currentIndex = 0),
            progress = PlaybackProgress(positionSeconds, 180.0),
            playbackState = state,
        )
    }

    @Test
    fun normalProgressAgainstThePublishedTimelineStaysSuppressed() {
        // Published at 10 s while playing at t=0; the engine advances in step for 60 s.
        val published = NaviampConnectTargetTimeline(10.0, playing = true, anchoredAtMillis = 0L)
        var previous = playingAt(10.0)
        for (second in 1..60) {
            val updated = playingAt(10.0 + second - 0.3) // sampling lag behind wall time
            assertEquals(false, shouldPublishNaviampConnectTargetSnapshot(previous, updated, published, second * 1_000L),
                "normal progress at $second s must not republish the queue")
            previous = updated
        }
    }

    @Test
    fun targetSeekPublishesAsAPositionDiscontinuity() {
        val published = NaviampConnectTargetTimeline(10.0, playing = true, anchoredAtMillis = 0L)
        val previous = playingAt(15.0)
        // Forward seek from the TV remote: no seek generation, position jumps well past the timeline.
        assertTrue(shouldPublishNaviampConnectTargetSnapshot(previous, playingAt(95.0), published, 5_000L))
        // Backward seek.
        assertTrue(shouldPublishNaviampConnectTargetSnapshot(previous, playingAt(14.0), published, 5_000L))
        // A seek through the shared seek plan advances seek generation even for a small move.
        assertTrue(shouldPublishNaviampConnectTargetSnapshot(previous,
            playingAt(16.0).let { it.copy(progress = it.progress.copy(seekGeneration = 1L)) }, published, 5_000L))
        // A seek while paused.
        val paused = NaviampConnectTargetTimeline(40.0, playing = false, anchoredAtMillis = 0L)
        assertTrue(shouldPublishNaviampConnectTargetSnapshot(playingAt(40.0, PlaybackState.Paused),
            playingAt(70.0, PlaybackState.Paused), paused, 30_000L))
        assertEquals(false, shouldPublishNaviampConnectTargetSnapshot(playingAt(40.0, PlaybackState.Paused),
            playingAt(40.0, PlaybackState.Paused), paused, 30_000L))
    }

    @Test
    fun stalledPlaybackPublishesOnceItDriftsAndAnEndingTrackDoesNot() {
        val published = NaviampConnectTargetTimeline(10.0, playing = true, anchoredAtMillis = 0L)
        // Nominally playing but stuck at 12 s: controllers would run ahead.
        assertEquals(false, shouldPublishNaviampConnectTargetSnapshot(playingAt(12.0), playingAt(12.0), published, 3_000L))
        assertTrue(shouldPublishNaviampConnectTargetSnapshot(playingAt(12.0), playingAt(12.0), published, 5_000L))
        // Holding at the end of the track matches a controller that stops at the duration.
        val ending = NaviampConnectTargetTimeline(178.0, playing = true, anchoredAtMillis = 0L)
        assertEquals(false, shouldPublishNaviampConnectTargetSnapshot(playingAt(180.0), playingAt(180.0), ending, 20_000L))
    }

    @Test
    fun attachingControllerReceivesTheCurrentPosition() {
        val snapshot = factory.create(revision = 0, live = playingAt(120.4), volumePercent = 50)
        assertEquals(120_400L, snapshot.playback.positionMillis)
        assertEquals(NaviampConnectPlaybackState.Playing, snapshot.playback.state)
    }

    @Test
    fun successfulRemotePlayIntentRevealsTargetNowPlayingOnlyWhenPlaybackStarts() {
        assertTrue(NaviampConnectPlay.revealsNowPlayingAfter(NaviampConnectPlaybackState.Playing))
        assertTrue(NaviampConnectPlay.revealsNowPlayingAfter(NaviampConnectPlaybackState.Buffering))
        assertTrue(
            NaviampConnectTogglePlayPause.revealsNowPlayingAfter(NaviampConnectPlaybackState.Playing),
        )
        assertEquals(
            false,
            NaviampConnectTogglePlayPause.revealsNowPlayingAfter(NaviampConnectPlaybackState.Paused),
        )
        assertEquals(
            false,
            NaviampConnectPause.revealsNowPlayingAfter(NaviampConnectPlaybackState.Paused),
        )
    }

    @Test
    fun projectsControllerLocalQueueDirectlyIntoHandoffCommand() {
        val first = track("first", "First")
        val second = track("second", "Second")
        val identity = NaviampConnectSourceIdentity(
            providerId = "navidrome",
            canonicalServerOrigin = "https://music.example.test",
            accountIdentity = "listener",
        )

        val handoff = naviampCoreConnectQueueHandoff(
            live = NaviampLivePlaybackState(
                currentTrack = second,
                queue = PlaybackQueue(listOf(first, second), currentIndex = 1),
                progress = PlaybackProgress(12.345, 61.0),
                playbackState = PlaybackState.Playing,
                repeatMode = RepeatMode.Queue,
                shuffledUpNextSnapshot = emptyList(),
            ),
            sourceIdentity = identity,
        )

        assertEquals(identity, handoff.sourceIdentity)
        assertEquals(listOf("first", "second"), handoff.queue.occurrences.map { it.mediaId })
        assertEquals(listOf("artist", "artist"), handoff.queue.occurrences.map { it.artistId })
        assertEquals(listOf("album", "album"), handoff.queue.occurrences.map { it.albumId })
        assertEquals(listOf("cover", "cover"), handoff.queue.occurrences.map { it.artworkId })
        assertEquals(1, handoff.queue.currentIndex)
        assertEquals(12_345, handoff.positionMillis)
        assertEquals(NaviampConnectRepeatMode.All, handoff.repeatMode)
        assertEquals(true, handoff.shuffled)
        assertEquals(true, handoff.playing)
        assertTrue(handoff.revealsNowPlayingAfter(NaviampConnectPlaybackState.Playing))
        assertEquals(
            false,
            handoff.copy(playing = false).revealsNowPlayingAfter(NaviampConnectPlaybackState.Paused),
        )
    }

    @Test
    fun firstRemotePlayCanStartAPausedControllerQueue() {
        val current = track("current", "Current")

        val handoff = naviampCoreConnectQueueHandoff(
            live = NaviampLivePlaybackState(
                currentTrack = current,
                queue = PlaybackQueue(listOf(current), currentIndex = 0),
                playbackState = PlaybackState.Paused,
            ),
            sourceIdentity = NaviampConnectSourceIdentity(
                providerId = "navidrome",
                canonicalServerOrigin = "https://music.example.test",
                accountIdentity = "listener",
            ),
            playing = true,
        )

        assertTrue(handoff.playing)
    }

    private fun track(id: String, title: String, favorite: Boolean = false) = Track(
        id = TrackId(id),
        title = title,
        artistId = ArtistId("artist"),
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
