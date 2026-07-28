package app.naviamp.domain.popular

import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistPopularTracksTest {
    @Test
    fun similarArtistFallsBackToProviderSearchWhenLocalIndexHasNoExactMatch() = runTest {
        val gorgonCity = Artist(ArtistId("gorgon-city"), "Gorgon City")
        var fallbackQueries = 0
        val service = SimilarArtistsService(
            libraryArtistsSearch = { _, _ -> listOf(Artist(ArtistId("gorgon-heights"), "Gorgon Heights")) },
            client = object : SimilarArtistsClient {
                override suspend fun similarArtists(artist: Artist, limit: Int) = listOf(
                    SimilarArtistCandidate("lastfm", "gorgon-city", "Gorgon City"),
                )
            },
            fallbackArtistsSearch = { query, _ ->
                fallbackQueries += 1
                listOf(gorgonCity).filter { it.name.equals(query, ignoreCase = true) }
            },
        )

        val match = service.similarArtists(Artist(ArtistId("camelphat"), "CamelPhat")).single()

        assertEquals(gorgonCity, match.matchedArtist)
        assertEquals(1, fallbackQueries)
    }

    @Test
    fun similarArtistUsesExactLocalMatchWithoutProviderFallback() = runTest {
        val gorgonCity = Artist(ArtistId("gorgon-city"), "Gorgon City")
        var fallbackQueries = 0
        val service = SimilarArtistsService(
            libraryArtistsSearch = { _, _ -> listOf(gorgonCity) },
            client = object : SimilarArtistsClient {
                override suspend fun similarArtists(artist: Artist, limit: Int) = listOf(
                    SimilarArtistCandidate("lastfm", "gorgon-city", "GORGON CITY"),
                )
            },
            fallbackArtistsSearch = { _, _ ->
                fallbackQueries += 1
                emptyList()
            },
        )

        val match = service.similarArtists(Artist(ArtistId("camelphat"), "CamelPhat")).single()

        assertEquals(gorgonCity, match.matchedArtist)
        assertEquals(0, fallbackQueries)
    }

    @Test
    fun matchesRemasteredPopularTrackToLibraryTitle() {
        val matches = matchPopularTracks(
            candidates = listOf(
                ArtistPopularTrackCandidate(
                    source = NavidromeAgentMetadataSource,
                    sourceTrackId = "source-1",
                    rank = 1,
                    title = "Symphony Of Destruction",
                    albumTitle = "Countdown To Extinction (Deluxe Edition)",
                    durationSeconds = 243,
                ),
            ),
            libraryTracks = listOf(
                track(
                    id = "local-1",
                    title = "Symphony Of Destruction (Remastered 2012)",
                    albumTitle = "Countdown To Extinction",
                    durationSeconds = 246,
                ),
            ),
        )

        assertEquals("local-1", matches["source-1"]?.id?.value)
    }

    @Test
    fun matchesPopularTrackWithSubtitleToPlainLibraryTitle() {
        val matches = matchPopularTracks(
            candidates = listOf(
                ArtistPopularTrackCandidate(
                    source = NavidromeAgentMetadataSource,
                    sourceTrackId = "source-2",
                    rank = 2,
                    title = "Holy Wars...The Punishment Due",
                    albumTitle = "Rust In Peace (2004 Remix / Expanded Edition)",
                    durationSeconds = 390,
                ),
            ),
            libraryTracks = listOf(
                track(
                    id = "local-2",
                    title = "Holy Wars... The Punishment Due",
                    albumTitle = "Rust in Peace",
                    durationSeconds = 393,
                ),
            ),
        )

        assertEquals("local-2", matches["source-2"]?.id?.value)
    }

    private fun track(
        id: String,
        title: String,
        albumTitle: String,
        durationSeconds: Int,
    ): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = "Megadeth",
            albumTitle = albumTitle,
            durationSeconds = durationSeconds,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
