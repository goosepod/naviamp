package app.naviamp.presentation

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.home.HomeContentLoadRequest
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.home.homeLoadFailureStatus
import app.naviamp.domain.home.loadHomeContent
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.toSharedHomeUi

fun interface NaviampCoreHomeDateSource {
    fun current(): HomeDate
}

data class NaviampCoreHomeSupplement(
    val sourceId: String? = null,
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentInternetRadioStations: List<InternetRadioStation> = emptyList(),
    val playlistTracksById: Map<String, List<Track>> = emptyMap(),
    val keepDownloadedPlaylistIds: Set<String> = emptySet(),
    val sonicDiscoveryRows: SonicHomeDiscoveryRows = SonicHomeDiscoveryRows(),
)

fun interface NaviampCoreHomeSupplementSource {
    fun current(): NaviampCoreHomeSupplement
}

/** Owns Home loading, stale refresh rejection, mapping, status, and builder navigation. */
class NaviampCoreHomeController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val navigationController: NaviampCoreNavigationController,
    private val dateSource: NaviampCoreHomeDateSource,
    private val supplementSource: NaviampCoreHomeSupplementSource =
        NaviampCoreHomeSupplementSource { NaviampCoreHomeSupplement() },
    private val providerResponseService: ProviderResponseService? = null,
    private val libraryRepository: HomeLibraryRepository? = null,
    private val artistLimit: Int = 50,
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
) : NaviampCoreCommandController {
    private var refreshGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val home = command as? NaviampCoreCommand.Home
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        return when (home) {
            NaviampCoreCommand.Home.Refresh -> NaviampCoreImmediateCommandResult.Deferred
            is NaviampCoreCommand.Home.SelectMixBuilder -> {
                selectMixBuilder(home.item.id)
                NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.SelectRecentRadio,
            is NaviampCoreCommand.Home.SelectInternetRadio,
            is NaviampCoreCommand.Home.SelectStation,
            is NaviampCoreCommand.Home.SonicTrackAction,
            is NaviampCoreCommand.Home.RecentTrackAction,
            -> NaviampCoreImmediateCommandResult.Unhandled
        }
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        if (command != NaviampCoreCommand.Home.Refresh) return null
        refresh()
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun refresh() {
        val generation = ++refreshGeneration
        val provider = providerSource.current()
        if (provider == null) {
            publish(refreshing = false, status = "Connect to Navidrome to load Home.")
            return
        }
        val supplement = supplementSource.current()
        publish(refreshing = true, status = "Loading home...")
        runCatching {
            loadHomeContent(
                HomeContentLoadRequest(
                    provider = provider,
                    providerResponseService = providerResponseService,
                    libraryRepository = libraryRepository,
                    sourceId = supplement.sourceId,
                    date = dateSource.current(),
                    recentRadioStreams = supplement.recentRadioStreams,
                    recentInternetRadioStations = supplement.recentInternetRadioStations,
                    artistLimit = artistLimit,
                ),
            )
        }.onSuccess { content ->
            if (generation != refreshGeneration) return@onSuccess
            mediaRegistry.updateHome(content, supplement.sonicDiscoveryRows)
            val sonicEnabled = stateStore.state.value.shell.playback.settings.sonicSimilarityEnabled &&
                provider.capabilities.supportsSonicSimilarity
            stateStore.update { state ->
                state.copy(
                    shell = state.shell.copy(
                        home = state.shell.home.copy(
                            content = content.toSharedHomeUi(
                                coverArtUrl = { id -> id?.let(provider::coverArtUrl) },
                                playlistTracksById = supplement.playlistTracksById,
                                keepDownloadedPlaylistIds = supplement.keepDownloadedPlaylistIds,
                                sonicDiscoveryRows = supplement.sonicDiscoveryRows,
                                canFavoriteAlbums = provider.capabilities.supportsAlbumFavorites,
                                showSonicPathBuilder = sonicEnabled,
                                showSonicMixBuilder = sonicEnabled,
                            ),
                            refreshing = false,
                        ),
                    ),
                    overlays = state.overlays.copy(status = null),
                )
            }
        }.onFailure { cause ->
            if (generation == refreshGeneration) {
                publish(refreshing = false, status = homeLoadFailureStatus(cause))
            }
        }
    }

    suspend fun refreshAfterConnection() = refresh()

    private fun selectMixBuilder(id: String) {
        val route = when (id) {
            "artist" -> SharedRoute.ArtistMix
            "album" -> SharedRoute.AlbumMix
            "genre" -> SharedRoute.GenreMix
            "sonic-path" -> SharedRoute.SonicPath
            "sonic-mix" -> SharedRoute.SonicMix
            else -> {
                publish(
                    refreshing = stateStore.state.value.shell.home.refreshing,
                    status = "Unknown mix builder.",
                )
                return
            }
        }
        navigationController.dispatch(NaviampCoreCommand.Navigation.SelectRoute(route))
    }

    private fun publish(refreshing: Boolean, status: String?) {
        stateStore.update { state ->
            state.copy(
                shell = state.shell.copy(home = state.shell.home.copy(refreshing = refreshing)),
                overlays = state.overlays.copy(status = status),
            )
        }
    }
}
