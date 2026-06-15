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
private const val LocalArtistAlbumIdMarker = ":local-album:"

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
            val albumId = first.albumId
                ?: localArtistAlbumId(artistId, first.albumTitle, first.artistName)
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
            .withLocalAlbumsWhenEmpty(libraryIndexRepository, sourceId, artistId, fallbackName)
    }.recoverCatching { error ->
        val fallbackDetail = sourceId?.let {
            artistDetailsFromLibraryTracks(
                artistId = artistId,
                fallbackName = fallbackName,
                tracks = libraryIndexRepository.artistDetailFallbackTracks(it, artistId, fallbackName),
            )
        }
        fallbackDetail ?: throw error
    }.getOrThrow()

private fun localArtistAlbumId(artistId: ArtistId, albumTitle: String?, artistName: String): AlbumId =
    AlbumId(
        "artist:${artistId.value}$LocalArtistAlbumIdMarker" +
            "${(albumTitle ?: "unknown-album").lowercase()}:${artistName.lowercase()}",
    )

private fun ArtistDetails.withLocalAlbumsWhenEmpty(
    libraryIndexRepository: LocalLibraryIndexRepository,
    sourceId: String?,
    artistId: ArtistId,
    fallbackName: String?,
): ArtistDetails {
    if (albums.isNotEmpty() || sourceId == null) return this
    val localDetail = artistDetailsFromLibraryTracks(
        artistId = artistId,
        fallbackName = artist.name.takeIf { it.isNotBlank() } ?: fallbackName,
        tracks = libraryIndexRepository.artistDetailFallbackTracks(
            sourceId = sourceId,
            artistId = artistId,
            fallbackName = fallbackName ?: artist.name,
        ),
    ) ?: return this
    return copy(albums = localDetail.albums)
}

private fun LocalLibraryIndexRepository.artistDetailFallbackTracks(
    sourceId: String,
    artistId: ArtistId,
    fallbackName: String?,
): List<Track> {
    val idMatches = libraryTracksForArtist(sourceId, artistId, limit = 1_000)
    if (idMatches.isNotEmpty()) return idMatches
    return fallbackName
        ?.takeIf { it.isNotBlank() }
        ?.let { libraryTracksForArtistName(sourceId, it, limit = 1_000) }
        .orEmpty()
}

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
    artist: Artist,
    fetchLimit: Int = ArtistDetailSimilarArtistsFetchLimit,
    displayLimit: Int = ArtistDetailSimilarArtistsDisplayLimit,
    loadSimilarArtists: suspend (artist: Artist, limit: Int) -> List<SimilarArtistMatch>,
): SimilarArtistsUpdate =
    runCatching {
        similarArtistsUpdate(
            artists = loadSimilarArtists(artist, fetchLimit),
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
                tracks = libraryIndexRepository.albumDetailFallbackTracks(
                    sourceId = it,
                    albumId = albumId,
                    fallbackTitle = fallbackTitle,
                    fallbackArtistName = fallbackArtistName,
                ),
            )
        }
        fallbackDetail ?: throw error
    }.getOrThrow()

private fun LocalLibraryIndexRepository.albumDetailFallbackTracks(
    sourceId: String,
    albumId: AlbumId,
    fallbackTitle: String?,
    fallbackArtistName: String?,
): List<Track> {
    val albumMatches = libraryTracksForAlbum(sourceId, albumId, limit = 1_000)
    if (albumMatches.isNotEmpty() || !albumId.value.contains(LocalArtistAlbumIdMarker)) return albumMatches
    val artistName = fallbackArtistName?.takeIf { it.isNotBlank() } ?: return emptyList()
    val albumTitle = fallbackTitle ?: "Unknown Album"
    return libraryTracksForArtistName(sourceId, artistName, limit = 1_000)
        .filter { track -> track.albumId == null && (track.albumTitle ?: "Unknown Album") == albumTitle }
}

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
