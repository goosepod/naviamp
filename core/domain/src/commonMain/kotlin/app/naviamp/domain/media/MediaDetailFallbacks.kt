package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.ArtistId
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.popular.SimilarArtistMatch
import app.naviamp.domain.provider.MediaProvider

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

suspend fun loadArtistDetails(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    artist: Artist,
    sourceId: String?,
): ArtistDetails =
    loadArtistDetails(
        libraryIndexRepository = libraryIndexRepository,
        providerResponseService = providerResponseService,
        provider = provider,
        artistId = artist.id,
        fallbackName = artist.name,
        sourceId = sourceId,
    )

suspend fun loadArtistDetails(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    artistId: ArtistId,
    fallbackName: String?,
    sourceId: String?,
): ArtistDetails =
    runCatching {
        providerResponseService.artist(provider, artistId)
    }.recoverCatching { error ->
        val fallbackDetail = sourceId?.let {
            artistDetailsFromLibraryTracks(
                artistId = artistId,
                fallbackName = fallbackName,
                tracks = libraryIndexRepository.libraryTracksForArtist(it, artistId, limit = 1_000),
            )
        }
        fallbackDetail ?: throw error
    }.getOrThrow()

fun trackArtist(track: Track): Artist? =
    track.artistId?.let { artistId ->
        Artist(
            id = artistId,
            name = track.artistName,
        )
    }

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

suspend fun loadArtistPopularTracksUpdate(
    sourceId: String?,
    artist: Artist,
    fetchLimit: Int = ArtistDetailPopularTracksFetchLimit,
    displayLimit: Int = ArtistDetailPopularTracksDisplayLimit,
    loadPopularTracks: suspend (sourceId: String, artist: Artist, limit: Int) -> List<ArtistPopularTrackMatch>,
): ArtistPopularTracksUpdate {
    val activeSourceId = sourceId ?: return ArtistPopularTracksUpdate(
        tracks = emptyList(),
        status = missingPopularTracksSourceStatus(),
    )
    return runCatching {
        artistPopularTracksUpdate(
            matches = loadPopularTracks(activeSourceId, artist, fetchLimit),
            displayLimit = displayLimit,
        )
    }.getOrElse { error ->
        ArtistPopularTracksUpdate(
            tracks = emptyList(),
            status = popularTracksUnavailableStatus(error),
        )
    }
}

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

suspend fun loadSimilarArtistsUpdate(
    artistName: String,
    fetchLimit: Int = ArtistDetailSimilarArtistsFetchLimit,
    displayLimit: Int = ArtistDetailSimilarArtistsDisplayLimit,
    loadSimilarArtists: suspend (artistName: String, limit: Int) -> List<SimilarArtistMatch>,
): SimilarArtistsUpdate =
    runCatching {
        similarArtistsUpdate(
            artists = loadSimilarArtists(artistName, fetchLimit),
            displayLimit = displayLimit,
        )
    }.getOrElse { error ->
        SimilarArtistsUpdate(
            artists = emptyList(),
            status = similarArtistsUnavailableStatus(error),
        )
    }

fun albumDetailLoadingStatus(fallbackTitle: String?): String =
    "Loading ${fallbackTitle ?: "album"}..."

fun albumDetailLoadedStatus(): String =
    "Connected."

fun albumDetailLoadErrorStatus(error: Throwable): String =
    error.message ?: "Could not load album."

suspend fun loadAlbumDetails(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    album: Album,
    sourceId: String?,
): AlbumDetails =
    loadAlbumDetails(
        libraryIndexRepository = libraryIndexRepository,
        providerResponseService = providerResponseService,
        provider = provider,
        albumId = album.id,
        fallbackTitle = album.title,
        fallbackArtistName = album.artistName,
        sourceId = sourceId,
    )

suspend fun loadAlbumDetails(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    albumId: AlbumId,
    fallbackTitle: String?,
    fallbackArtistName: String?,
    sourceId: String?,
): AlbumDetails =
    runCatching {
        providerResponseService.album(provider, albumId)
    }.recoverCatching { error ->
        val fallbackDetail = sourceId?.let {
            albumDetailsFromLibraryTracks(
                albumId = albumId,
                fallbackTitle = fallbackTitle,
                fallbackArtistName = fallbackArtistName,
                tracks = libraryIndexRepository.libraryTracksForAlbum(it, albumId, limit = 1_000),
            )
        }
        fallbackDetail ?: throw error
    }.getOrThrow()

fun trackAlbum(track: Track): Album? =
    track.albumId?.let { albumId ->
        Album(
            id = albumId,
            title = track.albumTitle ?: "Album",
            artistName = track.artistName,
            coverArtId = track.coverArtId,
            recentlyAddedAtIso8601 = null,
            releaseYear = track.albumReleaseYear,
        )
    }
