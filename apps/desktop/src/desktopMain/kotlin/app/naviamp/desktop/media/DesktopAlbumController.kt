package app.naviamp.desktop

import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DesktopAlbumController(
    private val scope: CoroutineScope,
    private val sessionCache: DesktopCache,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val currentRoute: () -> AppRoute,
    private val lastContentRoute: () -> AppRoute,
    private val albumDetailBackRoute: () -> AppRoute,
    private val setAlbumDetailBackRoute: (AppRoute) -> Unit,
    private val setRoute: (AppRoute) -> Unit,
    private val setSelectedAlbum: (Album?) -> Unit,
    private val setSelectedAlbumDetails: (AlbumDetails?) -> Unit,
    private val setSelectedAlbumStatus: (String?) -> Unit,
) {
    fun openAlbumDetails(album: Album, backRouteOverride: AppRoute? = null) {
        val activeProvider = provider() ?: return
        setAlbumDetailBackRoute(
            resolveAlbumDetailBackRoute(
                currentRoute = currentRoute(),
                currentBackRoute = albumDetailBackRoute(),
                lastContentRoute = lastContentRoute(),
                backRouteOverride = backRouteOverride,
            ),
        )
        setSelectedAlbum(album)
        setSelectedAlbumDetails(null)
        setSelectedAlbumStatus("Loading...")
        setRoute(AppRoute.AlbumDetail)
        scope.launch {
            try {
                setSelectedAlbumDetails(loadAlbumDetails(sessionCache, activeProvider, album, sourceId()))
                setSelectedAlbumStatus(null)
            } catch (exception: Exception) {
                setSelectedAlbumStatus(albumLoadErrorStatus(exception))
            }
        }
    }

    fun openTrackAlbumDetails(track: Track) {
        openAlbumDetails(trackAlbum(track) ?: return, backRouteOverride = AppRoute.Player)
    }
}
