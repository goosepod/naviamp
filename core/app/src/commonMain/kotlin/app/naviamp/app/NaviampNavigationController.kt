package app.naviamp.app

import app.naviamp.domain.Artist
import app.naviamp.domain.app.NaviampNavigationState
import app.naviamp.domain.app.NaviampRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Canonical cross-platform owner of top-level routes and detail-screen back policy.
 *
 * Hosts load detail data and adapt operating-system back events, but they all record detail opens
 * and consume the same [NaviampDetailBackCommand].
 */
class NaviampNavigationController(
    initialState: NaviampNavigationState = NaviampNavigationState(),
) {
    private val mutableState = MutableStateFlow(initialState)
    private var detailState = NaviampDetailNavigationState()

    val state: StateFlow<NaviampNavigationState> = mutableState.asStateFlow()

    fun navigate(route: NaviampRoute) {
        mutableState.update { current -> current.copy(route = route) }
    }

    fun updateLastContentRoute(route: NaviampRoute) {
        mutableState.update { current -> current.copy(lastContentRoute = route) }
    }

    fun replace(state: NaviampNavigationState) {
        mutableState.value = state
    }

    val albumDetailBackRoute: NaviampRoute
        get() = detailState.albumBackRoute

    val artistDetailBackRoute: NaviampRoute
        get() = detailState.artistBackRoute

    fun recordAlbumDetailOpened(
        backRouteOverride: NaviampRoute? = null,
        openedFromNowPlaying: Boolean = false,
    ) {
        val navigation = state.value
        detailState = detailState.copy(
            albumBackRoute = backRouteOverride ?: when (navigation.route) {
                NaviampRoute.AlbumDetail -> detailState.albumBackRoute
                NaviampRoute.ArtistDetail -> NaviampRoute.ArtistDetail
                NaviampRoute.Player -> navigation.lastContentRoute
                else -> navigation.route
            },
            albumBackOpensNowPlaying = openedFromNowPlaying,
        )
    }

    fun recordArtistDetailOpened(
        artist: Artist,
        backRouteOverride: NaviampRoute? = null,
        pushCurrentArtist: Boolean = true,
        continuingArtistDetail: Boolean = state.value.route == NaviampRoute.ArtistDetail,
        openedFromNowPlaying: Boolean = false,
    ) {
        val navigation = state.value
        val backStack = if (pushCurrentArtist && continuingArtistDetail) {
            detailState.activeArtist
                ?.takeIf { current -> current.id != artist.id }
                ?.let { current -> detailState.artistBackStack + current }
                ?: detailState.artistBackStack
        } else if (!continuingArtistDetail) {
            emptyList()
        } else {
            detailState.artistBackStack
        }
        val backRoute = backRouteOverride ?: when (navigation.route) {
            NaviampRoute.ArtistDetail -> detailState.artistBackRoute
            NaviampRoute.Player -> navigation.lastContentRoute
            else -> navigation.route
        }
        detailState = detailState.copy(
            activeArtist = artist,
            artistBackStack = backStack,
            artistBackRoute = backRoute,
            artistBackOpensNowPlaying = if (continuingArtistDetail) {
                detailState.artistBackOpensNowPlaying
            } else {
                openedFromNowPlaying
            },
        )
    }

    /** Updates a fallback artist reference after the provider returns canonical metadata. */
    fun updateActiveArtist(artist: Artist) {
        detailState = detailState.copy(activeArtist = artist)
    }

    fun closeActiveDetail(kind: NaviampDetailKind): NaviampDetailBackCommand =
        when (kind) {
            NaviampDetailKind.Album -> NaviampDetailBackCommand.Navigate(
                detailState.albumBackRoute,
                reopenNowPlaying = detailState.albumBackOpensNowPlaying,
            )
            NaviampDetailKind.Artist -> {
                val previousArtist = detailState.artistBackStack.lastOrNull()
                if (previousArtist != null) {
                    detailState = detailState.copy(
                        activeArtist = previousArtist,
                        artistBackStack = detailState.artistBackStack.dropLast(1),
                    )
                    NaviampDetailBackCommand.OpenArtist(previousArtist)
                } else {
                    detailState = detailState.copy(activeArtist = null, artistBackStack = emptyList())
                    NaviampDetailBackCommand.Navigate(
                        detailState.artistBackRoute,
                        reopenNowPlaying = detailState.artistBackOpensNowPlaying,
                    )
                }
            }
        }

    fun clearDetailHistory() {
        detailState = NaviampDetailNavigationState()
    }
}

enum class NaviampDetailKind {
    Album,
    Artist,
}

sealed interface NaviampDetailBackCommand {
    data class Navigate(
        val route: NaviampRoute,
        val reopenNowPlaying: Boolean = false,
    ) : NaviampDetailBackCommand
    data class OpenArtist(val artist: Artist) : NaviampDetailBackCommand
}

private data class NaviampDetailNavigationState(
    val activeArtist: Artist? = null,
    val artistBackStack: List<Artist> = emptyList(),
    val albumBackRoute: NaviampRoute = NaviampRoute.Home,
    val artistBackRoute: NaviampRoute = NaviampRoute.Search,
    val albumBackOpensNowPlaying: Boolean = false,
    val artistBackOpensNowPlaying: Boolean = false,
)
