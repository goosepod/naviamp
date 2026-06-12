package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.ProviderId
import app.naviamp.domain.StreamRequest
import app.naviamp.domain.Genre
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.ConnectionValidation
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.ProviderCapabilities
import app.naviamp.domain.settings.RecentRadioKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadioRequestsTest {
    @Test
    fun createsLibraryGenreAndDecadeRadioRequests() {
        val library = libraryRadioRequest()
        assertEquals("Library radio", library.label)
        assertEquals(RecentRadioKind.Library, library.recentRadioStream.kind)

        val genre = genreRadioRequest(Genre("Shoegaze"))
        assertEquals("Shoegaze radio", genre.label)
        assertEquals("genre:Shoegaze", genre.recentRadioStream.id)
        assertEquals(RecentRadioKind.Genre, genre.recentRadioStream.kind)

        val decade = decadeRadioRequest(1980, 1989)
        assertEquals("1980-1989 radio", decade.label)
        assertEquals("decade:1980:1989", decade.recentRadioStream.id)
        assertEquals(RecentRadioKind.Decade, decade.recentRadioStream.kind)
    }

    @Test
    fun createsTrackAndPopularSeededRadioRequests() {
        val track = track("track-1", title = "Age of Consent", artistName = "New Order")
        val trackRequest = trackRadioRequest(track)
        assertEquals("Age of Consent radio", trackRequest.label)
        assertEquals(track, trackRequest.seedTrack)
        assertEquals("track:track-1", trackRequest.recentRadioStream.id)

        assertNull(popularTracksRadioRequest(emptyList(), seedLimit = 5))

        val tracks = listOf(
            track("one", artistName = "Slowdive"),
            track("two", artistName = "Slowdive"),
        )
        val popularRequest = assertNotNull(popularTracksRadioRequest(tracks, seedLimit = 5))
        assertTrue(popularRequest.seedTrack in tracks)
        assertEquals("${popularRequest.seedTrack.artistName} popular tracks radio", popularRequest.label)
        assertEquals(RecentRadioKind.Track, popularRequest.recentRadioStream.kind)
    }

    @Test
    fun createsEntitySeededRadioRequests() {
        val artist = Artist(id = ArtistId("artist-1"), name = "New Order")
        val album = Album(
            id = AlbumId("album-1"),
            title = "Power, Corruption & Lies",
            artistName = "New Order",
            coverArtId = "cover-1",
            recentlyAddedAtIso8601 = null,
            releaseYear = 1983,
        )
        val seedTrack = track("track-1", title = "Age of Consent", artistName = "New Order")

        val randomAlbum = randomAlbumSeededRadioRequest(album, seedTrack)
        assertEquals("Power, Corruption & Lies radio", randomAlbum.label)
        assertEquals(seedTrack, randomAlbum.seedTrack)
        assertEquals("random-album:album-1", randomAlbum.recentRadioStream.id)
        assertEquals(RecentRadioKind.RandomAlbum, randomAlbum.recentRadioStream.kind)

        val artistRadio = artistSeededRadioRequest(artist, seedTrack)
        assertEquals("New Order radio", artistRadio.label)
        assertEquals(seedTrack, artistRadio.seedTrack)
        assertEquals("artist:artist-1", artistRadio.recentRadioStream.id)
        assertEquals(RecentRadioKind.Artist, artistRadio.recentRadioStream.kind)

        val albumRadio = albumSeededRadioRequest(album, seedTrack)
        assertEquals("Power, Corruption & Lies radio", albumRadio.label)
        assertEquals(seedTrack, albumRadio.seedTrack)
        assertEquals("album:album-1", albumRadio.recentRadioStream.id)
        assertEquals(RecentRadioKind.Album, albumRadio.recentRadioStream.kind)
    }

    @Test
    fun radioRequestStartResultDeduplicatesTracksAndAddsCoverArtIds() = runTest {
        val service = RadioService(FakeRadioProvider())
        val request = RadioRequest(
            label = "Library radio",
            recentRadioStream = libraryRecentRadioStream(),
            loadTracks = {
                listOf(
                    track("track-1", albumTitle = "Album One", coverArtId = "cover-1"),
                    track("track-1", albumTitle = "Album One", coverArtId = "cover-1-duplicate"),
                    track("track-2", albumTitle = "Album Two", coverArtId = "cover-2"),
                )
            },
        )

        val result = assertIs<RadioRequestStartResult.Ready>(
            radioRequestStartResult(request, service, deduplicateTracks = true),
        )

        assertEquals(track("track-1", albumTitle = "Album One", coverArtId = "cover-1"), result.firstTrack)
        assertEquals(
            listOf(
                track("track-1", albumTitle = "Album One", coverArtId = "cover-1"),
                track("track-2", albumTitle = "Album Two", coverArtId = "cover-2"),
            ),
            result.queue,
        )
        assertEquals(listOf("cover-1", "cover-2"), result.recentRadioStream?.coverArtIds)
    }

    @Test
    fun radioRequestStartResultReturnsEmptyWhenNoTracksLoad() = runTest {
        val result = radioRequestStartResult(
            radioService = RadioService(FakeRadioProvider()),
            recentRadioStream = null,
            loadTracks = { emptyList() },
        )

        assertEquals(RadioRequestStartResult.Empty, result)
    }

    @Test
    fun radioRequestStartResultReturnsFailedWhenLoaderFails() = runTest {
        val error = IllegalStateException("radio failed")
        val result = radioRequestStartResult(
            radioService = RadioService(FakeRadioProvider()),
            recentRadioStream = null,
            loadTracks = { throw error },
        )

        assertEquals(error, assertIs<RadioRequestStartResult.Failed>(result).error)
    }

    @Test
    fun seededRadioBuildResultCreatesGeneratedQueueAndRecentStream() = runTest {
        val seedTrack = track("seed", albumTitle = "Seed Album", coverArtId = "seed-cover")
        val request = SeededRadioRequest(
            label = "Seed radio",
            seedTrack = seedTrack,
            recentRadioStream = trackRecentRadioStream(seedTrack),
            loadRest = {
                listOf(
                    track("seed", albumTitle = "Seed Album", coverArtId = "duplicate-seed-cover"),
                    track("similar", albumTitle = "Similar Album", coverArtId = "similar-cover"),
                )
            },
        )

        val result = assertIs<SeededRadioBuildResult.Ready>(
            seededRadioBuildResult(request, RadioService(FakeRadioProvider())),
        )

        assertEquals(
            listOf(seedTrack, track("similar", albumTitle = "Similar Album", coverArtId = "similar-cover")),
            result.queue,
        )
        assertEquals(listOf("seed-cover", "similar-cover"), result.recentRadioStream?.coverArtIds)
    }

    @Test
    fun seededRadioBuildResultReturnsFailedWhenLoaderFails() = runTest {
        val error = IllegalStateException("seeded radio failed")
        val result = seededRadioBuildResult(
            seedTrack = track("seed"),
            recentRadioStream = null,
            radioService = RadioService(FakeRadioProvider()),
            loadRest = { throw error },
        )

        assertEquals(error, assertIs<SeededRadioBuildResult.Failed>(result).error)
    }

    @Test
    fun seededRadioExpansionResultReturnsTracksOrFailure() = runTest {
        val service = RadioService(FakeRadioProvider())
        val fetchedTracks = listOf(track("similar"))

        val ready = assertIs<SeededRadioExpansionResult.Ready>(
            seededRadioExpansionResult(service) { fetchedTracks },
        )
        assertEquals(fetchedTracks, ready.fetchedTracks)

        val error = IllegalStateException("expansion failed")
        val failed = assertIs<SeededRadioExpansionResult.Failed>(
            seededRadioExpansionResult(service) { throw error },
        )
        assertEquals(error, failed.error)
    }

    private fun track(
        id: String,
        title: String = "Track $id",
        artistName: String = "Artist",
        albumTitle: String = "Album",
        coverArtId: String? = null,
    ): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistName = artistName,
            albumTitle = albumTitle,
            durationSeconds = 180,
            coverArtId = coverArtId,
            audioInfo = null,
            replayGain = null,
        )

    private class FakeRadioProvider : MediaProvider {
        override val id: ProviderId = ProviderId("provider-one")
        override val displayName: String = "Provider One"
        override val capabilities: ProviderCapabilities = ProviderCapabilities(
            supportsStreamingTranscode = true,
            supportsDownloadTranscode = true,
            supportsArtistRadio = true,
            supportsAlbumRadio = true,
            supportsTrackRadio = true,
        )

        override suspend fun validateConnection(): ConnectionValidation =
            error("unused")

        override suspend fun recentlyAddedAlbums(limit: Int): List<Album> =
            error("unused")

        override suspend fun album(albumId: AlbumId): AlbumDetails =
            error("unused")

        override suspend fun artist(artistId: ArtistId): ArtistDetails =
            error("unused")

        override suspend fun artists(limit: Int): List<Artist> =
            error("unused")

        override suspend fun tracks(limit: Int): List<Track> =
            error("unused")

        override suspend fun search(query: String, limit: Int): MediaSearchResults =
            error("unused")

        override suspend fun streamUrl(request: StreamRequest): String =
            error("unused")

        override fun coverArtUrl(coverArtId: String): String =
            error("unused")
    }
}
