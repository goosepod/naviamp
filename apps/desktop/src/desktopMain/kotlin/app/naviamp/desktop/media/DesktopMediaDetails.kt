package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistDetails
import app.naviamp.domain.Track
import app.naviamp.domain.media.artistDetailLoadErrorStatus
import app.naviamp.domain.media.artistDetailsFromLibraryTracks
import app.naviamp.domain.popular.ArtistPopularTrackMatch
import app.naviamp.domain.provider.MediaProvider

data class ArtistDetailNavigation(
    val backStack: List<Artist>,
    val backRoute: AppRoute,
)

data class ArtistPopularTracksUpdate(
    val tracks: List<Track>,
    val status: String?,
)

fun resolveAlbumDetailBackRoute(
    currentRoute: AppRoute,
    currentBackRoute: AppRoute,
    lastContentRoute: AppRoute,
    backRouteOverride: AppRoute?,
): AppRoute =
    backRouteOverride ?: when (currentRoute) {
        AppRoute.AlbumDetail -> currentBackRoute
        AppRoute.ArtistDetail -> AppRoute.ArtistDetail
        AppRoute.Player -> lastContentRoute
        else -> currentRoute
    }

fun artistDetailNavigation(
    artist: Artist,
    currentArtist: Artist?,
    currentRoute: AppRoute,
    currentBackStack: List<Artist>,
    currentBackRoute: AppRoute,
    lastContentRoute: AppRoute,
    backRouteOverride: AppRoute?,
    pushCurrentArtist: Boolean,
): ArtistDetailNavigation {
    val backStack = if (pushCurrentArtist && currentRoute == AppRoute.ArtistDetail) {
        currentArtist
            ?.takeIf { it.id != artist.id }
            ?.let { currentBackStack + it }
            ?: currentBackStack
    } else if (currentRoute != AppRoute.ArtistDetail) {
        emptyList()
    } else {
        currentBackStack
    }
    val backRoute = backRouteOverride ?: when (currentRoute) {
        AppRoute.ArtistDetail -> currentBackRoute
        AppRoute.Player -> lastContentRoute
        else -> currentRoute
    }
    return ArtistDetailNavigation(
        backStack = backStack,
        backRoute = backRoute,
    )
}

suspend fun loadAlbumDetails(
    cache: DesktopCache,
    provider: MediaProvider,
    album: Album,
): AlbumDetails =
    cache.album(provider, album.id)

suspend fun loadArtistDetails(
    cache: DesktopCache,
    provider: MediaProvider,
    artist: Artist,
    sourceId: String?,
): ArtistDetails =
    runCatching {
        cache.artist(provider, artist.id)
    }.recoverCatching { error ->
        val fallbackDetail = sourceId?.let {
            artistDetailsFromLibraryTracks(
                artistId = artist.id,
                fallbackName = artist.name,
                tracks = cache.libraryTracksForArtist(it, artist.id, limit = 1_000),
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

fun missingPopularTracksSourceStatus(): String =
    "Popular tracks unavailable: no connected media source."

fun loadingPopularTracksStatus(): String =
    "Loading popular tracks..."

fun artistPopularTracksUpdate(
    matches: List<ArtistPopularTrackMatch>,
    displayLimit: Int,
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

fun albumLoadErrorStatus(error: Throwable): String =
    error.message ?: "Could not load album."

fun artistLoadErrorStatus(error: Throwable): String =
    artistDetailLoadErrorStatus(error)
