package app.naviamp.ui

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ArtistInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaUiMappersTest {
    @Test
    fun downloadedTrackUiUsesSharedTrackRowMapping() {
        val track = Track(
            id = TrackId("track-1"),
            title = "Track One",
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 125,
            coverArtId = "cover-1",
            audioInfo = null,
            replayGain = null,
        )

        val ui = track.toDownloadedTrackUi(
            id = "/downloads/track-1.mp3",
            sizeBytes = 12_345L,
            coverArtUrl = { coverArtId -> coverArtId?.let { "cover://$it" } },
        )

        assertEquals("/downloads/track-1.mp3", ui.id)
        assertEquals("track-1", ui.track.id)
        assertEquals("Track One", ui.track.title)
        assertEquals("Artist - Album", ui.track.subtitle)
        assertEquals("2:05", ui.track.meta)
        assertEquals("cover://cover-1", ui.track.coverArtUrl)
        assertEquals(12_345L, ui.sizeBytes)
    }

    @Test
    fun artistDetailUiPrefersArtistInfoImage() {
        val ui = ArtistDetails(
            artist = Artist(ArtistId("artist-1"), "Artist One"),
            albums = emptyList(),
            info = ArtistInfo(
                biography = "Artist biography",
                smallImageUrl = "https://images.test/small.jpg",
                mediumImageUrl = "https://images.test/medium.jpg",
                largeImageUrl = "https://images.test/large.jpg",
            ),
        ).toSharedArtistDetailUi(
            coverArtUrl = { coverArtId -> coverArtId?.let { "cover://$it" } },
        )

        assertEquals("https://images.test/large.jpg", ui.artist.coverArtUrl)
        assertEquals("Artist biography", ui.biography)
    }

    @Test
    fun nowPlayingItemTargetResolvesQueueRelatedAndTrackIds() {
        val queueTrack = track("queue-track")
        val relatedTrack = track("related-track")
        val knownTrack = track("known-track")

        assertEquals(
            queueTrack,
            resolveNowPlayingItemTrack(
                item = item(nowPlayingQueueItemId(1)),
                queueTracks = listOf(track("before"), queueTrack),
            ),
        )
        assertEquals(
            relatedTrack,
            resolveNowPlayingItemTrack(
                item = item(nowPlayingRelatedItemId(0)),
                relatedTracks = listOf(relatedTrack),
            ),
        )
        assertEquals(
            knownTrack,
            resolveNowPlayingItemTrack(
                item = item("known-track"),
                knownTracks = listOf(knownTrack),
            ),
        )
        assertNull(
            resolveNowPlayingItemTrack(
                item = item(nowPlayingQueueItemId(4)),
                queueTracks = listOf(queueTrack),
            ),
        )
    }

    private fun item(id: String): NaviampNowPlayingItemUi =
        NaviampNowPlayingItemUi(id = id, title = id, subtitle = "")

    private fun track(id: String): Track =
        Track(
            id = TrackId(id),
            title = id,
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 125,
            coverArtId = "cover-1",
            audioInfo = null,
            replayGain = null,
        )
}
