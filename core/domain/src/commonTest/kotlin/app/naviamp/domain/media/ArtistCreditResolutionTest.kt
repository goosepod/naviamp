package app.naviamp.domain.media

import app.naviamp.domain.ArtistCredit
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.resolvedArtistCredits
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistCreditResolutionTest {
    @Test
    fun removesAggregateDisplayArtistWhenStructuredContributorsArePresent() {
        val track = track(
            artistName = "Snoop Dogg featuring Pharrell Williams",
            artistCredits = listOf(
                ArtistCredit(ArtistId("aggregate"), "Snoop Dogg featuring Pharrell Williams"),
                ArtistCredit(ArtistId("snoop"), "Snoop Dogg"),
                ArtistCredit(ArtistId("pharrell"), "Pharrell Williams"),
            ),
        )

        assertEquals(
            listOf("Snoop Dogg", "Pharrell Williams"),
            track.resolvedArtistCredits().map { it.name },
        )
    }

    @Test
    fun preservesARealArtistNameWhenItIsNotAnAggregateOfMultipleCredits() {
        val track = track(
            artistName = "Earth, Wind & Fire",
            artistCredits = listOf(
                ArtistCredit(ArtistId("earth-wind-fire"), "Earth, Wind & Fire"),
                ArtistCredit(ArtistId("guest"), "Guest Artist"),
            ),
        )

        assertEquals(
            listOf("Earth, Wind & Fire", "Guest Artist"),
            track.resolvedArtistCredits().map { it.name },
        )
    }
}

private fun track(
    artistName: String,
    artistCredits: List<ArtistCredit>,
) = Track(
    id = TrackId("track"),
    title = "Track",
    artistName = artistName,
    albumTitle = null,
    durationSeconds = null,
    coverArtId = null,
    audioInfo = null,
    replayGain = null,
    artistCredits = artistCredits,
)
