package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider

fun generatedRadioQueue(seedTrack: Track, fetchedTracks: List<Track>): List<Track> =
    (listOf(seedTrack) + fetchedTracks).distinctBy { it.id }

class RadioService(
    private val provider: MediaProvider,
    private val count: Int = DefaultRadioCount,
    private val providerResponseService: ProviderResponseService? = null,
) {
    suspend fun albumSeed(album: Album, loadedTracks: List<Track> = emptyList()): Track? =
        loadedTracks.randomOrNull()
            ?: albumTracks(album.id).randomOrNull()

    suspend fun artistSeed(artist: Artist, loadedAlbums: List<Album> = emptyList()): Track? {
        val albums = loadedAlbums.ifEmpty {
            providerResponseService?.artist(provider, artist.id)?.albums
                ?: provider.artist(artist.id).albums
        }
        return albums.shuffled().firstNotNullOfOrNull { album ->
            albumTracks(album.id)
                .filter { it.matchesArtist(artist.id, artist.name) }
                .randomOrNull()
        }
    }

    suspend fun albumRadio(albumId: AlbumId, fallbackTracks: List<Track> = emptyList()): List<Track> =
        provider.albumRadio(albumId, count = count).ifEmpty {
            fallbackTracks.ifEmpty { albumTracks(albumId) }.shuffled()
        }

    suspend fun artistRadio(artistId: ArtistId): List<Track> =
        provider.artistRadio(artistId, count = count)

    suspend fun trackRadio(trackId: TrackId): List<Track> =
        provider.trackRadio(trackId, count = count)

    suspend fun trackRadio(
        seedTrack: Track,
        preferSonicSimilarity: Boolean,
    ): List<Track> {
        if (preferSonicSimilarity && provider.capabilities.supportsSonicSimilarity) {
            val sonicTracks = provider.sonicSimilarTracks(seedTrack.id, count = count)
                .filterNot { track -> track.id == seedTrack.id }
            if (sonicTracks.isNotEmpty()) return sonicTracks
        }
        return trackRadio(seedTrack.id)
    }

    suspend fun libraryRadio(): List<Track> =
        provider.randomSongs(limit = count)

    suspend fun genreRadio(genre: String): List<Track> =
        provider.randomSongs(limit = count, genre = genre)

    suspend fun decadeRadio(fromYear: Int, toYear: Int): List<Track> =
        provider.randomSongs(limit = count, fromYear = fromYear, toYear = toYear)

    fun queue(seedTrack: Track, fetchedTracks: List<Track>): List<Track> =
        generatedRadioQueue(seedTrack, fetchedTracks)

    private suspend fun albumTracks(albumId: AlbumId): List<Track> =
        providerResponseService?.album(provider, albumId)?.tracks
            ?: provider.album(albumId).tracks

    private fun Track.matchesArtist(artistId: ArtistId, artistName: String): Boolean =
        this.artistId == artistId || this.artistName.equals(artistName, ignoreCase = true)

    private companion object {
        const val DefaultRadioCount = 50
    }
}
