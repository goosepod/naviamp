package app.naviamp.presentation

import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.home.HomeAlbumYear
import app.naviamp.domain.home.HomeContentLoadRequest
import app.naviamp.domain.home.HomeDate
import app.naviamp.domain.home.HomeLibraryRepository
import app.naviamp.domain.home.homeLoadFailureStatus
import app.naviamp.domain.home.loadHomeContent
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.homeSectionPresentation
import app.naviamp.domain.settings.resolvedHomeSectionOrder
import app.naviamp.domain.sonichome.SonicHomeDiscoveryService
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.SharedHomeCollectionPageUi

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

fun interface NaviampCoreSonicHomeDiscoverySource {
    suspend fun load(provider: MediaProvider, sourceId: String): SonicHomeDiscoveryRows
}

/** Builds Sonic Home discovery from portable provider and library-index contracts. */
fun naviampCoreSonicHomeDiscoverySource(
    libraryIndex: LocalLibraryIndexRepository,
): NaviampCoreSonicHomeDiscoverySource = NaviampCoreSonicHomeDiscoverySource { provider, sourceId ->
    runCatching {
        SonicHomeDiscoveryService(provider).loadRows(
            libraryTracks = libraryIndex.librarySnapshot(
                sourceId = sourceId,
                limit = SonicHomeDiscoveryLibrarySampleLimit,
            ).tracks,
            recentTracks = libraryIndex.recentlyPlayedLibraryTracks(
                sourceId = sourceId,
                limit = SonicHomeDiscoveryRecentTrackLimit,
            ),
            starredTracks = runCatching {
                provider.favoriteTracks(limit = SonicHomeDiscoveryFavoriteTrackLimit)
            }.getOrDefault(emptyList()),
        )
    }.getOrDefault(SonicHomeDiscoveryRows())
}

/** Adapts the portable library index to Home without host-owned repository mapping. */
fun localLibraryHomeRepository(
    libraryIndex: LocalLibraryIndexRepository,
): HomeLibraryRepository = object : HomeLibraryRepository {
    override fun albumYears(sourceId: String): List<HomeAlbumYear> =
        libraryIndex.libraryAlbumYears(sourceId).map { year ->
            HomeAlbumYear(year = year.year, albumCount = year.albumCount)
        }

    override fun recentlyPlayedTracks(sourceId: String, limit: Long): List<Track> =
        libraryIndex.recentlyPlayedLibraryTracks(sourceId, limit)
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
    private val sonicDiscoverySource: NaviampCoreSonicHomeDiscoverySource? = null,
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
            is NaviampCoreCommand.Home.OpenCollection -> {
                openCollection(home.sectionId)
                NaviampCoreImmediateCommandResult.Handled()
            }
            NaviampCoreCommand.Home.CloseCollection -> {
                closeCollection()
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
            val content = loadHomeContent(
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
            val sonicEnabled = stateStore.state.value.shell.playback.settings.sonicSimilarityEnabled &&
                provider.capabilities.supportsSonicSimilarity
            val sonicRows = if (sonicEnabled && supplement.sourceId != null) {
                sonicDiscoverySource?.load(provider, supplement.sourceId) ?: SonicHomeDiscoveryRows()
            } else {
                SonicHomeDiscoveryRows()
            }
            content to sonicRows
        }.onSuccess { (content, sonicRows) ->
            if (generation != refreshGeneration) return@onSuccess
            mediaRegistry.updateHome(content, sonicRows)
            val sonicEnabled = stateStore.state.value.shell.playback.settings.sonicSimilarityEnabled &&
                provider.capabilities.supportsSonicSimilarity
            stateStore.update { state ->
                val mappedContent = content.toSharedHomeUi(
                    coverArtUrl = { id -> id?.let(provider::coverArtUrl) },
                    playlistTracksById = supplement.playlistTracksById,
                    keepDownloadedPlaylistIds = supplement.keepDownloadedPlaylistIds,
                    sonicDiscoveryRows = sonicRows,
                    canFavoriteAlbums = provider.capabilities.supportsAlbumFavorites,
                    showSonicPathBuilder = sonicEnabled,
                    showSonicMixBuilder = sonicEnabled,
                    interfaceSettings = state.shell.general.interfaceSettings,
                )
                val refreshedPage = state.shell.home.collectionPage?.let { currentPage ->
                    mappedContent.collectionSections
                        .firstOrNull { it.id == currentPage.section.id }
                        ?.let { section -> currentPage.copy(section = section) }
                }
                state.copy(
                    shell = state.shell.copy(
                        home = state.shell.home.copy(
                            content = mappedContent,
                            refreshing = false,
                            collectionPage = refreshedPage,
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

    internal fun interfaceSettingsChanged(settings: InterfaceSettings) {
        stateStore.updateShell { shell ->
            val updatedSections = shell.home.content.collectionSections.map { section ->
                val presentation = settings.homeSectionPresentation(section.id)
                section.copy(
                    homeLayout = presentation.homeLayout,
                    homeItemLimit = presentation.homeItemLimit,
                    defaultPageLayout = presentation.pageLayout,
                )
            }
            val orderIndex = settings.resolvedHomeSectionOrder(updatedSections.map { it.id })
                .withIndex()
                .associate { it.value to it.index }
            val sections = updatedSections.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
            val currentPage = shell.home.collectionPage?.let { page ->
                val section = sections.firstOrNull { it.id == page.section.id } ?: return@let null
                page.copy(
                    section = section,
                    layout = settings.homeSectionPresentation(section.id).pageLayout,
                )
            }
            shell.copy(
                home = shell.home.copy(
                    content = shell.home.content.copy(collectionSections = sections),
                    collectionPage = currentPage,
                ),
            )
        }
    }

    fun resetForSourceChange() {
        refreshGeneration += 1
        mediaRegistry.updateHome(
            app.naviamp.domain.home.HomeContent(),
            SonicHomeDiscoveryRows(),
        )
        stateStore.updateShell { shell ->
            shell.copy(
                home = shell.home.copy(
                    content = app.naviamp.ui.SharedHomeUi(),
                    refreshing = false,
                    collectionPage = null,
                ),
            )
        }
    }

    private fun openCollection(sectionId: String) {
        val section = stateStore.state.value.shell.home.content.collectionSections
            .firstOrNull { it.id == sectionId }
        if (section == null) {
            publish(
                refreshing = stateStore.state.value.shell.home.refreshing,
                status = "This Home section is no longer available.",
            )
            return
        }
        stateStore.updateShell { shell ->
            shell.copy(home = shell.home.copy(collectionPage = SharedHomeCollectionPageUi(section)))
        }
    }

    private fun closeCollection() {
        stateStore.updateShell { shell ->
            shell.copy(home = shell.home.copy(collectionPage = null))
        }
    }

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

private const val SonicHomeDiscoveryLibrarySampleLimit = 5_000L
private const val SonicHomeDiscoveryRecentTrackLimit = 20L
private const val SonicHomeDiscoveryFavoriteTrackLimit = 5_000
