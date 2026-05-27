package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.popular.ArtistPopularTrackCandidate
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopMediaDetailsTest {
    @Test
    fun albumBackRouteUsesCurrentDetailRouteRules() {
        assertEquals(
            AppRoute.Home,
            resolveAlbumDetailBackRoute(
                currentRoute = AppRoute.Player,
                currentBackRoute = AppRoute.Search,
                lastContentRoute = AppRoute.Home,
                backRouteOverride = null,
            ),
        )
        assertEquals(
            AppRoute.ArtistDetail,
            resolveAlbumDetailBackRoute(
                currentRoute = AppRoute.ArtistDetail,
                currentBackRoute = AppRoute.Home,
                lastContentRoute = AppRoute.Search,
                backRouteOverride = null,
            ),
        )
        assertEquals(
            AppRoute.Search,
            resolveAlbumDetailBackRoute(
                currentRoute = AppRoute.Home,
                currentBackRoute = AppRoute.Home,
                lastContentRoute = AppRoute.Home,
                backRouteOverride = AppRoute.Search,
            ),
        )
    }

    @Test
    fun artistNavigationPushesCurrentArtistWhenOpeningNestedArtist() {
        val currentArtist = artist("current")
        val nextArtist = artist("next")

        assertEquals(
            ArtistDetailNavigation(
                backStack = listOf(currentArtist),
                backRoute = AppRoute.Home,
            ),
            artistDetailNavigation(
                artist = nextArtist,
                currentArtist = currentArtist,
                currentRoute = AppRoute.ArtistDetail,
                currentBackStack = emptyList(),
                currentBackRoute = AppRoute.Home,
                lastContentRoute = AppRoute.Search,
                backRouteOverride = null,
                pushCurrentArtist = true,
            ),
        )
    }

    @Test
    fun artistNavigationClearsStackWhenEnteringFromNonArtistRoute() {
        assertEquals(
            ArtistDetailNavigation(
                backStack = emptyList(),
                backRoute = AppRoute.Search,
            ),
            artistDetailNavigation(
                artist = artist("next"),
                currentArtist = artist("current"),
                currentRoute = AppRoute.Search,
                currentBackStack = listOf(artist("old")),
                currentBackRoute = AppRoute.Home,
                lastContentRoute = AppRoute.Home,
                backRouteOverride = null,
                pushCurrentArtist = true,
            ),
        )
    }

    @Test
    fun trackArtistAndAlbumReturnNullWhenIdsAreMissing() {
        val track = track()

        assertNull(trackArtist(track))
        assertNull(trackAlbum(track))
    }

    @Test
    fun trackArtistAndAlbumMapTrackMetadata() {
        val track = track(
            artistId = ArtistId("artist"),
            albumId = AlbumId("album"),
            albumTitle = "Album",
            releaseYear = 2024,
        )

        assertEquals(Artist(ArtistId("artist"), "Artist"), trackArtist(track))
        assertEquals(
            Album(
                id = AlbumId("album"),
                title = "Album",
                artistName = "Artist",
                coverArtId = "cover",
                recentlyAddedAtIso8601 = null,
                releaseYear = 2024,
            ),
            trackAlbum(track),
        )
    }

    @Test
    fun popularTracksUpdateLimitsTracksAndReportsEmptyMatches() {
        val matches = listOf(match("one"), match("two"), match("three"))

        assertEquals(
            ArtistPopularTracksUpdate(
                tracks = matches.take(2).map { it.matchedTrack },
                status = null,
            ),
            artistPopularTracksUpdate(matches, displayLimit = 2),
        )
        assertEquals(
            ArtistPopularTracksUpdate(
                tracks = emptyList(),
                status = "No popular tracks matched songs in your library.",
            ),
            artistPopularTracksUpdate(emptyList(), displayLimit = 2),
        )
    }

    @Test
    fun detailStatusHelpersUseFallbackMessages() {
        assertEquals("Could not load album.", albumLoadErrorStatus(Exception()))
        assertEquals("Could not load artist.", artistLoadErrorStatus(Exception()))
        assertEquals("Popular tracks unavailable: unknown error", popularTracksUnavailableStatus(Exception()))
        assertEquals(
            "Popular tracks unavailable: no connected media source.",
            missingPopularTracksSourceStatus(),
        )
        assertEquals("Loading popular tracks...", loadingPopularTracksStatus())
    }

    private fun artist(id: String): Artist =
        Artist(
            id = ArtistId(id),
            name = "Artist $id",
        )

    private fun track(
        artistId: ArtistId? = null,
        albumId: AlbumId? = null,
        albumTitle: String? = null,
        releaseYear: Int? = null,
    ): Track =
        Track(
            id = TrackId("track"),
            title = "Track",
            artistId = artistId,
            artistName = "Artist",
            albumId = albumId,
            albumTitle = albumTitle,
            albumReleaseYear = releaseYear,
            durationSeconds = 120,
            coverArtId = "cover",
            audioInfo = null,
            replayGain = null,
        )

    private fun match(id: String): ArtistPopularTrackMatch =
        ArtistPopularTrackMatch(
            candidate = ArtistPopularTrackCandidate(
                source = "test",
                sourceTrackId = id,
                rank = 1,
                title = "Track $id",
            ),
            matchedTrack = track().copy(id = TrackId(id), title = "Track $id"),
            fetchedAtEpochMillis = 1L,
        )
}
