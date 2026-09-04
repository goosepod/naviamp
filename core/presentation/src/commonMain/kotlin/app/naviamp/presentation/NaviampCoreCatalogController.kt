package app.naviamp.presentation

import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.SearchDisconnectedStatus
import app.naviamp.domain.provider.normalizedSearchQuery
import app.naviamp.domain.provider.searchResultsUpdate
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampLibraryCatalogUi
import app.naviamp.ui.NaviampLibraryView
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedSearchResultsUi
import app.naviamp.ui.toSharedTrackRowUi

/** Owns Search and Library state, provider transactions, paging, and stale-result rejection. */
class NaviampCoreCatalogController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val libraryPageSize: Int = 50,
    private val libraryGenreRefresh: NaviampCoreLibraryGenreRefreshPort = NaviampCoreLibraryGenreRefreshPort { },
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
) : NaviampCoreCommandController {
    private var searchGeneration = 0L
    private data class LibraryLoadState(
        var generation: Long = 0L,
        var nextRequest: MediaPageRequest? = null,
        var loadingGeneration: Long? = null,
    )

    private val libraryLoads = NaviampLibraryView.entries.associateWith {
        LibraryLoadState(nextRequest = MediaPageRequest(limit = libraryPageSize))
    }
    private var jumpGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Search.ChangeQuery -> {
            updateSearchQuery(command.query)
            NaviampCoreImmediateCommandResult.Deferred
        }
        NaviampCoreCommand.Search.Clear -> handled(::clearSearch)
        NaviampCoreCommand.Search.Submit -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Library.ChangeView -> {
            selectLibraryView(command.view)
            NaviampCoreImmediateCommandResult.Deferred
        }
        is NaviampCoreCommand.Library.ChangeQuery -> {
            updateLibraryQuery(command.query)
            NaviampCoreImmediateCommandResult.Deferred
        }
        is NaviampCoreCommand.Library.JumpToLetter -> NaviampCoreImmediateCommandResult.Deferred
        NaviampCoreCommand.Library.Refresh,
        NaviampCoreCommand.Library.LoadMore,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            NaviampCoreCommand.Search.Submit,
            is NaviampCoreCommand.Search.ChangeQuery,
            -> search()
            is NaviampCoreCommand.Library.ChangeView -> refreshLibrary(command.view)
            is NaviampCoreCommand.Library.ChangeQuery -> refreshLibrary()
            NaviampCoreCommand.Library.Refresh -> refreshLibrary()
            NaviampCoreCommand.Library.LoadMore -> loadMoreLibrary()
            is NaviampCoreCommand.Library.JumpToLetter -> jumpToLetter(command.letter)
            else -> return null
        }
        return NaviampCoreCommandResult.Completed
    }

    private suspend fun search() {
        val generation = ++searchGeneration
        val query = stateStore.state.value.shell.search.query
        val normalized = normalizedSearchQuery(query)
        val provider = providerSource.current()
        if (normalized == null) {
            publishSearch(MediaSearchResults(), status = null, searching = false, provider = provider)
            return
        }
        if (provider == null) {
            publishSearch(MediaSearchResults(), SearchDisconnectedStatus, searching = false, provider = null)
            return
        }
        updateSearchState { it.copy(searching = true, status = "Searching...") }
        val update = searchResultsUpdate(normalized) { searchQuery, limit ->
            provider.search(searchQuery, limit)
        }
        if (generation != searchGeneration) return
        publishSearch(update.results, update.status, searching = false, provider = provider)
    }

    private suspend fun refreshLibrary(
        view: NaviampLibraryView = stateStore.state.value.shell.library.selectedView,
    ) {
        val load = libraryLoads.getValue(view)
        val generation = ++load.generation
        val request = MediaPageRequest(limit = libraryPageSize)
        load.nextRequest = request
        loadLibraryPage(view, request, replace = true, generation = generation)
    }

    suspend fun refreshAfterConnection() = refreshLibrary()

    private suspend fun loadMoreLibrary() {
        val view = stateStore.state.value.shell.library.selectedView
        val load = libraryLoads.getValue(view)
        val request = load.nextRequest ?: return
        val generation = load.generation
        if (load.loadingGeneration == generation) return
        loadLibraryPage(view, request, replace = request.offset == 0, generation = generation)
    }

    private suspend fun loadLibraryPage(
        view: NaviampLibraryView,
        request: MediaPageRequest,
        replace: Boolean,
        generation: Long,
    ) {
        val provider = providerSource.current()
        if (provider == null) {
            publishLibraryStatus(view, SearchDisconnectedStatus, loading = false)
            return
        }
        val load = libraryLoads.getValue(view)
        val sourceKey = provider.id.value to provider.cacheNamespace
        load.loadingGeneration = generation
        publishLibraryStatus(view, "Loading library...", loading = true)
        val query = stateStore.state.value.shell.library.catalog(view).query
        runCatching {
            when (view) {
                NaviampLibraryView.Artists -> {
                    val page = if (query.isBlank()) {
                        provider.artistsPage(request)
                    } else {
                        provider.searchArtistsPage(query.trim(), request)
                    }
                    if (!isCurrentLibraryLoad(load, generation, sourceKey)) return@runCatching false to null
                    mediaRegistry.updateLibraryArtists(page.items, replace)
                    val mapped = page.items.map { artist ->
                        artist.toSharedMediaItemUi(
                            coverArtUrl = { id -> id?.let { provider.coverArtUrl(it) } },
                            canFavorite = provider.capabilities.supportsArtistFavorites,
                        )
                    }
                    updateLibraryCatalog(view) { current ->
                        current.copy(items = mergeItems(current.items, mapped, replace), syncStatus = NaviampLibrarySyncStatusUi())
                    }
                    true to page.nextRequest
                }
                NaviampLibraryView.Albums -> {
                    val page = if (query.isBlank()) {
                        provider.albumsPage(request)
                    } else {
                        provider.searchAlbumsPage(query.trim(), request)
                    }
                    if (!isCurrentLibraryLoad(load, generation, sourceKey)) return@runCatching false to null
                    mediaRegistry.updateLibraryAlbums(page.items, replace)
                    val mapped = page.items.map { album ->
                        album.toSharedMediaItemUi(
                            coverArtUrl = { id -> id?.let { provider.coverArtUrl(it) } },
                            canFavorite = provider.capabilities.supportsAlbumFavorites,
                        )
                    }
                    updateLibraryCatalog(view) { current ->
                        current.copy(items = mergeItems(current.items, mapped, replace), syncStatus = NaviampLibrarySyncStatusUi())
                    }
                    true to page.nextRequest
                }
                NaviampLibraryView.Songs -> {
                    val page = if (query.isBlank()) {
                        provider.tracksPage(request)
                    } else {
                        provider.searchTracksPage(query.trim(), request)
                    }
                    if (!isCurrentLibraryLoad(load, generation, sourceKey)) return@runCatching false to null
                    mediaRegistry.updateLibraryTracks(page.items, replace)
                    val mapped = page.items.map { track ->
                        track.toSharedTrackRowUi(coverArtUrl = { id -> id?.let { provider.coverArtUrl(it) } })
                    }
                    updateLibraryCatalog(view) { current ->
                        current.copy(tracks = mergeTracks(current.tracks, mapped, replace), syncStatus = NaviampLibrarySyncStatusUi())
                    }
                    true to page.nextRequest
                }
            }
        }.onSuccess { (accepted, nextRequest) ->
            if (accepted && view == NaviampLibraryView.Artists && replace && query.isBlank()) {
                // Keep the browsable library available when a provider's optional genre endpoint fails.
                runCatching { libraryGenreRefresh.refresh() }
            }
            if (accepted) load.nextRequest = nextRequest
        }.onFailure { cause ->
            if (generation == load.generation) {
                publishLibraryStatus(view, cause.message ?: "Could not load library.", loading = false)
            }
        }
        if (generation == load.generation) {
            load.loadingGeneration = null
            val currentStatus = stateStore.state.value.shell.library.catalog(view).syncStatus.message
            if (currentStatus == "Loading library...") publishLibraryStatus(view, null, loading = false)
        }
    }

    private fun updateSearchQuery(query: String) {
        updateSearchState { it.copy(query = query) }
    }

    private fun clearSearch() {
        searchGeneration += 1
        updateSearchState { NaviampSearchScreenUi() }
    }

    private fun publishSearch(
        results: MediaSearchResults,
        status: String?,
        searching: Boolean,
        provider: MediaProvider?,
    ) {
        mediaRegistry.updateSearch(results)
        val mapped = provider?.let { active ->
            results.toSharedSearchResultsUi(
                coverArtUrl = { id -> id?.let(active::coverArtUrl) },
                canFavoriteArtists = active.capabilities.supportsArtistFavorites,
                canFavoriteAlbums = active.capabilities.supportsAlbumFavorites,
            )
        } ?: SharedSearchResultsUi()
        updateSearchState { current ->
            current.copy(results = mapped, status = status, searching = searching)
        }
    }

    private fun updateSearchState(transform: (NaviampSearchScreenUi) -> NaviampSearchScreenUi) {
        stateStore.updateShell { shell -> shell.copy(search = transform(shell.search)) }
    }

    private fun updateLibraryQuery(query: String) {
        val view = stateStore.state.value.shell.library.selectedView
        val load = libraryLoads.getValue(view)
        load.generation += 1
        load.nextRequest = MediaPageRequest(limit = libraryPageSize)
        updateLibraryCatalog(view) { it.copy(query = query) }
    }

    private fun selectLibraryView(view: NaviampLibraryView) {
        val previous = stateStore.state.value.shell.library.selectedView
        if (previous == view) return
        libraryLoads.getValue(previous).generation += 1
        stateStore.updateShell { shell -> shell.copy(library = shell.library.copy(selectedView = view)) }
    }

    private fun publishLibraryStatus(view: NaviampLibraryView, message: String?, loading: Boolean) {
        updateLibraryCatalog(view) { catalog ->
            catalog.copy(syncStatus = NaviampLibrarySyncStatusUi(message = message, isSyncing = loading))
        }
    }

    private fun updateLibraryCatalog(
        view: NaviampLibraryView,
        transform: (NaviampLibraryCatalogUi) -> NaviampLibraryCatalogUi,
    ) {
        stateStore.updateShell { shell ->
            val library = shell.library
            shell.copy(
                library = when (view) {
                    NaviampLibraryView.Artists -> library.copy(artists = transform(library.artists))
                    NaviampLibraryView.Albums -> library.copy(albums = transform(library.albums))
                    NaviampLibraryView.Songs -> library.copy(songs = transform(library.songs))
                },
            )
        }
    }

    private fun mergeItems(
        current: List<app.naviamp.ui.SharedMediaItemUi>,
        incoming: List<app.naviamp.ui.SharedMediaItemUi>,
        replace: Boolean,
    ) = if (replace) incoming else (current + incoming).distinctBy { it.id }

    private fun mergeTracks(
        current: List<app.naviamp.ui.SharedTrackRowUi>,
        incoming: List<app.naviamp.ui.SharedTrackRowUi>,
        replace: Boolean,
    ) = if (replace) incoming else (current + incoming).distinctBy { it.id }

    private fun isCurrentLibraryLoad(
        load: LibraryLoadState,
        generation: Long,
        sourceKey: Pair<String, String>,
    ): Boolean {
        val currentProvider = providerSource.current()
        return generation == load.generation &&
            currentProvider?.let { it.id.value to it.cacheNamespace } == sourceKey
    }

    private fun publishLibraryJump(letter: Char) {
        stateStore.update { state ->
            state.copy(
                viewport = state.viewport.copy(
                    libraryJump = NaviampCoreLibraryJumpRequest(letter.uppercaseChar(), ++jumpGeneration),
                ),
            )
        }
    }

    private suspend fun jumpToLetter(letter: Char) {
        val normalized = letter.uppercaseChar()
        val view = stateStore.state.value.shell.library.selectedView
        val library = stateStore.state.value.shell.library
        if (normalized != '#' && library.catalog(view).query.isBlank()) {
            var remainingPages = 1_000
            while (libraryLoads.getValue(view).nextRequest != null && remainingPages-- > 0) {
                val catalog = stateStore.state.value.shell.library.catalog(view)
                val lastTitle = (catalog.items.lastOrNull()?.title ?: catalog.tracks.lastOrNull()?.title).orEmpty()
                if (lastTitle.isNotBlank() && lastTitle.first().uppercaseChar() >= normalized) break
                loadMoreLibrary()
            }
        }
        publishLibraryJump(normalized)
    }

    private inline fun handled(action: () -> Unit): NaviampCoreImmediateCommandResult {
        action()
        return NaviampCoreImmediateCommandResult.Handled()
    }
}
