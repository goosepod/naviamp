package app.naviamp.domain.radio

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track

sealed interface RadioSeedResult {
    data class Ready(
        val seedTrack: Track,
    ) : RadioSeedResult

    data object Missing : RadioSeedResult

    data class Failed(
        val error: Throwable,
    ) : RadioSeedResult
}

suspend fun radioSeedResult(
    loadSeed: suspend () -> Track?,
): RadioSeedResult =
    runCatching {
        loadSeed()?.let { seedTrack -> RadioSeedResult.Ready(seedTrack) }
            ?: RadioSeedResult.Missing
    }.getOrElse { error ->
        RadioSeedResult.Failed(error)
    }

suspend fun selectArtistRadioSeedTrack(
    artist: Artist,
    sourceId: String?,
    randomLibraryTrackForArtist: suspend (String, ArtistId) -> Track?,
    artistDetails: suspend () -> ArtistDetails,
    albumDetails: suspend (Album) -> AlbumDetails,
): Track? =
    runCatching {
        sourceId?.let { localSourceId ->
            randomLibraryTrackForArtist(localSourceId, artist.id)?.let { return@runCatching it }
        }
        artistDetails().albums.shuffled().forEach { album ->
            val tracks = albumDetails(album).tracks
            tracks.filter { it.artistId == artist.id }
                .randomOrNull()
                ?.let { return@runCatching it }
            tracks.filter { it.artistName.equals(artist.name, ignoreCase = true) }
                .randomOrNull()
                ?.let { return@runCatching it }
        }
        null
    }.getOrNull()

suspend fun selectAlbumRadioSeedTrack(
    album: Album,
    sourceId: String?,
    loadedAlbumTracks: List<Track> = emptyList(),
    randomLibraryTrackForAlbum: suspend (String, AlbumId) -> Track?,
    albumDetails: suspend () -> AlbumDetails,
): Track? =
    runCatching {
        loadedAlbumTracks.randomOrNull()?.let { return@runCatching it }
        sourceId?.let { localSourceId ->
            randomLibraryTrackForAlbum(localSourceId, album.id)?.let { return@runCatching it }
        }
        albumDetails().tracks.randomOrNull()
    }.getOrNull()
