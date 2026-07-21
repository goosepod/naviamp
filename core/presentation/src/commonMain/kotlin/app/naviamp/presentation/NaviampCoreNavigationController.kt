package app.naviamp.presentation

import app.naviamp.app.NaviampDetailBackCommand
import app.naviamp.app.NaviampDetailKind
import app.naviamp.app.NaviampNavigationController
import app.naviamp.domain.Artist
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.ui.NaviampAlbumDetailScreenUi
import app.naviamp.ui.NaviampArtistDetailScreenUi
import app.naviamp.ui.NaviampPlaylistDetailScreenUi
import app.naviamp.ui.toNaviampRoute
import app.naviamp.ui.toSharedRoute

/** Core-to-Core link used when artist-detail back navigation reveals a prior artist. */
fun interface NaviampCoreArtistNavigator {
    fun openArtist(artist: Artist)
}

/** Owns shell routes, overlays, detail back policy, and their complete UI-state publication. */
class NaviampCoreNavigationController(
    private val navigation: NaviampNavigationController,
    private val stateStore: NaviampCoreStateStore,
    private val artistNavigator: NaviampCoreArtistNavigator,
) : NaviampCoreCommandController {
    init {
        publishNavigation()
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val navigationCommand = command as? NaviampCoreCommand.Navigation
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        when (navigationCommand) {
            is NaviampCoreCommand.Navigation.SelectRoute -> selectRoute(navigationCommand.route.toNaviampRoute())
            NaviampCoreCommand.Navigation.OpenNowPlaying -> setNowPlayingOpen(true)
            NaviampCoreCommand.Navigation.CloseNowPlaying -> setNowPlayingOpen(false)
            NaviampCoreCommand.Navigation.BackFromAlbum -> closeAlbum()
            NaviampCoreCommand.Navigation.BackFromArtist -> closeArtist()
            NaviampCoreCommand.Navigation.BackFromPlaylist -> closePlaylist()
        }
        return NaviampCoreImmediateCommandResult.Handled()
    }

    fun recordAlbumOpened(backRouteOverride: NaviampRoute? = null) {
        navigation.recordAlbumDetailOpened(backRouteOverride)
    }

    fun openAlbumDetail(backRouteOverride: NaviampRoute? = null) {
        recordAlbumOpened(backRouteOverride)
        navigation.navigate(NaviampRoute.AlbumDetail)
    }

    fun recordArtistOpened(
        artist: Artist,
        backRouteOverride: NaviampRoute? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        navigation.recordArtistDetailOpened(
            artist = artist,
            backRouteOverride = backRouteOverride,
            pushCurrentArtist = pushCurrentArtist,
        )
    }

    fun openArtistDetail(
        artist: Artist,
        backRouteOverride: NaviampRoute? = null,
        pushCurrentArtist: Boolean = true,
    ) {
        recordArtistOpened(artist, backRouteOverride, pushCurrentArtist)
        navigation.navigate(NaviampRoute.ArtistDetail)
    }

    fun updateActiveArtist(artist: Artist) {
        navigation.updateActiveArtist(artist)
    }

    private fun selectRoute(route: NaviampRoute) {
        navigation.navigate(route)
        navigation.updateLastContentRoute(route)
        navigation.clearDetailHistory()
        stateStore.updateShell { shell ->
            shell.copy(
                shellChrome = shell.shellChrome.copy(
                    selectedRoute = route.toSharedRoute(),
                    nowPlayingOpen = false,
                ),
                albumDetail = NaviampAlbumDetailScreenUi(),
                artistDetail = NaviampArtistDetailScreenUi(),
                playlistDetail = NaviampPlaylistDetailScreenUi(),
            )
        }
    }

    private fun setNowPlayingOpen(open: Boolean) {
        stateStore.updateShell { shell ->
            shell.copy(shellChrome = shell.shellChrome.copy(nowPlayingOpen = open))
        }
    }

    private fun closeAlbum() {
        when (val result = navigation.closeActiveDetail(NaviampDetailKind.Album)) {
            is NaviampDetailBackCommand.Navigate -> {
                navigation.navigate(result.route)
                stateStore.updateShell { shell ->
                    shell.copy(
                        shellChrome = shell.shellChrome.copy(selectedRoute = result.route.toSharedRoute()),
                        albumDetail = NaviampAlbumDetailScreenUi(),
                    )
                }
            }
            is NaviampDetailBackCommand.OpenArtist ->
                error("Album detail history returned an artist destination.")
        }
    }

    private fun closeArtist() {
        when (val result = navigation.closeActiveDetail(NaviampDetailKind.Artist)) {
            is NaviampDetailBackCommand.Navigate -> {
                navigation.navigate(result.route)
                stateStore.updateShell { shell ->
                    shell.copy(
                        shellChrome = shell.shellChrome.copy(selectedRoute = result.route.toSharedRoute()),
                        artistDetail = NaviampArtistDetailScreenUi(),
                    )
                }
            }
            is NaviampDetailBackCommand.OpenArtist -> artistNavigator.openArtist(result.artist)
        }
    }

    private fun closePlaylist() {
        navigation.navigate(NaviampRoute.Playlists)
        stateStore.updateShell { shell ->
            shell.copy(
                shellChrome = shell.shellChrome.copy(selectedRoute = NaviampRoute.Playlists.toSharedRoute()),
                playlistDetail = NaviampPlaylistDetailScreenUi(),
            )
        }
    }

    private fun publishNavigation() {
        val navigationState = navigation.state.value
        stateStore.updateShell { shell ->
            shell.copy(
                shellChrome = shell.shellChrome.copy(
                    selectedRoute = navigationState.route.toSharedRoute(),
                    nowPlayingOpen = navigationState.route == NaviampRoute.Player,
                ),
            )
        }
    }
}
