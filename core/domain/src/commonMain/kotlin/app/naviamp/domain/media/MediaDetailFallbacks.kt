package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.popular.SimilarArtistMatch

const val ArtistDetailPopularTracksFetchLimit = 25
const val ArtistDetailPopularTracksDisplayLimit = 10
const val ArtistDetailSimilarArtistsFetchLimit = 20
const val ArtistDetailSimilarArtistsDisplayLimit = 10

data class ArtistPopularTracksUpdate(
    val tracks: List<Track>,
    val status: String?,
)

data class SimilarArtistsUpdate(
    val artists: List<SimilarArtistMatch>,
    val status: String?,
)

fun albumDetailsFromLibraryTracks(
    albumId: AlbumId,
    fallbackTitle: String?,
    fallbackArtistName: String?,
    tracks: List<Track>,
): AlbumDetails? {
    if (tracks.isEmpty()) return null
    val first = tracks.first()
    val album = Album(
        id = albumId,
        title = fallbackTitle ?: first.albumTitle ?: "Unknown Album",
        artistName = fallbackArtistName ?: first.artistName,
        coverArtId = first.coverArtId,
        recentlyAddedAtIso8601 = null,
        releaseYear = first.albumReleaseYear,
    )
    return AlbumDetails(album = album, tracks = tracks)
}

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

fun missingPopularTracksSourceStatus(): String =
    "Popular tracks unavailable: no connected media source."

fun loadingPopularTracksStatus(): String =
    "Loading popular tracks..."

fun artistPopularTracksUpdate(
    matches: List<ArtistPopularTrackMatch>,
    displayLimit: Int = ArtistDetailPopularTracksDisplayLimit,
): ArtistPopularTracksUpdate =
    ArtistPopularTracksUpdate(
        tracks = matches
            .map { it.matchedTrack }
            .take(displayLimit),
        status = if (matches.isEmpty()) {
            "No popular tracks matched songs in your library."
        } else {
            null
        },
    )

fun popularTracksUnavailableStatus(error: Throwable): String =
    "Popular tracks unavailable: ${error.message ?: "unknown error"}"

fun loadingSimilarArtistsStatus(): String =
    "Finding similar artists..."

fun similarArtistsUpdate(
    artists: List<SimilarArtistMatch>,
    displayLimit: Int = ArtistDetailSimilarArtistsDisplayLimit,
): SimilarArtistsUpdate =
    SimilarArtistsUpdate(
        artists = artists.take(displayLimit),
        status = if (artists.isEmpty()) "No similar artists found." else null,
    )

fun similarArtistsUnavailableStatus(error: Throwable): String =
    "Similar artists unavailable: ${error.message ?: "unknown error"}"

fun albumDetailLoadingStatus(fallbackTitle: String?): String =
    "Loading ${fallbackTitle ?: "album"}..."

fun albumDetailLoadedStatus(): String =
    "Connected."

fun albumDetailLoadErrorStatus(error: Throwable): String =
    error.message ?: "Could not load album."
