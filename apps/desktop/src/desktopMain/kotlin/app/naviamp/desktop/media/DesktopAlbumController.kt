package app.naviamp.desktop

import app.naviamp.domain.app.NaviampRoute
import app.naviamp.app.NaviampDetailBackCommand
import app.naviamp.app.NaviampDetailKind
import app.naviamp.app.NaviampNavigationController

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.media.AlbumDetailFlowCoordinator
import app.naviamp.domain.media.AlbumDetailFlowRequest
import app.naviamp.domain.media.connectedDetailStatusAsNull
import app.naviamp.domain.media.trackAlbum
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DesktopAlbumController(
    private val scope: CoroutineScope,
    private val libraryIndexRepository: LocalLibraryIndexRepository,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val provider: () -> MediaProvider?,
    private val sourceId: () -> String?,
    private val navigationController: NaviampNavigationController,
    private val setRoute: (NaviampRoute) -> Unit,
) {
    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)

    var selectedAlbum by mutableStateOf<Album?>(null)
        private set
    var selectedAlbumDetails by mutableStateOf<AlbumDetails?>(null)
        private set
    var selectedAlbumStatus by mutableStateOf<String?>(null)
        private set
    val albumDetailBackRoute: NaviampRoute
        get() = navigationController.albumDetailBackRoute

    fun updateSelectedAlbumDetails(details: AlbumDetails?) {
        selectedAlbumDetails = details
    }

    fun openAlbumDetails(album: Album, backRouteOverride: NaviampRoute? = null) {
        val activeProvider = provider() ?: return
        navigationController.recordAlbumDetailOpened(backRouteOverride)
        selectedAlbum = album
        selectedAlbumDetails = null
        setRoute(NaviampRoute.AlbumDetail)
        scope.launch {
            AlbumDetailFlowCoordinator(
                setStatus = { status -> selectedAlbumStatus = connectedDetailStatusAsNull(status) },
                applyDetail = { details -> selectedAlbumDetails = details },
            ).load(
                AlbumDetailFlowRequest(
                    libraryIndexRepository = libraryIndexRepository,
                    providerResponseService = providerResponseService,
                    provider = activeProvider,
                    albumId = album.id,
                    fallbackTitle = album.title,
                    fallbackArtistName = album.artistName,
                    sourceId = sourceId(),
                ),
            )
        }
    }

    fun openTrackAlbumDetails(track: Track) {
        openAlbumDetails(trackAlbum(track) ?: return, backRouteOverride = NaviampRoute.Player)
    }

    fun closeAlbumDetails() {
        when (val command = navigationController.closeActiveDetail(NaviampDetailKind.Album)) {
            is NaviampDetailBackCommand.Navigate -> setRoute(command.route)
            is NaviampDetailBackCommand.OpenArtist -> error("Album detail history returned an artist command.")
        }
    }
}
