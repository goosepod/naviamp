package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.provider.*
import app.naviamp.ui.SharedProviderDiscoveryUi
import app.naviamp.ui.SharedProviderDiscoveryArtistUi
import app.naviamp.ui.SharedDiscoveryReleaseUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Shared artist browsing, actions and preview lifecycle for every discovery entry point. */
class NaviampCoreDiscoveryController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val sourceId: () -> String?,
    private val externalUri: NaviampCoreExternalUriPort,
    private val playDiscoverySample: suspend (Track) -> Unit,
    private val stopDiscoverySample: () -> Unit,
    private val openLibraryArtist: suspend (app.naviamp.domain.Artist) -> Unit,
    private val refreshFeed: suspend (MediaProvider) -> Unit,
    private val showPage: () -> Unit = {},
    private val restoreRoute: (app.naviamp.ui.SharedRoute) -> Unit = {},
    private val openLibraryAlbum: suspend (app.naviamp.domain.Album) -> Unit = {},
    acquisitionRepository: DiscoveryAcquisitionRepository = MemoryDiscoveryAcquisitionRepository(),
    nowMillis: () -> Long = { 0L },
) : NaviampCoreCommandController {
    private val browser = NaviampDiscoveryBrowserController(
        { stateStore.state.value.shell.home.providerDiscovery }, ::updateDiscovery, providerSource::current, nowMillis, showPage, stopDiscoverySample, ::openCatalogAlbum, externalUri::open, playDiscoverySample, refreshFeed, { providerSource.isCurrentDiscovery(it) })
    private val acquisitions = DiscoveryAcquisitionTracker(acquisitionRepository, nowMillis)
    private fun acquisitionScope(provider: MediaProvider) = DiscoveryAcquisitionScope(sourceId() ?: "__unbound__", provider.discoveryAccountScope)
    private var discoveryDetailGeneration = 0L
    private val scheduledDiscoveryCommands = mutableListOf<Pair<NaviampCoreCommand.Home, Long>>()
    private var boundProvider = providerSource.current()
    private var boundSourceId = sourceId()
    private var releaseGeneration = 0L
    private val scheduledSamplePages = mutableListOf<Pair<NaviampCoreCommand.Home.DiscoveryAction, Long>>()
    private val scheduledReleaseActions = mutableListOf<Pair<NaviampCoreCommand.Home.DiscoveryReleaseAction, NaviampCoreCommand.Home.DiscoveryAction>>()
    private val requestLedger = mutableMapOf<String, Map<ProviderDiscoveryAction, ProviderDiscoveryOperation>>()
    private val contextualSearches = mutableListOf<Pair<NaviampCoreCommand.Home.FindInAurral, DiscoveryBrowseAction>>()
    private var sourceGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val home = command as? NaviampCoreCommand.Home ?: return NaviampCoreImmediateCommandResult.Unhandled
        bindSource()
        return when (home) {
            is NaviampCoreCommand.Home.FindInAurral -> {
                val provider = providerSource.current()
                val shell = stateStore.state.value.shell
                if (home.sourceId != sourceId() || provider?.discovery == null)
                    return NaviampCoreImmediateCommandResult.Handled()
                val query = if (home.artistId != null) {
                    shell.artistDetail.detail?.takeIf { it.artist.id == home.artistId && it.aurralSourceId == home.sourceId }?.artist?.title
                } else shell.nowPlaying?.takeIf { it.isLive && it.id == home.radioId &&
                    it.aurralRadioMetadata == home.radioMetadata && it.aurralSourceId == home.sourceId }
                    ?.aurralRadioMetadata?.substringBefore(" - ")
                if (query.isNullOrBlank()) return NaviampCoreImmediateCommandResult.Handled()
                browser.reset()
                stateStore.updateShell { current ->
                    val discovery = current.home.providerDiscovery?.takeIf { it.providerId == provider.effectiveDiscoveryProviderId.value }
                        ?: SharedProviderDiscoveryUi(provider.effectiveDiscoveryProviderId.value)
                    current.copy(home = current.home.copy(providerDiscovery = discovery.copy(
                        sourceId = sourceId(), sourceGeneration = sourceGeneration, requestsOpen = false,
                        originRoute = shell.shellChrome.selectedRoute, contextQuery = home.radioMetadata,
                        isCompanion = provider.effectiveDiscoveryProviderId != provider.id)))
                }
                browser.dispatch(DiscoveryBrowseAction.Open(DiscoveryBrowsePage.Search))
                val action = DiscoveryBrowseAction.ChartSearch(query, ProviderCatalogScope.Artist)
                val result = browser.dispatch(action)
                if (result == NaviampCoreImmediateCommandResult.Deferred) contextualSearches += home to action
                result
            }
            is NaviampCoreCommand.Home.Browse -> {
                val action = scopedBrowseAction(home.action)
                if (action == null) NaviampCoreImmediateCommandResult.Handled() else {
                    updateDiscovery { it.copy(sourceId = sourceId(), sourceGeneration = sourceGeneration) }
                    browser.dispatch(action)
                }
            }
            NaviampCoreCommand.Home.ShowDiscoveryRequests -> {
                stopDiscoverySample()
                publishAcquisitions()
                updateDiscovery { it.copy(requestsOpen = true) }
                NaviampCoreImmediateCommandResult.Handled()
            }
            NaviampCoreCommand.Home.DismissDiscoveryRequests -> {
                updateDiscovery { it.copy(requestsOpen = false) }
                NaviampCoreImmediateCommandResult.Handled()
            }
            NaviampCoreCommand.Home.RefreshDiscoveryRequests,
            is NaviampCoreCommand.Home.OpenDiscoveryRequest -> NaviampCoreImmediateCommandResult.Deferred
            is NaviampCoreCommand.Home.OpenDiscoveryRequestsExternal -> {
                val page = stateStore.state.value.shell.home.providerDiscovery
                if (home.sourceId == sourceId() && home.generation == page?.sourceGeneration)
                    providerSource.current()?.discovery?.requestsUrl?.let(externalUri::open)
                NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.DiscoveryReleaseAction -> {
                val page = stateStore.state.value.shell.home.providerDiscovery ?: return NaviampCoreImmediateCommandResult.Handled()
                val artist = page.artistDetail?.artist ?: return NaviampCoreImmediateCommandResult.Handled()
                if (home.sourceId != sourceId() || home.sourceGeneration != page.sourceGeneration ||
                    home.releaseId != page.releasePage?.release?.id) NaviampCoreImmediateCommandResult.Handled()
                else {
                    val inner = NaviampCoreCommand.Home.DiscoveryAction(artist.id, home.action)
                    val result = dispatch(inner)
                    if (result == NaviampCoreImmediateCommandResult.Deferred) scheduledReleaseActions += home to inner
                    result
                }
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryRelease -> NaviampCoreImmediateCommandResult.Deferred
            is NaviampCoreCommand.Home.BrowseDiscoveryArtist -> {
                val provider = providerSource.current()
                if (home.sourceId != sourceId() || home.artist.artistMbid == null || provider?.discovery == null)
                    return NaviampCoreImmediateCommandResult.Handled()
                stateStore.updateShell { shell -> shell.copy(home = shell.home.copy(providerDiscovery =
                    shell.home.providerDiscovery?.takeIf { it.providerId == provider.effectiveDiscoveryProviderId.value }
                        ?: SharedProviderDiscoveryUi(provider.effectiveDiscoveryProviderId.value, isCompanion = provider.effectiveDiscoveryProviderId != provider.id))) }
                if (home.sourceId == sourceId() && home.artist.artistMbid != null && prepareDiscoveryArtist(home.artist.id, home.artist)) {
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
                    NaviampCoreImmediateCommandResult.Deferred
                } else NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.SelectDiscoveryRelease -> {
                val current = stateStore.state.value.shell.home.providerDiscovery
                val release = current?.artistDetail?.details?.releases?.firstOrNull { it.id == home.id && it.releaseGroupMbid != null }
                if (release == null) NaviampCoreImmediateCommandResult.Handled() else {
                    stopDiscoverySample()
                    releaseGeneration++
                    updateDiscovery { it.copy(releasePage = SharedDiscoveryReleaseUi(release)) }
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
                    NaviampCoreImmediateCommandResult.Deferred
                }
            }
            is NaviampCoreCommand.Home.DiscoveryViewport -> {
                updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.copy(
                    firstVisibleItem = home.index.coerceAtLeast(0), firstVisibleOffset = home.offset.coerceAtLeast(0), releaseSection = home.section)) }
                NaviampCoreImmediateCommandResult.Handled()
            }
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
                stopDiscoverySample()
                val current = stateStore.state.value.shell.home.providerDiscovery
                if (current?.releasePage != null) {
                    releaseGeneration++
                    updateDiscovery { it.copy(releasePage = null) }
                    return NaviampCoreImmediateCommandResult.Handled()
                }
                discoveryDetailGeneration++
                val previous = current?.artistBackStack?.lastOrNull()?.let(::withRetainedRequests)
                updateDiscovery { it.copy(artistDetail = previous, artistPage = previous != null,
                    artistBackStack = it.artistBackStack.dropLast(1)) }
                if (previous == null) current?.originRoute?.let(restoreRoute)
                if (previous?.loading == true) NaviampCoreImmediateCommandResult.Deferred else NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.DiscoveryAction -> {
                val current = stateStore.state.value.shell.home.providerDiscovery
                val detail = current?.artistDetail
                val release = current?.releasePage
                val sample = home.action as? ProviderDiscoveryAction.PlaySample
                val allowed = if (sample != null) visibleSamples().any { it.id == sample.id }
                    else detail != null && !detail.actionBusy && !detail.savingFeedback && !requestBlocked(detail, home.action, release) &&
                        (release == null || release.requestContext != null && when (val action = home.action) {
                            is ProviderDiscoveryAction.RequestAlbum -> detail.details?.canRequestAlbums == true && action.id == release.release.id &&
                                release.ownership?.availability != ProviderDiscoveryReleaseAvailability.Available
                            is ProviderDiscoveryAction.RequestTrack -> detail.details?.canRequestTracks == true && release.tracks?.tracks?.any {
                                it.recordingMbid?.value == action.id && it.recordingMbid !in release.ownership?.ownedRecordings.orEmpty()
                            } == true
                            else -> false
                        })
                if (detail?.artist?.id == home.id && !detail.loading && allowed) {
                    if (sample == null) {
                        if (home.action is ProviderDiscoveryAction.RequestAlbum || home.action is ProviderDiscoveryAction.RequestTrack)
                            recordRequest(home.id, home.action, ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Uncertain))
                        updateDiscovery { it.copy(artistDetail = detail.copy(actionBusy = true, pendingAction = home.action,
                            operationStatus = null, requestOperations = detail.requestOperations - home.action)) }
                    }
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
                    if (sample != null) scheduledSamplePages += home to releaseGeneration
                    NaviampCoreImmediateCommandResult.Deferred
                } else NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.DiscoveryFeedback -> {
                val detail = stateStore.state.value.shell.home.providerDiscovery?.artistDetail
                if (detail?.artist?.id == home.id && !detail.loading && !detail.savingFeedback && !detail.actionBusy && detail.details?.feedbackAvailable == true) {
                    updateDiscovery { it.copy(artistDetail = detail.copy(savingFeedback = true, feedbackFailed = false)) }
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
                    NaviampCoreImmediateCommandResult.Deferred
                } else NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryArtist -> NaviampCoreImmediateCommandResult.Deferred
            else -> NaviampCoreImmediateCommandResult.Unhandled
        }
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        bindSource()
        when (command) {
            is NaviampCoreCommand.Home.FindInAurral -> {
                val index = contextualSearches.indexOfFirst { it.first === command }
                if (index >= 0) {
                    val action = contextualSearches.removeAt(index).second
                    if (command.sourceId == sourceId()) browser.execute(action)
                }
            }
            is NaviampCoreCommand.Home.Browse -> scopedBrowseAction(command.action)?.let { browser.execute(it) }
            NaviampCoreCommand.Home.RefreshDiscoveryRequests -> refreshAcquisitions()
            NaviampCoreCommand.Home.ShowDiscoveryRequests -> Unit
            NaviampCoreCommand.Home.DismissDiscoveryRequests -> Unit
            is NaviampCoreCommand.Home.OpenDiscoveryRequest -> {
                val provider = providerSource.current()
                val page = stateStore.state.value.shell.home.providerDiscovery
                val record = page?.acquisitions?.firstOrNull { it.id == command.id }
                if (record != null && command.sourceId == sourceId() && command.generation == page.sourceGeneration) {
                    updateDiscovery { it.copy(requestsOpen = false) }
                    val artist = ProviderDiscoveryArtist(record.artistId, record.artistName, detailsAvailable = true)
                    if (artist.artistMbid != null && prepareDiscoveryArtist(artist.id, artist)) {
                        val generation = discoveryDetailGeneration
                        loadDiscoveryArtist(artist.id)
                        if (generation == discoveryDetailGeneration && provider != null && providerSource.isCurrentDiscovery(provider) &&
                            command.sourceId == sourceId()) {
                            record.releaseId?.takeIf { runCatching { DiscoveryReleaseGroupMbid(it) }.isSuccess }?.let { id ->
                                releaseGeneration++
                                updateDiscovery { it.copy(releasePage = SharedDiscoveryReleaseUi(ProviderDiscoveryRelease(id, record.releaseTitle ?: id))) }
                                loadRelease(id)
                            }
                        }
                    }
                }
            }
            is NaviampCoreCommand.Home.DiscoveryReleaseAction -> {
                val index = scheduledReleaseActions.indexOfFirst { it.first === command }
                if (index >= 0) {
                    val inner = scheduledReleaseActions.removeAt(index).second
                    val page = stateStore.state.value.shell.home.providerDiscovery
                    if (command.sourceId == sourceId() && command.sourceGeneration == page?.sourceGeneration &&
                        command.releaseId == page.releasePage?.release?.id) execute(inner)
                    else {
                        takeDiscoveryGeneration(inner)
                        scheduledSamplePages.removeAll { it.first === inner }
                        if (command.sourceId == sourceId() && command.sourceGeneration == page?.sourceGeneration) {
                            requestLedger[inner.id]?.let { requestLedger[inner.id] = it - inner.action }
                            updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.let { detail ->
                                if (detail.artist.id == inner.id && detail.pendingAction == inner.action)
                                    detail.copy(actionBusy = false, pendingAction = null, operationStatus = ProviderDiscoveryOperationStatus.Unavailable)
                                else detail
                            }) }
                        }
                    }
                }
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryRelease -> {
                val page = stateStore.state.value.shell.home.providerDiscovery
                if (command.sourceId == sourceId() && command.sourceGeneration == page?.sourceGeneration &&
                    command.releaseId == page.releasePage?.release?.id) {
                    page.releasePage?.ownership?.libraryAlbum?.let { stopDiscoverySample(); openLibraryAlbum(it) }
                }
            }
            is NaviampCoreCommand.Home.BrowseDiscoveryArtist -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == discoveryDetailGeneration && command.sourceId == sourceId()) loadDiscoveryArtist(command.artist.id)
            }
            is NaviampCoreCommand.Home.SelectDiscoveryRelease -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == discoveryDetailGeneration) loadRelease(command.id)
            }
            NaviampCoreCommand.Home.DismissDiscoveryArtist -> {
                stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.takeIf { it.loading }?.let { loadDiscoveryArtist(it.artist.id) }
            }
            is NaviampCoreCommand.Home.SelectDiscoveryArtist -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == null || scheduled == discoveryDetailGeneration) loadDiscoveryArtist(command.id)
            }
            is NaviampCoreCommand.Home.DiscoveryAction -> {
                val scheduled = takeDiscoveryGeneration(command)
                val sampleIndex = scheduledSamplePages.indexOfFirst { it.first === command }
                val samplePage = if (sampleIndex < 0) null else scheduledSamplePages.removeAt(sampleIndex).second
                if ((scheduled != null || command.action is ProviderDiscoveryAction.PlaySample) &&
                    (scheduled == null || scheduled == discoveryDetailGeneration) &&
                    (samplePage == null || samplePage == releaseGeneration)) runDiscoveryAction(command)
            }
            is NaviampCoreCommand.Home.DiscoveryFeedback -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == null || scheduled == discoveryDetailGeneration) updateDiscoveryFeedback(command.id, command.action)
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryArtist -> {
                val provider = providerSource.current() ?: return NaviampCoreCommandResult.Completed
                val discovery = stateStore.state.value.shell.home.providerDiscovery
                discovery?.artistDetail?.takeIf { it.artist.id == command.id && discovery.providerId == provider.effectiveDiscoveryProviderId.value }
                    ?.details?.libraryArtist?.let { stopDiscoverySample(); openLibraryArtist(it) }
            }
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun runDiscoveryAction(command: NaviampCoreCommand.Home.DiscoveryAction) {
        val provider = providerSource.current() ?: return
        val discovery = provider.discovery ?: return
        val detail = stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.takeIf { it.artist.id == command.id } ?: return
        val releasePage = stateStore.state.value.shell.home.providerDiscovery?.releasePage
        if (command.action is ProviderDiscoveryAction.PlaySample) {
            val sample = visibleSamples().firstOrNull { it.id == command.action.id } ?: return
            playDiscoverySample(sample.playbackTrack(detail.artist.name))
            return
        }
        if (requestBlocked(detail, command.action, releasePage)) return
        val generation = discoveryDetailGeneration
        var operation: ProviderDiscoveryOperation? = null
        try {
            when (val action = command.action) {
                is ProviderDiscoveryAction.PlaySample -> Unit // Handled independently above.
                is ProviderDiscoveryAction.RequestTrack -> {
                    if (releasePage != null) {
                        val context = releasePage.requestContext ?: return
                        val track = releasePage.tracks?.tracks?.firstOrNull { it.recordingMbid?.value == action.id } ?: return
                        if (detail.details?.canRequestTracks == true) operation = acquisitions.submit(acquisitionScope(provider), detail.artist,
                            action, context.release, track.title) {
                                if (!providerSource.isCurrentDiscovery(provider)) throw CancellationException("Discovery source changed")
                                discovery.requestReleaseTrack(context, track)
                            }
                    } else {
                        val sample = detail.samples.firstOrNull { it.id == action.id } ?: return
                        if (detail.details?.canRequestTracks == true) operation = acquisitions.submit(acquisitionScope(provider), detail.artist,
                            action, null, sample.title) {
                                if (!providerSource.isCurrentDiscovery(provider)) throw CancellationException("Discovery source changed")
                                discovery.requestTrack(detail.artist, sample)
                            }
                    }
                }
                is ProviderDiscoveryAction.RequestAlbum -> {
                    val context = releasePage?.requestContext
                    val release = if (releasePage != null) context?.release?.takeIf { it.id == action.id }
                        else detail.details?.releases?.firstOrNull { it.id == action.id }
                    if (release != null && detail.details?.canRequestAlbums == true)
                        operation = acquisitions.submit(acquisitionScope(provider), context?.artist ?: detail.artist, action, release) {
                            if (!providerSource.isCurrentDiscovery(provider)) throw CancellationException("Discovery source changed")
                            discovery.requestAlbum(context?.artist ?: detail.artist, release)
                        }
                }
                is ProviderDiscoveryAction.SetMonitoring -> if (detail.details?.canChangeMonitoring == true)
                    operation = discovery.setMonitoring(detail.artist, action.enabled)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) {
                operation = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Uncertain)
                throw error
            }
            operation = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Uncertain)
        } finally {
            if (generation == discoveryDetailGeneration && providerSource.isCurrentDiscovery(provider)) {
                operation?.takeIf { command.action is ProviderDiscoveryAction.RequestAlbum || command.action is ProviderDiscoveryAction.RequestTrack }
                    ?.let { recordRequest(command.id, command.action, it) }
                updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.takeIf { it.artist.id == command.id }?.let { latest ->
                    val result = operation?.status
                    latest.copy(actionBusy = false, pendingAction = null,
                        requestOperations = operation?.takeIf { command.action is ProviderDiscoveryAction.RequestAlbum ||
                            command.action is ProviderDiscoveryAction.RequestTrack }?.let { latest.requestOperations + (command.action to it) } ?: latest.requestOperations,
                        operationStatus = result ?: latest.operationStatus,
                        blockedActions = if (result?.blocksRepeatedRequest == true)
                            latest.blockedActions + command.action else latest.blockedActions,
                        details = latest.details?.let { details -> details.copy(monitored = when (result) {
                            ProviderDiscoveryOperationStatus.MonitoringEnabled -> true
                            ProviderDiscoveryOperationStatus.MonitoringDisabled -> false
                            else -> details.monitored
                        }) })
                }) }
                publishAcquisitions()
            }
        }
    }

    private fun scopedBrowseAction(action: DiscoveryBrowseAction): DiscoveryBrowseAction? =
        if (action is DiscoveryBrowseAction.Scoped) action.action.takeIf {
            action.sourceId == sourceId() && action.generation == sourceGeneration && action.action !is DiscoveryBrowseAction.Scoped
        } else action

    private fun recordRequest(artistId: String, action: ProviderDiscoveryAction, operation: ProviderDiscoveryOperation) {
        requestLedger[artistId] = requestLedger[artistId].orEmpty() + (action to operation)
    }

    private fun publishAcquisitions() {
        val provider = providerSource.current() ?: return
        val records = acquisitions.records(acquisitionScope(provider))
        updateDiscovery { current -> current.copy(acquisitions = records,
            artistDetail = current.artistDetail?.let(::withRetainedRequests)) }
    }

    suspend fun refreshAcquisitions(): DiscoveryAcquisitionSnapshot? {
        bindSource()
        val provider = providerSource.current() ?: return null
        val discovery = provider.discovery ?: return null
        val scope = acquisitionScope(provider)
        val generation = sourceGeneration
        updateDiscovery { it.copy(requestsLoading = true) }
        val result = try { acquisitions.refresh(scope, discovery) } catch (error: Throwable) {
            if (error is CancellationException) throw error
            DiscoveryAcquisitionSnapshot(acquisitions.records(scope), status = ProviderDiscoveryStatus.Unavailable)
        } finally {
            if (generation == sourceGeneration && providerSource.isCurrentDiscovery(provider))
                updateDiscovery { it.copy(requestsLoading = false) }
        }
        if (generation != sourceGeneration || !providerSource.isCurrentDiscovery(provider) || scope != acquisitionScope(providerSource.current() ?: return null)) return null
        result.records.forEach { recordRequest(it.artistId, it.action, it.operation) }
        updateDiscovery { current -> current.copy(acquisitions = result.records,
            requestActivity = if (result.activity.status == ProviderDiscoveryStatus.Ready) result.activity else current.requestActivity.copy(status = result.activity.status),
            requestsStatus = result.status, artistDetail = current.artistDetail?.let { artist ->
                withRetainedRequests(artist).copy(operationStatus = artist.operationStatus.takeIf {
                    it in setOf(ProviderDiscoveryOperationStatus.MonitoringEnabled, ProviderDiscoveryOperationStatus.MonitoringDisabled)
                })
            },
            releasePage = current.releasePage?.let { page ->
                val record = result.records.firstOrNull { it.artistId == current.artistDetail?.artist?.id && it.releaseId == page.release.id }
                record?.let { result.ownership[it.id] }?.let { page.copy(ownership = it, ownershipLoading = false) } ?: page
            }) }
        return result
    }

    /** Called only by a visible, started common surface; cancellation stops in-flight reads. */
    suspend fun maintainAcquisitions() {
        var attempts = 0
        var failures = 0
        while (true) {
            val result = refreshAcquisitions() ?: return
            attempts++
            failures = if (result.status == ProviderDiscoveryStatus.Ready) 0 else failures + 1
            val wait = DiscoveryAcquisitionTracker.nextPollMillis(result, attempts, failures) ?: return
            delay(wait)
        }
    }

    private fun requestBlocked(artist: SharedProviderDiscoveryArtistUi, action: ProviderDiscoveryAction,
        release: SharedDiscoveryReleaseUi?): Boolean = action in artist.blockedActions ||
        (action is ProviderDiscoveryAction.RequestTrack && release != null &&
            artist.requestOperations[ProviderDiscoveryAction.RequestAlbum(release.release.id)]?.status == ProviderDiscoveryOperationStatus.WaitingForRelease)

    private fun withRetainedRequests(artist: SharedProviderDiscoveryArtistUi): SharedProviderDiscoveryArtistUi {
        val stored = providerSource.current()?.let { acquisitions.records(acquisitionScope(it)) }.orEmpty()
            .filter { it.artistId == artist.artist.id }.associateBy { it.action }
        val operations = artist.requestOperations + requestLedger[artist.artist.id].orEmpty() + stored.mapValues { it.value.operation }
        val blocked = operations.filterValues { it.status.blocksRepeatedRequest }.keys
        return artist.copy(requestOperations = operations, blockedActions = blocked, acquisitions = stored)
    }

    private fun discoveryArtist(id: String): ProviderDiscoveryArtist? {
        val discovery = stateStore.state.value.shell.home.providerDiscovery ?: return null
        if (discovery.providerId != providerSource.current()?.effectiveDiscoveryProviderId?.value) return null
        return discovery.artistDetail?.artist?.takeIf { it.id == id }
            ?: discovery.artistDetail?.similar?.artists?.firstOrNull { it.id == id }
            ?: discovery.browser?.charts?.artists?.firstOrNull { it.artist.id == id }?.artist
            ?: discovery.browser?.catalog?.artists?.firstOrNull { it.id == id }
            ?: discovery.browser?.recommendations?.firstOrNull { it.id == id }
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

    private fun prepareDiscoveryArtist(id: String, supplied: ProviderDiscoveryArtist? = null): Boolean {
        val artist = (supplied ?: discoveryArtist(id))?.takeIf { it.artistMbid != null } ?: return false
        val current = stateStore.state.value.shell.home.providerDiscovery?.artistDetail
        val origin = stateStore.state.value.shell.shellChrome.selectedRoute
        val different = current?.artist?.id != id
        if (different) stopDiscoverySample()
        discoveryDetailGeneration++
        showPage()
        updateDiscovery { it.copy(artistPage = true, releasePage = null, sourceId = sourceId(),
            sourceGeneration = sourceGeneration,
            originRoute = if (!it.artistPage) origin else it.originRoute,
            artistBackStack = if (different && current != null) it.artistBackStack + current.copy(
                actionBusy = false, savingFeedback = false, pendingAction = null,
                feedbackFailed = current.feedbackFailed || current.savingFeedback,
                details = if (current.savingFeedback) current.details?.copy(feedbackAvailable = false, feedback = emptySet()) else current.details,
                operationStatus = if (current.actionBusy) ProviderDiscoveryOperationStatus.Uncertain else current.operationStatus,
                requestOperations = current.requestOperations + requestLedger[current.artist.id].orEmpty(),
                blockedActions = current.blockedActions + listOfNotNull(current.pendingAction),
                samplesLoading = false, similarLoading = false,
                samplesStatus = current.samplesStatus ?: if (current.samplesLoading) ProviderDiscoveryStatus.Unavailable else null,
                similar = current.similar ?: if (current.similarLoading) ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Unavailable) else null,
            ) else it.artistBackStack,
            artistDetail = withRetainedRequests(if (!different && current != null) current.copy(loading = true, samplesLoading = true)
                else SharedProviderDiscoveryArtistUi(artist))) }
        return true
    }

    private suspend fun openCatalogAlbum(album: ProviderCatalogAlbum) {
        val provider = providerSource.current() ?: return
        val mbid = album.release.releaseGroupMbid ?: return
        val native = provider.discovery ?: return
        val generation = sourceGeneration
        val details = native.releaseDetails(mbid)
        if (generation != sourceGeneration || !providerSource.isCurrentDiscovery(provider)) return
        val resolvedRelease = details.release ?: return
        val credit = album.artistMbid ?: details.credits.firstNotNullOfOrNull { it.mbid }
        if (credit == null || details.status != ProviderDiscoveryStatus.Ready || resolvedRelease.releaseGroupMbid != mbid) return
        val artist = ProviderDiscoveryArtist(credit.value, album.artistName, detailsAvailable = true)
        if (!prepareDiscoveryArtist(artist.id, artist)) return
        val artistGeneration = discoveryDetailGeneration
        loadDiscoveryArtist(artist.id)
        if (artistGeneration != discoveryDetailGeneration || generation != sourceGeneration || !providerSource.isCurrentDiscovery(provider)) return
        releaseGeneration++
        updateDiscovery { it.copy(releasePage = SharedDiscoveryReleaseUi(resolvedRelease)) }
        loadRelease(mbid.value)
    }

    private suspend fun loadRelease(id: String) {
        val provider = providerSource.current() ?: return
        val generation = discoveryDetailGeneration
        val releaseRevision = releaseGeneration
        val release = stateStore.state.value.shell.home.providerDiscovery?.releasePage?.release?.takeIf { it.id == id } ?: return
        val mbid = release.releaseGroupMbid ?: return
        val artist = stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist ?: return
        coroutineScope {
            launch {
                val response = try { provider.discovery?.releaseDetails(mbid) ?: ProviderDiscoveryReleaseDetails() }
                catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Unavailable)
                }
                val result = response.takeIf { it.status != ProviderDiscoveryStatus.Ready || it.release?.id.equals(id, ignoreCase = true) }
                    ?: ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Unavailable)
                if (generation != discoveryDetailGeneration || releaseRevision != releaseGeneration || !providerSource.isCurrentDiscovery(provider)) return@launch
                updateDiscovery { current -> current.copy(releasePage = current.releasePage?.takeIf { it.release.id == id }
                    ?.copy(loading = false, result = result, requestContext = result.release?.takeIf {
                        result.status == ProviderDiscoveryStatus.Ready &&
                            (result.credits.isEmpty() || result.credits.any { credit -> credit.mbid?.value.equals(artist.id, ignoreCase = true) })
                    }?.let { ProviderDiscoveryReleaseContext(artist, it) })) }
            }
            launch {
                val tracks = try { provider.discovery?.releaseTracks(mbid, ProviderDiscoveryReleaseContext(artist, release)) ?: ProviderDiscoveryReleaseTracks() }
                catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoveryReleaseTracks(ProviderDiscoveryStatus.Unavailable)
                }
                if (generation != discoveryDetailGeneration || releaseRevision != releaseGeneration || !providerSource.isCurrentDiscovery(provider)) return@launch
                val ordered = tracks.copy(tracks = tracks.tracks.withIndex().sortedWith(
                    compareBy<IndexedValue<ProviderDiscoveryReleaseTrack>> { it.value.discNumber ?: Int.MAX_VALUE }
                        .thenBy { it.value.trackNumber ?: Int.MAX_VALUE }.thenBy { it.index }).map { it.value })
                updateDiscovery { current -> current.copy(releasePage = current.releasePage?.takeIf { it.release.id == id }
                    ?.copy(tracksLoading = false, tracks = ordered)) }
            }
            launch {
                val ownership = try { provider.discovery?.releaseOwnership(mbid) ?: ProviderDiscoveryReleaseOwnership() }
                catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoveryReleaseOwnership(ProviderDiscoveryStatus.Unavailable)
                }
                if (generation != discoveryDetailGeneration || releaseRevision != releaseGeneration || !providerSource.isCurrentDiscovery(provider)) return@launch
                updateDiscovery { current -> current.copy(releasePage = current.releasePage?.takeIf { it.release.id == id }
                    ?.copy(ownershipLoading = false, ownership = ownership)) }
            }
        }
    }

    private fun visibleSamples(): List<ProviderDiscoverySample> {
        val discovery = stateStore.state.value.shell.home.providerDiscovery ?: return emptyList()
        val release = discovery.releasePage ?: return discovery.artistDetail?.samples.orEmpty()
        return release.tracks?.tracks.orEmpty().mapNotNull { track ->
            track.previewId?.let { ProviderDiscoverySample(it.value, track.title, release.release.title, release.release.imageUrl) }
        }
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
        if (generation != discoveryDetailGeneration || !providerSource.isCurrentDiscovery(provider) ||
            stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist?.id != id) return
        updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.let { latest -> latest.copy(
            artist = result.details?.artist ?: artist, loading = false, status = result.status,
            details = result.details ?: latest.details, samplesLoading = true, similarLoading = true) }) }
        coroutineScope {
            launch {
                val samples = try { provider.discovery?.samples(result.details?.artist ?: artist) ?: ProviderDiscoverySamples() }
                catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoverySamples(ProviderDiscoveryStatus.Unavailable)
                }
                if (generation == discoveryDetailGeneration && providerSource.isCurrentDiscovery(provider)) {
                    updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.takeIf { it.artist.id == id }
                        ?.copy(samplesLoading = false, samples = samples.tracks, samplesStatus = samples.status)) }
                }
            }
            launch {
                val mbid = artist.artistMbid ?: return@launch
                val similar = try { provider.discovery?.similarArtists(mbid) ?: ProviderDiscoverySimilarArtists() }
                catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Unavailable)
                }
                if (generation == discoveryDetailGeneration && providerSource.isCurrentDiscovery(provider)) {
                    updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.takeIf { it.artist.id == id }
                        ?.copy(similarLoading = false, similar = similar)) }
                }
            }
        }
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
        if (generation != discoveryDetailGeneration || !providerSource.isCurrentDiscovery(provider) ||
            stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist?.id != id) return
        val success = result.status == ProviderDiscoveryStatus.Ready
        updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.takeIf { it.artist.id == id }?.let { latest ->
            latest.copy(savingFeedback = false, feedbackFailed = !success,
                details = latest.details?.copy(feedback = if (success) result.selected else emptySet(), feedbackAvailable = success))
        }) }
        if (success) {
            refreshFeed(provider)
            if (stateStore.state.value.shell.home.providerDiscovery?.browser != null) {
                browser.dispatch(DiscoveryBrowseAction.Refresh)
                browser.execute(DiscoveryBrowseAction.Refresh)
            }
        }
    }

    fun resetForSourceChange() {
        boundProvider = providerSource.current()
        boundSourceId = sourceId()
        contextualSearches.clear()
        stopDiscoverySample()
        discoveryDetailGeneration++
        releaseGeneration++
        sourceGeneration++
        requestLedger.clear()
        browser.reset()
        updateDiscovery { it.copy(feed = ProviderDiscoveryFeed(), loading = false,
            sourceGeneration = sourceGeneration, acquisitions = emptyList(), requestActivity = ProviderDiscoveryActivities(),
            requestsOpen = false, requestsLoading = false, requestsStatus = null) }
    }

    private fun bindSource() {
        val provider = providerSource.current()
        val key = sourceId()
        if (provider?.discoveryAccountScope != boundProvider?.discoveryAccountScope || provider?.id != boundProvider?.id || provider?.cacheNamespace != boundProvider?.cacheNamespace ||
            provider?.selectedMusicFolderIds?.toSet() != boundProvider?.selectedMusicFolderIds?.toSet() || key != boundSourceId) {
            resetForSourceChange()
            boundProvider = provider
            boundSourceId = key
            updateDiscovery { it.copy(artistDetail = null, artistPage = false, artistBackStack = emptyList(), releasePage = null, sourceId = key) }
        }
    }
}
