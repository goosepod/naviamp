package app.naviamp.presentation

import app.naviamp.domain.provider.*
import app.naviamp.ui.SharedProviderDiscoveryUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

/** Shared discovery collection navigation and bounded reads. Never rebuilds recommendations. */
internal class NaviampDiscoveryBrowserController(
    private val state: () -> SharedProviderDiscoveryUi?,
    private val update: ((SharedProviderDiscoveryUi) -> SharedProviderDiscoveryUi) -> Unit,
    private val provider: () -> MediaProvider?,
    private val nowMillis: () -> Long,
    private val showPage: () -> Unit,
    private val stopPreview: () -> Unit,
) {
    private var revision = 0L
    private var read: Job? = null
    private val scheduled = mutableListOf<Pair<DiscoveryBrowseAction, Long>>()
    fun reset() { revision++; read?.cancel(); scheduled.clear(); update { it.copy(browser = null) } }
    fun dispatch(action: DiscoveryBrowseAction): NaviampCoreImmediateCommandResult {
        when (action) {
            is DiscoveryBrowseAction.Open -> {
                revision++; read?.cancel(); showPage(); stopPreview()
                update { it.copy(browser = it.browser?.copy(page = action.page) ?: DiscoveryBrowserState(page = action.page, nowMillis = nowMillis()),
                    artistPage = false, artistDetail = null, artistBackStack = emptyList(), releasePage = null) }
            }
            DiscoveryBrowseAction.Close -> { reset(); stopPreview(); return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.View -> { change { it.copy(view = action.view) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Sort -> { change { it.copy(sort = action.sort, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Filter -> { change { it.copy(filter = action.filter, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Genre -> { change { it.copy(genre = action.genre, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Seed -> { change { it.copy(seed = action.seed, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Viewport -> { change { it.copy(firstVisibleItem = action.index.coerceAtLeast(0), firstVisibleOffset = action.offset.coerceAtLeast(0)) }; return NaviampCoreImmediateCommandResult.Handled() }
            DiscoveryBrowseAction.Refresh -> { revision++; read?.cancel() }
            DiscoveryBrowseAction.More -> if (state()?.browser?.loading != false || state()?.browser?.nextOffset == null) return NaviampCoreImmediateCommandResult.Handled()
        }
        scheduled += action to revision
        change { it.copy(loading = true) }
        return NaviampCoreImmediateCommandResult.Deferred
    }
    private fun change(transform: (DiscoveryBrowserState) -> DiscoveryBrowserState) = update { current -> current.copy(browser = current.browser?.let(transform)) }
    suspend fun execute(action: DiscoveryBrowseAction) {
        val index = scheduled.indexOfFirst { it.first === action }; if (index < 0) return
        val token = scheduled.removeAt(index).second; if (token != revision) return
        val owner = provider()
        val native = owner?.discovery
        if (native == null) { change { it.copy(loading = false, status = ProviderDiscoveryStatus.Unsupported) }; return }
        read = currentCoroutineContext()[Job]
        fun current() = token == revision && provider() === owner
        try {
            val more = action == DiscoveryBrowseAction.More
            if (!more) {
                val feed = native.load()
                if (!current()) return
                change { it.copy(building = feed.status == ProviderDiscoveryStatus.Building) }
            }
            var offset = if (more) state()?.browser?.nextOffset ?: return else 0
            // Bounded passes; large collections retain an explicit Load more action.
            repeat(10) {
                val page = native.recommendations(offset, 100)
                if (!current()) return
                if (page.status !in setOf(ProviderDiscoveryStatus.Ready, ProviderDiscoveryStatus.Building)) {
                    change { it.copy(status = page.status) }; return
                }
                val old = state()?.browser ?: return
                if (offset == 0 && page.artists.isEmpty() && old.recommendations.isNotEmpty() && old.building) {
                    change { it.copy(status = ProviderDiscoveryStatus.Building) }; return
                }
                if (offset != 0 && old.runId != null && page.runId != null && old.runId != page.runId) {
                    change { it.copy(status = ProviderDiscoveryStatus.Unavailable) }; return
                }
                if (page.offset != offset || page.nextOffset?.let { it <= offset } == true) {
                    change { it.copy(status = ProviderDiscoveryStatus.Unavailable) }; return
                }
                change { previous -> previous.copy(
                    recommendations = ((if (offset == 0) emptyList() else previous.recommendations) + page.artists).distinctBy { it.id },
                    seeds = if (offset == 0) page.seeds else previous.seeds,
                    genres = if (offset == 0) page.genres else previous.genres,
                    lastUpdated = page.lastUpdated ?: previous.lastUpdated, runId = page.runId ?: previous.runId,
                    total = page.totalCount ?: previous.total, nextOffset = page.nextOffset, status = page.status,
                    nowMillis = nowMillis()) }
                offset = page.nextOffset ?: return
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (current()) change { it.copy(status = ProviderDiscoveryStatus.Unavailable) }
        } finally {
            if (current()) change { it.copy(loading = false) }
        }
    }
}
