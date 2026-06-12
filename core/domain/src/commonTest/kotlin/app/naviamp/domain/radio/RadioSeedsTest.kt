package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RadioSeedsTest {
    @Test
    fun artistSeedPrefersLibraryTrackWhenAvailable() = runBlocking {
        val artist = artist()
        val libraryTrack = track("library", artistId = artist.id, artistName = artist.name)

        val seed = selectArtistRadioSeedTrack(
            artist = artist,
            sourceId = "source-1",
            randomLibraryTrackForArtist = { _, _ -> libraryTrack },
            artistDetails = { error("Should not load artist details when library seed exists.") },
            albumDetails = { error("Should not load album details when library seed exists.") },
        )

        assertEquals(libraryTrack, seed)
    }

    @Test
    fun artistSeedFallsBackToMatchingAlbumTrack() = runBlocking {
        val artist = artist()
        val album = album()
        val matchingByName = track("name-match", artistName = artist.name.lowercase())

        val seed = selectArtistRadioSeedTrack(
            artist = artist,
            sourceId = null,
            randomLibraryTrackForArtist = { _, _ -> null },
            artistDetails = { ArtistDetails(artist = artist, albums = listOf(album), info = null) },
            albumDetails = { AlbumDetails(album = album, tracks = listOf(matchingByName)) },
        )

        assertEquals(matchingByName, seed)
    }

    @Test
    fun albumSeedPrefersLoadedThenLibraryThenAlbumTracks() = runBlocking {
        val album = album()
        val loadedTrack = track("loaded")
        val libraryTrack = track("library")
        val albumTrack = track("album")

        assertEquals(
            loadedTrack,
            selectAlbumRadioSeedTrack(
                album = album,
                sourceId = "source-1",
                loadedAlbumTracks = listOf(loadedTrack),
                randomLibraryTrackForAlbum = { _, _ -> libraryTrack },
                albumDetails = { AlbumDetails(album = album, tracks = listOf(albumTrack)) },
            ),
        )
        assertEquals(
            libraryTrack,
            selectAlbumRadioSeedTrack(
                album = album,
                sourceId = "source-1",
                loadedAlbumTracks = emptyList(),
                randomLibraryTrackForAlbum = { _, _ -> libraryTrack },
                albumDetails = { AlbumDetails(album = album, tracks = listOf(albumTrack)) },
            ),
        )
        assertEquals(
            albumTrack,
            selectAlbumRadioSeedTrack(
                album = album,
                sourceId = null,
                loadedAlbumTracks = emptyList(),
                randomLibraryTrackForAlbum = { _, _ -> null },
                albumDetails = { AlbumDetails(album = album, tracks = listOf(albumTrack)) },
            ),
        )
    }

    @Test
    fun radioSeedResultClassifiesReadyMissingAndFailed() = runBlocking {
        val seedTrack = track("seed")
        assertEquals(
            RadioSeedResult.Ready(seedTrack),
            radioSeedResult { seedTrack },
        )
        assertEquals(
            RadioSeedResult.Missing,
            radioSeedResult { null },
        )

        val error = IllegalStateException("seed failed")
        val failed = assertIs<RadioSeedResult.Failed>(
            radioSeedResult { throw error },
        )
        assertEquals(error, failed.error)
    }

    private fun artist(): Artist =
        Artist(id = ArtistId("artist-1"), name = "New Order")

    private fun album(): Album =
        Album(
            id = AlbumId("album-1"),
            title = "Power, Corruption & Lies",
            artistName = "New Order",
            coverArtId = null,
            recentlyAddedAtIso8601 = null,
            releaseYear = 1983,
        )

    private fun track(
        id: String,
        artistId: ArtistId? = null,
        artistName: String = "New Order",
    ): Track =
        Track(
            id = TrackId(id),
            title = "Track $id",
            artistId = artistId,
            artistName = artistName,
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = null,
            audioInfo = null,
            replayGain = null,
        )
}
