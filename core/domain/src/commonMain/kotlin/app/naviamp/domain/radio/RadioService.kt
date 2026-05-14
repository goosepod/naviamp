package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.provider.MediaProvider

class RadioService(
    private val provider: MediaProvider,
    private val count: Int = DefaultRadioCount,
) {
    suspend fun albumSeed(album: Album, loadedTracks: List<Track> = emptyList()): Track? =
        loadedTracks.randomOrNull()
            ?: provider.album(album.id).tracks.randomOrNull()

    suspend fun artistSeed(artist: Artist, loadedAlbums: List<Album> = emptyList()): Track? {
        val albums = loadedAlbums.ifEmpty { provider.artist(artist.id).albums }
        return albums.shuffled().firstNotNullOfOrNull { album ->
            provider.album(album.id).tracks
                .filter { it.matchesArtist(artist.id, artist.name) }
                .randomOrNull()
        }
    }

    suspend fun albumRadio(albumId: AlbumId, fallbackTracks: List<Track> = emptyList()): List<Track> =
        provider.albumRadio(albumId, count = count).ifEmpty {
            fallbackTracks.ifEmpty { provider.album(albumId).tracks }.shuffled()
        }

    suspend fun artistRadio(artistId: ArtistId): List<Track> =
        provider.artistRadio(artistId, count = count)

    suspend fun trackRadio(trackId: TrackId): List<Track> =
        provider.trackRadio(trackId, count = count)

    suspend fun libraryRadio(): List<Track> =
        provider.randomSongs(limit = count)

    suspend fun genreRadio(genre: String): List<Track> =
        provider.randomSongs(limit = count, genre = genre)

    suspend fun decadeRadio(fromYear: Int, toYear: Int): List<Track> =
        provider.randomSongs(limit = count, fromYear = fromYear, toYear = toYear)

    fun queue(seedTrack: Track, fetchedTracks: List<Track>): List<Track> =
        (listOf(seedTrack) + fetchedTracks).distinctBy { it.id }

    private fun Track.matchesArtist(artistId: ArtistId, artistName: String): Boolean =
        this.artistId == artistId || this.artistName.equals(artistName, ignoreCase = true)

    private companion object {
        const val DefaultRadioCount = 50
    }
}
