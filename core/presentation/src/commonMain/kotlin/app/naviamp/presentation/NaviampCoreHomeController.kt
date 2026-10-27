package app.naviamp.presentation

import app.naviamp.domain.home.sortedFavoriteArtists

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
import app.naviamp.domain.radio.sessionSubtitle
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.homeSectionPresentation
import app.naviamp.domain.settings.effectiveSonicSimilarityEnabled
import app.naviamp.domain.settings.resolvedHomeSectionOrder
import app.naviamp.domain.sonichome.SonicHomeDiscoveryService
import app.naviamp.domain.sonichome.SonicHomeDiscoveryRows
import app.naviamp.ui.SharedRoute
import app.naviamp.ui.SharedMediaItemUi
import app.naviamp.ui.toSharedHomeUi
import app.naviamp.ui.SharedHomeCollectionPageUi
import app.naviamp.ui.withRecentRadioStreams
import app.naviamp.domain.provider.ProviderDiscoveryFeed
import app.naviamp.domain.provider.ProviderDiscoveryStatus
import app.naviamp.ui.SharedProviderDiscoveryUi
import app.naviamp.ui.SharedProviderDiscoveryArtistUi
import app.naviamp.domain.provider.ProviderDiscoveryArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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

    override fun reconcileFavoriteArtists(
        sourceId: String,
        artists: List<app.naviamp.domain.Artist>,
        observedAtIso8601: String,
        complete: Boolean,
    ) = libraryIndex.reconcileFavoriteArtists(sourceId, artists, observedAtIso8601, complete)

    override fun locallyKnownFavoriteArtists(sourceId: String, limit: Long) =
        libraryIndex.locallyKnownFavoriteArtists(sourceId, limit)

    override fun favoriteArtistRadioLastPlayed(sourceId: String) =
        libraryIndex.favoriteArtistRadioLastPlayed(sourceId)

    override fun setArtistFavoriteActivity(
        sourceId: String,
        artist: app.naviamp.domain.Artist,
        favorite: Boolean,
        changedAtIso8601: String,
    ) = libraryIndex.setArtistFavoriteActivity(sourceId, artist, favorite, changedAtIso8601)

    override fun recordArtistRadioPlayed(
        sourceId: String,
        artist: app.naviamp.domain.Artist,
        playedAtIso8601: String,
    ) = libraryIndex.recordArtistRadioPlayed(sourceId, artist, playedAtIso8601)

    override fun recordTrackArtistRadioPlayedIfFavorite(
        sourceId: String,
        artistId: app.naviamp.domain.ArtistId,
        artistName: String,
        playedAtIso8601: String,
    ) = libraryIndex.recordTrackArtistRadioPlayedIfFavorite(sourceId, artistId, artistName, playedAtIso8601)
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
    private val observedAtIso8601: () -> String = { "" },
    private val externalUri: NaviampCoreExternalUriPort = NaviampCoreExternalUriPort {},
    private val openLibraryArtist: suspend (app.naviamp.domain.Artist) -> Unit = {},
) : NaviampCoreCommandController {
    private var refreshGeneration = 0L
    private var discoveryDetailGeneration = 0L
    private var discoveryFeedGeneration = 0L
    private val scheduledDiscoveryCommands = mutableListOf<Pair<NaviampCoreCommand.Home, Long>>()

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val home = command as? NaviampCoreCommand.Home
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        return when (home) {
            NaviampCoreCommand.Home.Refresh -> NaviampCoreImmediateCommandResult.Deferred
            is NaviampCoreCommand.Home.OpenDiscoveryArtist -> {
                discoveryArtist(home.id)?.externalUrl?.let(externalUri::open)
                NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.SelectDiscoveryArtist -> {
                if (prepareDiscoveryArtist(home.id)) {
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
                    NaviampCoreImmediateCommandResult.Deferred
                }
                else NaviampCoreImmediateCommandResult.Handled()
            }
            NaviampCoreCommand.Home.DismissDiscoveryArtist -> {
                discoveryDetailGeneration++
                updateDiscovery { it.copy(artistDetail = null) }
                NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.DiscoveryFeedback -> {
                val detail = stateStore.state.value.shell.home.providerDiscovery?.artistDetail
                if (detail?.artist?.id == home.id && !detail.loading && !detail.savingFeedback && detail.details?.feedbackAvailable == true) {
                    updateDiscovery { it.copy(artistDetail = detail.copy(savingFeedback = true, feedbackFailed = false)) }
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
                    NaviampCoreImmediateCommandResult.Deferred
                } else NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryArtist -> NaviampCoreImmediateCommandResult.Deferred
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
        when (command) {
            NaviampCoreCommand.Home.Refresh -> refresh(forceRefreshRecentlyAdded = true)
            is NaviampCoreCommand.Home.SelectDiscoveryArtist -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == null || scheduled == discoveryDetailGeneration) loadDiscoveryArtist(command.id)
            }
            is NaviampCoreCommand.Home.DiscoveryFeedback -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == null || scheduled == discoveryDetailGeneration) updateDiscoveryFeedback(command.id, command.action)
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryArtist -> {
                val provider = providerSource.current() ?: return NaviampCoreCommandResult.Completed
                val discovery = stateStore.state.value.shell.home.providerDiscovery
                discovery?.artistDetail?.takeIf { it.artist.id == command.id && discovery.providerId == provider.id.value }
                    ?.details?.libraryArtist?.let { openLibraryArtist(it) }
            }
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private fun discoveryArtist(id: String): ProviderDiscoveryArtist? {
        val discovery = stateStore.state.value.shell.home.providerDiscovery ?: return null
        if (discovery.providerId != providerSource.current()?.id?.value) return null
        return discovery.artistDetail?.artist?.takeIf { it.id == id }
            ?: (discovery.feed.recommendations + discovery.feed.trending).firstOrNull { it.id == id }
    }

    private fun takeDiscoveryGeneration(command: NaviampCoreCommand.Home): Long? {
        val index = scheduledDiscoveryCommands.indexOfFirst { it.first === command }
        return if (index < 0) null else scheduledDiscoveryCommands.removeAt(index).second
    }

    private fun updateDiscovery(transform: (SharedProviderDiscoveryUi) -> SharedProviderDiscoveryUi) {
        stateStore.updateShell { shell -> shell.copy(home = shell.home.copy(
            providerDiscovery = shell.home.providerDiscovery?.let(transform),
        )) }
    }

    private fun prepareDiscoveryArtist(id: String): Boolean {
        val artist = discoveryArtist(id)?.takeIf { it.detailsAvailable } ?: return false
        discoveryDetailGeneration++
        updateDiscovery { it.copy(artistDetail = SharedProviderDiscoveryArtistUi(artist)) }
        return true
    }

    private suspend fun loadDiscoveryArtist(id: String) {
        if (stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist?.id != id && !prepareDiscoveryArtist(id)) return
        val artist = discoveryArtist(id) ?: return
        val provider = providerSource.current() ?: return
        val generation = discoveryDetailGeneration
        val result = try {
            provider.discovery?.artistDetails(artist)
                ?: app.naviamp.domain.provider.ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Unsupported)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            app.naviamp.domain.provider.ProviderDiscoveryArtistResult(ProviderDiscoveryStatus.Unavailable)
        }
        if (generation != discoveryDetailGeneration || !providerSource.isCurrent(provider) ||
            stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist?.id != id) return
        updateDiscovery { current -> current.copy(artistDetail = SharedProviderDiscoveryArtistUi(
            artist = result.details?.artist ?: artist, loading = false, status = result.status, details = result.details,
        )) }
    }

    private suspend fun updateDiscoveryFeedback(id: String, action: app.naviamp.domain.provider.ProviderDiscoveryFeedbackAction) {
        val detail = stateStore.state.value.shell.home.providerDiscovery?.artistDetail
            ?.takeIf { it.artist.id == id && !it.loading && it.details?.feedbackAvailable == true } ?: return
        val provider = providerSource.current() ?: return
        val discovery = provider.discovery ?: return
        val generation = discoveryDetailGeneration
        updateDiscovery { it.copy(artistDetail = detail.copy(savingFeedback = true, feedbackFailed = false)) }
        val result = try { discovery.toggleFeedback(detail.artist, action) } catch (error: Throwable) {
            if (error is CancellationException) throw error
            app.naviamp.domain.provider.ProviderDiscoveryFeedbackResult(ProviderDiscoveryStatus.Unavailable)
        }
        if (generation != discoveryDetailGeneration || !providerSource.isCurrent(provider) ||
            stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist?.id != id) return
        val success = result.status == ProviderDiscoveryStatus.Ready
        updateDiscovery { it.copy(artistDetail = detail.copy(savingFeedback = false, feedbackFailed = !success,
            details = detail.details?.copy(feedback = if (success) result.selected else emptySet(), feedbackAvailable = success))) }
        if (success) refreshProviderDiscovery(provider, refreshGeneration)
    }

    private suspend fun refresh(forceRefreshRecentlyAdded: Boolean = false) = coroutineScope {
        val generation = ++refreshGeneration
        val provider = providerSource.current()
        if (provider == null) {
            stateStore.updateShell { it.copy(home = it.home.copy(providerDiscovery = null)) }
            publish(refreshing = false, status = "Connect to Navidrome to load Home.")
            return@coroutineScope
        }
        val supplement = supplementSource.current()
        publish(refreshing = true, status = "Loading home...")
        launch { refreshProviderDiscovery(provider, generation) }
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
                    forceRefreshRecentlyAdded = forceRefreshRecentlyAdded,
                    observedAtIso8601 = observedAtIso8601,
                ),
            )
            val sonicEnabled = stateStore.state.value.shell.playback.settings.effectiveSonicSimilarityEnabled() &&
                provider.capabilities.supportsSonicSimilarity
            val sonicRows = if (sonicEnabled && supplement.sourceId != null) {
                sonicDiscoverySource?.load(provider, supplement.sourceId) ?: SonicHomeDiscoveryRows()
            } else {
                SonicHomeDiscoveryRows()
            }
            content to sonicRows
        }.onSuccess { (content, sonicRows) ->
            if (generation != refreshGeneration || !providerSource.isCurrent(provider)) return@onSuccess
            mediaRegistry.updateHome(content, sonicRows)
            val sonicEnabled = stateStore.state.value.shell.playback.settings.effectiveSonicSimilarityEnabled() &&
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
            if (cause is CancellationException) throw cause
            if (generation == refreshGeneration && providerSource.isCurrent(provider)) {
                publish(refreshing = false, status = homeLoadFailureStatus(cause))
            }
        }
    }

    private suspend fun refreshProviderDiscovery(provider: MediaProvider, generation: Long) {
        if (generation != refreshGeneration || !providerSource.isCurrent(provider)) return
        val feedGeneration = ++discoveryFeedGeneration
        val discovery = provider.discovery
        if (discovery == null) {
            stateStore.updateShell { it.copy(home = it.home.copy(providerDiscovery = null)) }
            return
        }
        stateStore.updateShell { shell -> shell.copy(home = shell.home.copy(
            providerDiscovery = shell.home.providerDiscovery?.takeIf { it.providerId == provider.id.value }?.copy(loading = true)
                ?: SharedProviderDiscoveryUi(provider.id.value, loading = true),
        )) }
        val feed = try {
            discovery.load()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            ProviderDiscoveryFeed(ProviderDiscoveryStatus.Unavailable)
        }
        if (generation != refreshGeneration || feedGeneration != discoveryFeedGeneration || !providerSource.isCurrent(provider)) return
        stateStore.updateShell { shell -> shell.copy(home = shell.home.copy(
            providerDiscovery = (shell.home.providerDiscovery ?: SharedProviderDiscoveryUi(provider.id.value)).copy(feed = feed, loading = false),
        )) }
    }


    suspend fun refreshAfterConnection() = refresh()

    fun restoreRecentRadioStreams(streams: List<RecentRadioStream>) {
        val provider = providerSource.current()
        stateStore.updateShell { shell ->
            val recentItems = streams.map { recent ->
                val coverArtUrls = recent.coverArtIds
                    .mapNotNull { id -> provider?.coverArtUrl(id) }
                    .distinct()
                    .take(4)
                SharedMediaItemUi(
                    id = recent.id,
                    title = recent.label,
                    subtitle = recent.sessionSubtitle(),
                    coverArtUrl = coverArtUrls.firstOrNull(),
                    coverArtUrls = coverArtUrls,
                )
            }
            shell.copy(
                home = shell.home.copy(
                    content = shell.home.content.withRecentRadioStreams(
                        streams = recentItems,
                        interfaceSettings = shell.general.interfaceSettings,
                    ),
                ),
            )
        }
    }

    internal fun interfaceSettingsChanged(settings: InterfaceSettings) {
        val favoriteOrder = mediaRegistry.home.favoriteArtists
            .sortedFavoriteArtists(settings.favoriteArtistSort, mediaRegistry.home.favoriteArtistLastPlayed)
            .withIndex().associate { it.value.id.value to it.index }
        stateStore.updateShell { shell ->
            val updatedSections = shell.home.content.collectionSections.map { section ->
                val presentation = settings.homeSectionPresentation(section.id)
                section.copy(
                    items = if (section.favoriteArtistSort != null) section.items.sortedBy {
                        favoriteOrder[it.mediaItem.id] ?: Int.MAX_VALUE
                    } else section.items,
                    favoriteArtistSort = section.favoriteArtistSort?.let { settings.favoriteArtistSort },
                    visible = presentation.visible,
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
                val section = sections.firstOrNull { it.id == page.section.id && it.visible }
                    ?: return@let null
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
        discoveryDetailGeneration++
        discoveryFeedGeneration++
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
                    providerDiscovery = null,
                ),
            )
        }
    }

    private fun openCollection(sectionId: String) {
        val section = stateStore.state.value.shell.home.content.collectionSections
            .firstOrNull { it.id == sectionId && it.visible }
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
