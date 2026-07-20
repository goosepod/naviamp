package app.naviamp.android.playback

import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.queue.PlaybackQueue
import app.naviamp.domain.settings.PlaybackSessionSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidPlaybackServiceSessionControllerTest {
    @AfterTest
    fun clearNotificationState() {
        AndroidPlaybackNotificationControls.clear()
    }

    @Test
    fun hydrationRestoresQueuePositionAndRepublishesSystemSurfaces() {
        val first = track("first")
        val current = track("current")
        val session = assertNotNull(
            PlaybackSessionSettings.fromTracks(
                tracks = listOf(first, current),
                currentIndex = 1,
                positionSeconds = 47.25,
            ),
        )
        var metadata = AndroidPlaybackNotificationMetadata()
        var queue = PlaybackQueue()
        val mediaSessionUpdates = mutableListOf<AndroidPlaybackNotificationMetadata>()
        val artworkLoads = mutableListOf<Pair<String, AndroidPlaybackNotificationMetadata>>()
        val controller = AndroidPlaybackServiceSessionController(
            sessions = FakeServiceSessionStore(session, "https://art.example.test/current"),
            currentMetadata = { metadata },
            setCurrentMetadata = { metadata = it },
            syncQueue = { queue = it },
            updateMediaSession = mediaSessionUpdates::add,
            loadCoverArt = { url, update -> artworkLoads += url to update },
        )

        assertTrue(controller.hydrateSavedPlaybackSession())

        assertEquals(listOf(first, current), queue.tracks)
        assertEquals(1, queue.currentIndex)
        assertEquals(47_250L, AndroidPlaybackNotificationControls.positionMillis)
        assertEquals(180_000L, AndroidPlaybackNotificationControls.durationMillis)
        assertEquals("Track current", metadata.title)
        assertEquals("Artist", metadata.subtitle)
        assertEquals(listOf(metadata), mediaSessionUpdates)
        assertEquals(listOf("https://art.example.test/current" to metadata), artworkLoads)

        assertFalse(controller.hydrateSavedPlaybackSession())
        assertEquals(1, mediaSessionUpdates.size)
        assertEquals(1, artworkLoads.size)
    }

    private fun track(id: String): Track = Track(
        id = TrackId(id),
        title = "Track $id",
        artistName = "Artist",
        albumTitle = "Album",
        durationSeconds = 180,
        coverArtId = "cover-$id",
        audioInfo = null,
        replayGain = null,
    )
}

private class FakeServiceSessionStore(
    private val session: PlaybackSessionSettings,
    private val artworkUrl: String?,
) : AndroidPlaybackServiceSessionStore {
    override fun latestSession() = AndroidPlaybackServiceSavedSession("source", session)

    override fun coverArtUrl(track: Track): String? = artworkUrl
}
