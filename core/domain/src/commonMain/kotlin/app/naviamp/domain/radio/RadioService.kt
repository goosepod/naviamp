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
    private val tuning: RadioTuningSettings = RadioTuningSettings(),
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
        provider.albumRadio(albumId, count = fetchCount).ifEmpty {
            fallbackTracks.ifEmpty { albumTracks(albumId) }.shuffled()
        }.let { tracks -> tunedRadioTracks(seedTrack = tracks.firstOrNull(), tracks = tracks, tuning = tuning, targetCount = count) }

    suspend fun artistRadio(artistId: ArtistId): List<Track> =
        provider.artistRadio(artistId, count = fetchCount)
            .let { tracks -> tunedRadioTracks(seedTrack = tracks.firstOrNull(), tracks = tracks, tuning = tuning, targetCount = count) }

    suspend fun trackRadio(trackId: TrackId): List<Track> =
        provider.trackRadio(trackId, count = fetchCount)
            .let { tracks -> tunedRadioTracks(seedTrack = tracks.firstOrNull(), tracks = tracks, tuning = tuning, targetCount = count) }

    suspend fun trackRadio(
        seedTrack: Track,
        preferSonicSimilarity: Boolean,
    ): List<Track> {
        if (preferSonicSimilarity && provider.capabilities.supportsSonicSimilarity) {
            val sonicTracks = provider.sonicSimilarTracks(seedTrack.id, count = fetchCount)
                .filterNot { track -> track.id == seedTrack.id }
            if (sonicTracks.isNotEmpty()) {
                return tunedTrackRadio(seedTrack, sonicTracks)
            }
        }
        return provider.trackRadio(seedTrack.id, count = fetchCount)
            .let { tracks -> tunedTrackRadio(seedTrack, tracks) }
    }

    suspend fun libraryRadio(): List<Track> =
        provider.randomSongs(limit = fetchCount)
            .let { tracks -> tunedRadioTracks(seedTrack = null, tracks = tracks, tuning = tuning, targetCount = count) }

    suspend fun genreRadio(genre: String): List<Track> =
        provider.randomSongs(limit = fetchCount, genre = genre)
            .let { tracks -> tunedRadioTracks(seedTrack = null, tracks = tracks, tuning = tuning, targetCount = count) }

    suspend fun decadeRadio(fromYear: Int, toYear: Int): List<Track> =
        provider.randomSongs(limit = fetchCount, fromYear = fromYear, toYear = toYear)
            .let { tracks -> tunedRadioTracks(seedTrack = null, tracks = tracks, tuning = tuning, targetCount = count) }

    fun queue(seedTrack: Track, fetchedTracks: List<Track>): List<Track> =
        generatedRadioQueue(seedTrack, fetchedTracks)

    private suspend fun albumTracks(albumId: AlbumId): List<Track> =
        providerResponseService?.album(provider, albumId)?.tracks
            ?: provider.album(albumId).tracks

    private suspend fun tunedTrackRadio(seedTrack: Track, tracks: List<Track>): List<Track> {
        val candidateTracks = if (tuning.artistRunMode == RadioArtistRunMode.Mixed) {
            tracks
        } else {
            sameArtistTracks(seedTrack) + tracks
        }
        return tunedRadioTracks(
            seedTrack = seedTrack,
            tracks = candidateTracks.filterNot { it.id == seedTrack.id },
            tuning = tuning,
            targetCount = count,
        )
    }

    private suspend fun sameArtistTracks(seedTrack: Track): List<Track> {
        val artistId = seedTrack.artistId ?: return emptyList()
        val albums = runCatching {
            providerResponseService?.artist(provider, artistId)?.albums
                ?: provider.artist(artistId).albums
        }.getOrElse { return emptyList() }
        return albums
            .flatMap { album -> runCatching { albumTracks(album.id) }.getOrDefault(emptyList()) }
            .filter { track -> track.matchesArtist(artistId, seedTrack.artistName) && track.id != seedTrack.id }
            .distinctBy { it.id }
    }

    private val fetchCount: Int
        get() = when (tuning) {
            RadioTuningSettings() -> count
            else -> (count * 3).coerceAtLeast(count)
        }

    private fun Track.matchesArtist(artistId: ArtistId, artistName: String): Boolean =
        if (this.artistId != null) {
            this.artistId == artistId
        } else {
            this.artistName.equals(artistName, ignoreCase = true)
        }

    private companion object {
        const val DefaultRadioCount = 50
    }
}
