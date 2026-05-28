package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track

fun artistDetailsFromLibraryTracks(
    artistId: ArtistId,
    fallbackName: String?,
    tracks: List<Track>,
): ArtistDetails? {
    if (tracks.isEmpty()) {
        return fallbackName?.let { name ->
            ArtistDetails(artist = Artist(artistId, name), albums = emptyList(), info = null)
        }
    }
    val artistName = fallbackName ?: tracks.firstOrNull()?.artistName ?: "Unknown Artist"
    val albums = tracks
        .groupBy { track -> track.albumId?.value ?: track.albumTitle.orEmpty() }
        .mapNotNull { (_, albumTracks) ->
            val first = albumTracks.firstOrNull() ?: return@mapNotNull null
            val albumId = first.albumId ?: return@mapNotNull null
            Album(
                id = albumId,
                title = first.albumTitle ?: "Unknown Album",
                artistName = first.artistName,
                coverArtId = first.coverArtId,
                recentlyAddedAtIso8601 = null,
                releaseYear = first.albumReleaseYear,
            )
        }
        .sortedWith(compareBy<Album> { it.releaseYear ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() })
    return ArtistDetails(
        artist = Artist(artistId, artistName),
        albums = albums,
        info = null,
    )
}

fun artistDetailLoadingStatus(fallbackName: String?): String =
    "Loading ${fallbackName ?: "artist"}..."

fun artistDetailLoadedStatus(detail: ArtistDetails): String =
    if (detail.albums.isEmpty()) {
        "No albums found for ${detail.artist.name}."
    } else {
        "Connected."
    }

fun artistDetailLoadErrorStatus(error: Throwable): String =
    error.message ?: "Could not load artist."
