package app.naviamp.domain.media

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistCredit
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtistNavigationTest {
    @Test
    fun clickedNameWithoutIdResolvesExactIndividualInsteadOfCombinedArtist() = runTest {
        val track = combinedTrack()

        val resolved = resolveTrackArtistNavigation(track, null, "David Guetta") { query, _ ->
            assertEquals("David Guetta", query)
            listOf(
                Artist(ArtistId("combined"), track.artistName),
                Artist(ArtistId("david-guetta"), "David Guetta"),
            )
        }

        assertEquals(Artist(ArtistId("david-guetta"), "David Guetta"), resolved)
    }

    @Test
    fun unresolvedClickedNameDoesNotFallBackToCombinedArtist() = runTest {
        val resolved = resolveTrackArtistNavigation(combinedTrack(), null, "David Guetta") { _, _ -> emptyList() }

        assertNull(resolved)
    }

    @Test
    fun combinedTrackIdAttachedToIndividualNameIsResolvedByName() = runTest {
        val resolved = resolveTrackArtistNavigation(
            combinedTrack(),
            requestedId = "combined",
            requestedName = "David Guetta",
        ) { _, _ -> listOf(Artist(ArtistId("david-guetta"), "David Guetta")) }

        assertEquals(Artist(ArtistId("david-guetta"), "David Guetta"), resolved)
    }

    @Test
    fun structuredIndividualIdAvoidsSearch() = runTest {
        val track = combinedTrack().copy(
            artistCredits = listOf(ArtistCredit(ArtistId("david-guetta"), "David Guetta")),
        )

        val resolved = resolveTrackArtistNavigation(track, "david-guetta", "David Guetta") { _, _ ->
            error("Search must not run for an identified credit")
        }

        assertEquals(Artist(ArtistId("david-guetta"), "David Guetta"), resolved)
    }

    private fun combinedTrack() = Track(
        id = TrackId("think-of-me"),
        title = "Think Of Me",
        artistId = ArtistId("combined"),
        artistName = "HUGEL, David Guetta, Kehlani, Daecolm",
        albumId = null,
        albumTitle = null,
        durationSeconds = null,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )
}
