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
    private val openAlbum: suspend (ProviderCatalogAlbum) -> Unit,
    private val openExternal: (String) -> Unit,
    private val playPreview: suspend (app.naviamp.domain.Track) -> Unit,
    private val refreshHome: suspend (MediaProvider) -> Unit,
) {
    private val pendingGenres = mutableMapOf<Pair<String, String>, Boolean>()
    private val uncertainSaves = mutableSetOf<Pair<String, String>>()
    private val backStack = mutableListOf<DiscoveryBrowserState>()
    private var revision = 0L
    private var read: Job? = null
    private val scheduled = mutableListOf<Pair<DiscoveryBrowseAction, Long>>()
    fun reset() { revision++; read?.cancel(); scheduled.clear(); backStack.clear(); update { it.copy(browser = null) } }
    fun dispatch(action: DiscoveryBrowseAction): NaviampCoreImmediateCommandResult {
        when (action) {
            is DiscoveryBrowseAction.Scoped -> return NaviampCoreImmediateCommandResult.Handled()
            is DiscoveryBrowseAction.Open -> {
                state()?.browser?.takeIf { it.page != action.page }?.let { backStack += it }
                revision++; read?.cancel(); showPage(); stopPreview()
                update { it.copy(browser = it.browser?.copy(page = action.page) ?: DiscoveryBrowserState(page = action.page, nowMillis = nowMillis()),
                    artistPage = false, artistDetail = null, artistBackStack = emptyList(), releasePage = null) }
            }
            is DiscoveryBrowseAction.BlockGenre -> {
                val screen = state()?.browser ?: return NaviampCoreImmediateCommandResult.Handled()
                val account = provider()?.discoveryAccountScope ?: return NaviampCoreImmediateCommandResult.Handled()
                val key = discoveryGenreKey(action.tag)
                val approved = if (action.blocked) screen.genres else screen.blockedGenres.tags
                if (screen.loading || screen.blockedGenres.status != ProviderDiscoveryStatus.Ready ||
                    approved.none { discoveryGenreKey(it) == key } || (account to key) in pendingGenres)
                    return NaviampCoreImmediateCommandResult.Handled()
                pendingGenres[account to key] = action.blocked
                change { it.copy(genreMutation = ProviderDiscoveryOperationStatus.Uncertain, uncertainGenres = it.uncertainGenres + (key to action.blocked)) }
            }
            is DiscoveryBrowseAction.ChartSection -> {
                stopPreview()
                val wasGenre = state()?.browser?.chartGenreId != null
                if (wasGenre) { revision++; read?.cancel() }
                change { it.copy(chartSection = action.section, chartGenreId = null, firstVisibleItem = 0, firstVisibleOffset = 0,
                    loading = if (wasGenre) false else it.loading, status = if (wasGenre) it.charts.status else it.status) }
                return NaviampCoreImmediateCommandResult.Handled()
            }
            is DiscoveryBrowseAction.ChartGenre -> { revision++; read?.cancel(); stopPreview(); change { it.copy(chartGenreId = action.id, firstVisibleItem = 0, firstVisibleOffset = 0) } }
            is DiscoveryBrowseAction.ChartSearch -> {
                state()?.browser?.let { backStack += it }
                revision++; read?.cancel(); stopPreview()
                change { it.copy(page = DiscoveryBrowsePage.Search, query = action.query, scope = action.scope, catalog = ProviderCatalogPage(), submittedQuery = "", firstVisibleItem = 0, firstVisibleOffset = 0) }
            }
            is DiscoveryBrowseAction.ChartPreview -> if (chartSong(action.key)?.previewId == null) return NaviampCoreImmediateCommandResult.Handled()
            DiscoveryBrowseAction.SaveChart -> {
                val charts = state()?.browser?.charts ?: return NaviampCoreImmediateCommandResult.Handled()
                val key = charts.playlistId ?: return NaviampCoreImmediateCommandResult.Handled()
                val account = provider()?.discoveryAccountScope ?: return NaviampCoreImmediateCommandResult.Handled()
                if (!charts.canSave || charts.libraryPlaylistId != null || state()?.browser?.loading == true || (account to key) in uncertainSaves)
                    return NaviampCoreImmediateCommandResult.Handled()
                uncertainSaves += account to key
                change { it.copy(chartSave = ProviderDiscoveryChartSave(ProviderDiscoveryOperationStatus.Uncertain)) }
            }
            is DiscoveryBrowseAction.Query -> {
                revision++; read?.cancel()
                change { it.copy(query = action.query.take(1000), loading = false, catalog = ProviderCatalogPage(), submittedQuery = "", firstVisibleItem = 0, firstVisibleOffset = 0) }
                return NaviampCoreImmediateCommandResult.Handled()
            }
            is DiscoveryBrowseAction.Scope -> {
                revision++; read?.cancel()
                change { it.copy(scope = action.scope, loading = false, catalog = ProviderCatalogPage(), submittedQuery = "", firstVisibleItem = 0, firstVisibleOffset = 0) }
                return NaviampCoreImmediateCommandResult.Handled()
            }
            DiscoveryBrowseAction.External -> {
                provider()?.discovery?.catalogUrl?.let(openExternal)
                return NaviampCoreImmediateCommandResult.Handled()
            }
            is DiscoveryBrowseAction.Album -> if (state()?.browser?.catalog?.albums?.none { it == action.album } != false)
                return NaviampCoreImmediateCommandResult.Handled()
            DiscoveryBrowseAction.Search -> { revision++; read?.cancel() }
            DiscoveryBrowseAction.Close -> {
                revision++; read?.cancel(); stopPreview()
                when {
                    state()?.browser?.chartGenreId != null -> change { it.copy(chartGenreId = null, status = it.charts.status, loading = false) }
                    backStack.isNotEmpty() -> { val previous = backStack.removeAt(backStack.lastIndex)
                        val latest = state()?.browser
                        update { it.copy(browser = previous.copy(loading = false, recommendations = latest?.recommendations ?: previous.recommendations,
                            seeds = latest?.seeds ?: previous.seeds, genres = latest?.genres ?: previous.genres,
                            blockedGenres = latest?.blockedGenres ?: previous.blockedGenres, uncertainGenres = latest?.uncertainGenres.orEmpty(),
                            total = latest?.total, nextOffset = latest?.nextOffset, charts = latest?.charts ?: previous.charts)) } }
                    else -> reset()
                }
                return NaviampCoreImmediateCommandResult.Handled()
            }
            is DiscoveryBrowseAction.View -> { change { it.copy(view = action.view) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Sort -> { change { it.copy(sort = action.sort, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Filter -> { change { it.copy(filter = action.filter, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Genre -> { change { it.copy(genre = action.genre, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Seed -> { change { it.copy(seed = action.seed, firstVisibleItem = 0, firstVisibleOffset = 0) }; return NaviampCoreImmediateCommandResult.Handled() }
            is DiscoveryBrowseAction.Viewport -> { change { it.copy(firstVisibleItem = action.index.coerceAtLeast(0), firstVisibleOffset = action.offset.coerceAtLeast(0)) }; return NaviampCoreImmediateCommandResult.Handled() }
            DiscoveryBrowseAction.Refresh -> { revision++; read?.cancel() }
            DiscoveryBrowseAction.More -> if (state()?.browser?.loading != false || (if (state()?.browser?.page == DiscoveryBrowsePage.Search) state()?.browser?.catalog?.nextOffset else state()?.browser?.nextOffset) == null) return NaviampCoreImmediateCommandResult.Handled()
        }
        scheduled += action to revision
        change { it.copy(loading = true) }
        return NaviampCoreImmediateCommandResult.Deferred
    }
    private suspend fun refreshBlockedGenres(native: ProviderDiscovery, owner: MediaProvider, current: () -> Boolean) {
        val result = native.blockedGenres()
        if (!current()) return
        val account = owner.discoveryAccountScope
        if (result.status == ProviderDiscoveryStatus.Ready) {
            pendingGenres.entries.filter { it.key.first == account }.toList().forEach { (key, desired) ->
                if (result.tags.any { discoveryGenreKey(it) == key.second } == desired) pendingGenres.remove(key)
            }
        }
        val pending = pendingGenres.filterKeys { it.first == account }.mapKeys { it.key.second }
        change { it.copy(blockedGenres = if (result.status == ProviderDiscoveryStatus.Ready) result else it.blockedGenres.copy(status = result.status),
            uncertainGenres = pending, status = if (it.page == DiscoveryBrowsePage.BlockedGenres) result.status else it.status,
            genreMutation = if (it.genreMutation == ProviderDiscoveryOperationStatus.Uncertain && pending.isEmpty()) ProviderDiscoveryOperationStatus.Saved else it.genreMutation) }
    }
    private fun chartSong(key: String): DiscoveryChartSong? {
        val screen = state()?.browser ?: return null
        return (if (screen.chartGenreId != null) screen.genreChart?.songs else screen.charts.songs)?.firstOrNull { it.key == key }
    }
    private fun change(transform: (DiscoveryBrowserState) -> DiscoveryBrowserState) = update { current -> current.copy(browser = current.browser?.let(transform)) }
    suspend fun execute(action: DiscoveryBrowseAction) {
        val index = scheduled.indexOfFirst { it.first === action }; if (index < 0) return
        val token = scheduled.removeAt(index).second; if (token != revision) return
        val owner = provider()
        val native = owner?.discovery
        if (owner == null || native == null) { change { it.copy(loading = false, status = ProviderDiscoveryStatus.Unsupported) }; return }
        val task = currentCoroutineContext()[Job]
        read = task
        fun current() = token == revision && provider() === owner
        try {
            if (action is DiscoveryBrowseAction.BlockGenre) {
                val result = native.setGenreBlocked(action.tag, action.blocked)
                if (!current()) return
                val key = discoveryGenreKey(action.tag)
                if (result.status != ProviderDiscoveryOperationStatus.Uncertain) pendingGenres.remove(owner.discoveryAccountScope to key)
                change { it.copy(genreMutation = result.status,
                    blockedGenres = result.tags?.let { tags -> ProviderDiscoveryBlockedGenres(ProviderDiscoveryStatus.Ready, tags) } ?: it.blockedGenres,
                    uncertainGenres = if (result.status == ProviderDiscoveryOperationStatus.Uncertain) it.uncertainGenres else it.uncertainGenres - key) }
                if (result.status != ProviderDiscoveryOperationStatus.Saved) return
                refreshHome(owner)
                if (!current()) return
                // Continue through the complete bounded recommendation refresh after confirmed success.
            }
            if (state()?.browser?.page == DiscoveryBrowsePage.BlockedGenres && action !is DiscoveryBrowseAction.BlockGenre) {
                refreshBlockedGenres(native, owner, ::current)
                if (!current()) return
                val context = native.recommendations(0, 1)
                if (current() && context.status == ProviderDiscoveryStatus.Ready) change { it.copy(genres = context.genres) }
                return
            }
            if (action is DiscoveryBrowseAction.ChartPreview) {
                val song = chartSong(action.key) ?: return
                val preview = song.previewId ?: return
                playPreview(ProviderDiscoverySample(preview.value, song.title, song.albumTitle, song.imageUrl).playbackTrack(song.artistName)); return
            }
            if (action == DiscoveryBrowseAction.SaveChart) {
                val id = state()?.browser?.charts?.playlistId ?: return
                val result = native.saveChartPlaylist(id)
                if (!current()) return
                change { it.copy(chartSave = result, charts = it.charts.copy(libraryPlaylistId = result.playlistId ?: it.charts.libraryPlaylistId)) }
                if (result.status in setOf(ProviderDiscoveryOperationStatus.Rejected, ProviderDiscoveryOperationStatus.Forbidden, ProviderDiscoveryOperationStatus.Unsupported)) uncertainSaves -= owner.discoveryAccountScope to id
                return
            }
            if (state()?.browser?.page == DiscoveryBrowsePage.Trending) {
                val genreId = state()?.browser?.chartGenreId
                if (genreId != null) {
                    val result = native.genreChart(genreId)
                    if (current()) change { it.copy(status = result.status, genreChart = if (result.status == ProviderDiscoveryStatus.Ready) result else it.genreChart?.takeIf { old -> old.id == genreId }) }
                } else {
                    val result = native.charts()
                    if (current()) change { it.copy(status = result.status, charts = if (result.status == ProviderDiscoveryStatus.Ready) result else it.charts) }
                }
                return
            }
            if (action is DiscoveryBrowseAction.Album) { openAlbum(action.album); return }
            if (state()?.browser?.page == DiscoveryBrowsePage.Search) {
                val screen = state()?.browser ?: return
                val query = screen.query.trim()
                if (query.isBlank()) { change { it.copy(catalog = ProviderCatalogPage(ProviderDiscoveryStatus.Ready, totalCount = 0), submittedQuery = "", status = ProviderDiscoveryStatus.Ready) }; return }
                val offset = if (action == DiscoveryBrowseAction.More) screen.catalog.nextOffset ?: return else 0
                val result = native.searchCatalog(query, screen.scope, offset, 24)
                if (!current() || state()?.browser?.query?.trim() != query || state()?.browser?.scope != screen.scope) return
                if (result.status != ProviderDiscoveryStatus.Ready || result.offset != offset || result.nextOffset?.let { it <= offset } == true) {
                    change { it.copy(status = if (result.status == ProviderDiscoveryStatus.Ready) ProviderDiscoveryStatus.Unavailable else result.status) }; return
                }
                change { it.copy(submittedQuery = query, status = result.status, catalog = result.copy(
                    artists = ((if (offset == 0) emptyList() else screen.catalog.artists) + result.artists).distinctBy { it.id },
                    albums = ((if (offset == 0) emptyList() else screen.catalog.albums) + result.albums).distinctBy { it.release.id })) }
                return
            }
            val more = action == DiscoveryBrowseAction.More
            if (!more) refreshBlockedGenres(native, owner, ::current)
            if (!current()) return
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
            if (read === task) read = null
            if (current()) change { it.copy(loading = false) }
        }
    }
}
