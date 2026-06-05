package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.media.albumDetailLoadErrorStatus
import app.naviamp.domain.media.albumDetailsFromLibraryTracks
import app.naviamp.domain.media.artistDetailLoadErrorStatus
import app.naviamp.domain.media.artistDetailsFromLibraryTracks
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider

data class ArtistDetailNavigation(
    val backStack: List<Artist>,
    val backRoute: DesktopAppRoute,
)

fun resolveAlbumDetailBackRoute(
    currentRoute: DesktopAppRoute,
    currentBackRoute: DesktopAppRoute,
    lastContentRoute: DesktopAppRoute,
    backRouteOverride: DesktopAppRoute?,
): DesktopAppRoute =
    backRouteOverride ?: when (currentRoute) {
        DesktopAppRoute.AlbumDetail -> currentBackRoute
        DesktopAppRoute.ArtistDetail -> DesktopAppRoute.ArtistDetail
        DesktopAppRoute.Player -> lastContentRoute
        else -> currentRoute
    }

fun artistDetailNavigation(
    artist: Artist,
    currentArtist: Artist?,
    currentRoute: DesktopAppRoute,
    currentBackStack: List<Artist>,
    currentBackRoute: DesktopAppRoute,
    lastContentRoute: DesktopAppRoute,
    backRouteOverride: DesktopAppRoute?,
    pushCurrentArtist: Boolean,
): ArtistDetailNavigation {
    val backStack = if (pushCurrentArtist && currentRoute == DesktopAppRoute.ArtistDetail) {
        currentArtist
            ?.takeIf { it.id != artist.id }
            ?.let { currentBackStack + it }
            ?: currentBackStack
    } else if (currentRoute != DesktopAppRoute.ArtistDetail) {
        emptyList()
    } else {
        currentBackStack
    }
    val backRoute = backRouteOverride ?: when (currentRoute) {
        DesktopAppRoute.ArtistDetail -> currentBackRoute
        DesktopAppRoute.Player -> lastContentRoute
        else -> currentRoute
    }
    return ArtistDetailNavigation(
        backStack = backStack,
        backRoute = backRoute,
    )
}

suspend fun loadAlbumDetails(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    album: Album,
    sourceId: String?,
): AlbumDetails =
    runCatching {
        providerResponseService.album(provider, album.id)
    }.recoverCatching { error ->
        val fallbackDetail = sourceId?.let {
            albumDetailsFromLibraryTracks(
                albumId = album.id,
                fallbackTitle = album.title,
                fallbackArtistName = album.artistName,
                tracks = libraryIndexRepository.libraryTracksForAlbum(it, album.id, limit = 1_000),
            )
        }
        fallbackDetail ?: throw error
    }.getOrThrow()

suspend fun loadArtistDetails(
    libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseService: ProviderResponseService,
    provider: MediaProvider,
    artist: Artist,
    sourceId: String?,
): ArtistDetails =
    runCatching {
        providerResponseService.artist(provider, artist.id)
    }.recoverCatching { error ->
        val fallbackDetail = sourceId?.let {
            artistDetailsFromLibraryTracks(
                artistId = artist.id,
                fallbackName = artist.name,
                tracks = libraryIndexRepository.libraryTracksForArtist(it, artist.id, limit = 1_000),
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

fun albumLoadErrorStatus(error: Throwable): String =
    albumDetailLoadErrorStatus(error)

fun artistLoadErrorStatus(error: Throwable): String =
    artistDetailLoadErrorStatus(error)
