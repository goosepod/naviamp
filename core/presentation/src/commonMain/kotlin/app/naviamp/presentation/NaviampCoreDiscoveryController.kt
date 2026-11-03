package app.naviamp.presentation

import app.naviamp.domain.Track
import app.naviamp.domain.provider.*
import app.naviamp.ui.SharedProviderDiscoveryUi
import app.naviamp.ui.SharedProviderDiscoveryArtistUi
import app.naviamp.ui.SharedDiscoveryReleaseUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
) : NaviampCoreCommandController {
    private var discoveryDetailGeneration = 0L
    private val scheduledDiscoveryCommands = mutableListOf<Pair<NaviampCoreCommand.Home, Long>>()
    private var boundProvider = providerSource.current()
    private var boundSourceId = sourceId()
    private var releaseGeneration = 0L
    private var sourceGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val home = command as? NaviampCoreCommand.Home ?: return NaviampCoreImmediateCommandResult.Unhandled
        bindSource()
        return when (home) {
            is NaviampCoreCommand.Home.BrowseDiscoveryArtist -> {
                val provider = providerSource.current()
                if (home.sourceId != sourceId() || home.artist.artistMbid == null || provider?.discovery == null)
                    return NaviampCoreImmediateCommandResult.Handled()
                stateStore.updateShell { shell -> shell.copy(home = shell.home.copy(providerDiscovery =
                    shell.home.providerDiscovery?.takeIf { it.providerId == provider.id.value }
                        ?: SharedProviderDiscoveryUi(provider.id.value))) }
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
                val previous = current?.artistBackStack?.lastOrNull()
                updateDiscovery { it.copy(artistDetail = previous, artistPage = previous != null,
                    artistBackStack = it.artistBackStack.dropLast(1)) }
                if (previous == null) current?.originRoute?.let(restoreRoute)
                if (previous?.loading == true) NaviampCoreImmediateCommandResult.Deferred else NaviampCoreImmediateCommandResult.Handled()
            }
            is NaviampCoreCommand.Home.DiscoveryAction -> {
                val detail = stateStore.state.value.shell.home.providerDiscovery?.artistDetail
                val sample = home.action as? ProviderDiscoveryAction.PlaySample
                val allowed = if (sample != null) detail?.samples?.any { it.id == sample.id } == true
                    else detail != null && !detail.actionBusy && !detail.savingFeedback && home.action !in detail.blockedActions
                if (detail?.artist?.id == home.id && !detail.loading && allowed) {
                    if (sample == null) updateDiscovery { it.copy(artistDetail = detail.copy(actionBusy = true, pendingAction = home.action, operationStatus = null)) }
                    scheduledDiscoveryCommands += home to discoveryDetailGeneration
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
                if (scheduled == null || scheduled == discoveryDetailGeneration) runDiscoveryAction(command)
            }
            is NaviampCoreCommand.Home.DiscoveryFeedback -> {
                val scheduled = takeDiscoveryGeneration(command)
                if (scheduled == null || scheduled == discoveryDetailGeneration) updateDiscoveryFeedback(command.id, command.action)
            }
            is NaviampCoreCommand.Home.OpenDiscoveryLibraryArtist -> {
                val provider = providerSource.current() ?: return NaviampCoreCommandResult.Completed
                val discovery = stateStore.state.value.shell.home.providerDiscovery
                discovery?.artistDetail?.takeIf { it.artist.id == command.id && discovery.providerId == provider.id.value }
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
        if (command.action is ProviderDiscoveryAction.PlaySample) {
            val sample = detail.samples.firstOrNull { it.id == command.action.id } ?: return
            playDiscoverySample(sample.playbackTrack(detail.artist.name))
            return
        }
        val generation = discoveryDetailGeneration
        var operation: ProviderDiscoveryOperation? = null
        try {
            when (val action = command.action) {
                is ProviderDiscoveryAction.PlaySample -> Unit // Handled independently above.
                is ProviderDiscoveryAction.RequestTrack -> {
                    val sample = detail.samples.firstOrNull { it.id == action.id } ?: return
                    if (detail.details?.canRequestTracks == true) operation = discovery.requestTrack(detail.artist, sample)
                }
                is ProviderDiscoveryAction.RequestAlbum -> {
                    val release = detail.details?.releases?.firstOrNull { it.id == action.id } ?: return
                    if (detail.details?.canRequestAlbums == true) operation = discovery.requestAlbum(detail.artist, release)
                }
                is ProviderDiscoveryAction.SetMonitoring -> if (detail.details?.canChangeMonitoring == true)
                    operation = discovery.setMonitoring(detail.artist, action.enabled)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            operation = ProviderDiscoveryOperation(ProviderDiscoveryOperationStatus.Uncertain)
        } finally {
            if (generation == discoveryDetailGeneration && providerSource.isCurrent(provider)) {
                updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.takeIf { it.artist.id == command.id }?.let { latest ->
                    val result = operation?.status
                    latest.copy(actionBusy = false, pendingAction = null,
                        operationStatus = result ?: latest.operationStatus,
                        blockedActions = if (result in setOf(ProviderDiscoveryOperationStatus.Queued, ProviderDiscoveryOperationStatus.AlreadyQueued,
                            ProviderDiscoveryOperationStatus.AlreadyOwned, ProviderDiscoveryOperationStatus.Saved, ProviderDiscoveryOperationStatus.Uncertain))
                            latest.blockedActions + command.action else latest.blockedActions,
                        details = latest.details?.let { details -> details.copy(monitored = when (result) {
                            ProviderDiscoveryOperationStatus.MonitoringEnabled -> true
                            ProviderDiscoveryOperationStatus.MonitoringDisabled -> false
                            else -> details.monitored
                        }) })
                }) }
            }
        }
    }

    private fun discoveryArtist(id: String): ProviderDiscoveryArtist? {
        val discovery = stateStore.state.value.shell.home.providerDiscovery ?: return null
        if (discovery.providerId != providerSource.current()?.id?.value) return null
        return discovery.artistDetail?.artist?.takeIf { it.id == id }
            ?: discovery.artistDetail?.similar?.artists?.firstOrNull { it.id == id }
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
                blockedActions = current.blockedActions + listOfNotNull(current.pendingAction),
                samplesLoading = false, similarLoading = false,
                samplesStatus = current.samplesStatus ?: if (current.samplesLoading) ProviderDiscoveryStatus.Unavailable else null,
                similar = current.similar ?: if (current.similarLoading) ProviderDiscoverySimilarArtists(ProviderDiscoveryStatus.Unavailable) else null,
            ) else it.artistBackStack,
            artistDetail = if (!different && current != null) current.copy(loading = true, samplesLoading = true)
                else SharedProviderDiscoveryArtistUi(artist)) }
        return true
    }

    private suspend fun loadRelease(id: String) {
        val provider = providerSource.current() ?: return
        val generation = discoveryDetailGeneration
        val releaseRevision = releaseGeneration
        val release = stateStore.state.value.shell.home.providerDiscovery?.releasePage?.release?.takeIf { it.id == id } ?: return
        val mbid = release.releaseGroupMbid ?: return
        val result = try { provider.discovery?.releaseDetails(mbid) ?: ProviderDiscoveryReleaseDetails() }
        catch (error: Throwable) {
            if (error is CancellationException) throw error
            ProviderDiscoveryReleaseDetails(ProviderDiscoveryStatus.Unavailable)
        }
        if (generation != discoveryDetailGeneration || releaseRevision != releaseGeneration || !providerSource.isCurrent(provider)) return
        updateDiscovery { current -> current.copy(releasePage = current.releasePage?.takeIf { it.release.id == id }
            ?.copy(loading = false, result = result)) }
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
                if (generation == discoveryDetailGeneration && providerSource.isCurrent(provider)) {
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
                if (generation == discoveryDetailGeneration && providerSource.isCurrent(provider)) {
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
        if (generation != discoveryDetailGeneration || !providerSource.isCurrent(provider) ||
            stateStore.state.value.shell.home.providerDiscovery?.artistDetail?.artist?.id != id) return
        val success = result.status == ProviderDiscoveryStatus.Ready
        updateDiscovery { current -> current.copy(artistDetail = current.artistDetail?.takeIf { it.artist.id == id }?.let { latest ->
            latest.copy(savingFeedback = false, feedbackFailed = !success,
                details = latest.details?.copy(feedback = if (success) result.selected else emptySet(), feedbackAvailable = success))
        }) }
        if (success) refreshFeed(provider)
    }

    fun resetForSourceChange() {
        stopDiscoverySample()
        discoveryDetailGeneration++
        releaseGeneration++
        sourceGeneration++
    }

    private fun bindSource() {
        val provider = providerSource.current()
        val key = sourceId()
        if (provider?.id != boundProvider?.id || provider?.cacheNamespace != boundProvider?.cacheNamespace ||
            provider?.selectedMusicFolderIds?.toSet() != boundProvider?.selectedMusicFolderIds?.toSet() || key != boundSourceId) {
            resetForSourceChange()
            boundProvider = provider
            boundSourceId = key
            updateDiscovery { it.copy(artistDetail = null, artistPage = false, artistBackStack = emptyList(), releasePage = null, sourceId = key) }
        }
    }
}
