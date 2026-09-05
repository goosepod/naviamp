package app.naviamp.presentation

import app.naviamp.domain.Album
import app.naviamp.domain.library.AlbumLibraryIndex
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.AlphabeticalLibraryKind
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
import kotlinx.coroutines.CancellationException

/** Owns Search and Library state, provider transactions, paging, and stale-result rejection. */
class NaviampCoreCatalogController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val libraryPageSize: Int = 50,
    private val libraryGenreRefresh: NaviampCoreLibraryGenreRefreshPort = NaviampCoreLibraryGenreRefreshPort { },
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
    private val albumIndex: AlbumLibraryIndex? = null,
) : NaviampCoreCommandController {
    private var searchGeneration = 0L
    private data class LibraryLoadState(
        var generation: Long = 0L,
        var nextRequest: MediaPageRequest? = null,
        var loadingGeneration: Long? = null,
        var loadedSource: Pair<String, String>? = null,
        var completeAlbumCatalog: Boolean = false,
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
            is NaviampCoreCommand.Library.ChangeView -> {
                val provider = providerSource.current()
                if (stateStore.state.value.shell.library.selectedView == command.view &&
                    (command.view == NaviampLibraryView.Albums && albumIndex != null ||
                        libraryLoads.getValue(command.view).loadedSource != provider?.let { it.id.value to it.cacheNamespace })) {
                    refreshLibrary(command.view)
                }
            }
            is NaviampCoreCommand.Library.ChangeQuery -> {
                if (stateStore.state.value.shell.library.selectedView != NaviampLibraryView.Albums || albumIndex == null) refreshLibrary()
            }
            NaviampCoreCommand.Library.Refresh -> refreshLibrary(forceAlbums = true)
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
        forceAlbums: Boolean = false,
    ) {
        if (view == NaviampLibraryView.Albums && albumIndex != null) {
            refreshAlbumIndex(forceAlbums)
            return
        }
        val load = libraryLoads.getValue(view)
        val generation = ++load.generation
        load.loadedSource = null
        updateLibraryCatalog(view) { it.copy(pendingJump = null) }
        val request = MediaPageRequest(limit = libraryPageSize)
        load.nextRequest = request
        loadLibraryPage(view, request, replace = true, generation = generation)
    }

    private suspend fun refreshAlbumIndex(force: Boolean) {
        val index = albumIndex ?: return
        val provider = providerSource.current() ?: return
        val view = NaviampLibraryView.Albums
        val load = libraryLoads.getValue(view)
        if (load.loadingGeneration == load.generation) return
        val generation = ++load.generation
        val sourceKey = provider.id.value to provider.cacheNamespace
        val scope = index.scope(provider)
        fun current() = isCurrentLibraryLoad(load, generation, sourceKey)
        fun publish(albums: List<Album>) {
            mediaRegistry.updateLibraryAlbums(albums, replace = true)
            val items = albums.map { album -> album.toSharedMediaItemUi(
                coverArtUrl = { id -> id?.let(provider::coverArtUrl) },
                canFavorite = provider.capabilities.supportsAlbumFavorites,
            ) }
            updateLibraryCatalog(view) { it.copy(items = items) }
        }
        val cached = index.snapshot(scope)
        load.completeAlbumCatalog = cached != null
        if (cached != null) publish(cached.albums)
        load.nextRequest = null
        load.loadedSource = sourceKey
        if (!force && cached != null && index.isFresh(cached)) {
            updateLibraryCatalog(view) { it.copy(syncStatus = NaviampLibrarySyncStatusUi()) }
            return
        }
        load.loadingGeneration = generation
        updateLibraryCatalog(view) { it.copy(syncStatus = NaviampLibrarySyncStatusUi(isSyncing = true, albumIndexCount = 0)) }
        try {
            val refreshed = index.refresh(scope, provider, ::current) { partial ->
                if (cached == null) publish(partial)
                updateLibraryCatalog(view) { it.copy(syncStatus = NaviampLibrarySyncStatusUi(
                    isSyncing = true, albumIndexCount = partial.size,
                )) }
            } ?: return
            if (!current()) return
            publish(refreshed.albums)
            load.completeAlbumCatalog = true
            updateLibraryCatalog(view) { it.copy(syncStatus = NaviampLibrarySyncStatusUi()) }
            stateStore.state.value.shell.library.albums.pendingJump?.let { letter ->
                if (stateStore.state.value.shell.library.selectedView == view) publishLibraryJump(letter)
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            if (current()) updateLibraryCatalog(view) { it.copy(
                syncStatus = NaviampLibrarySyncStatusUi(albumIndexFailed = true),
            ) }
        } finally {
            if (current()) {
                load.loadingGeneration = null
                updateLibraryCatalog(view) { it.copy(pendingJump = null, syncStatus = it.syncStatus.copy(isSyncing = false, albumIndexCount = null)) }
            }
        }
    }

    suspend fun refreshAfterConnection() {
        libraryLoads.values.forEach { it.generation++; it.loadedSource = null; it.loadingGeneration = null }
        stateStore.updateShell { it.copy(library = app.naviamp.ui.NaviampLibraryScreenUi(selectedView = it.library.selectedView)) }
        mediaRegistry.updateLibraryArtists(emptyList(), true)
        mediaRegistry.updateLibraryAlbums(emptyList(), true)
        mediaRegistry.updateLibraryTracks(emptyList(), true)
        refreshLibrary()
    }

    private suspend fun loadMoreLibrary() {
        val view = stateStore.state.value.shell.library.selectedView
        if (view == NaviampLibraryView.Albums && albumIndex != null) return
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
    ): Boolean {
        val provider = providerSource.current()
        if (provider == null) {
            publishLibraryStatus(view, SearchDisconnectedStatus, loading = false)
            return false
        }
        val load = libraryLoads.getValue(view)
        val sourceKey = provider.id.value to provider.cacheNamespace
        load.loadingGeneration = generation
        publishLibraryStatus(view, "Loading library...", loading = true)
        val query = stateStore.state.value.shell.library.catalog(view).query
        val result = runCatching {
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
                    val merged = if (replace) {
                        page.items
                    } else {
                        (mediaRegistry.libraryTracks + page.items).distinctBy { it.id }
                    }
                    val ordered = if (page.alphabeticallySortedByTitle) {
                        merged
                    } else {
                        merged.sortedWith(
                            compareBy<app.naviamp.domain.Track>(
                                { it.title.lowercase() },
                                { it.title },
                                { it.id.value },
                            ),
                        )
                    }
                    mediaRegistry.updateLibraryTracks(ordered, replace = true)
                    val mapped = ordered.map { track ->
                        track.toSharedTrackRowUi(coverArtUrl = { id -> id?.let { provider.coverArtUrl(it) } })
                    }
                    updateLibraryCatalog(view) { current ->
                        current.copy(tracks = mapped, syncStatus = NaviampLibrarySyncStatusUi())
                    }
                    true to page.nextRequest
                }
            }
        }.onSuccess { (accepted, nextRequest) ->
            if (accepted && view == NaviampLibraryView.Artists && replace && query.isBlank()) {
                // Keep the browsable library available when a provider's optional genre endpoint fails.
                runCatching { libraryGenreRefresh.refresh() }
            }
            if (accepted && isCurrentLibraryLoad(load, generation, sourceKey)) {
                load.nextRequest = nextRequest
                load.loadedSource = sourceKey
            }
        }.onFailure { cause ->
            if (cause is CancellationException) {
                if (isCurrentLibraryLoad(load, generation, sourceKey)) {
                    load.loadingGeneration = null
                    publishLibraryStatus(view, null, loading = false)
                }
                throw cause
            }
            if (isCurrentLibraryLoad(load, generation, sourceKey)) {
                publishLibraryStatus(view, cause.message ?: "Could not load library.", loading = false)
            }
        }
        if (generation == load.generation) {
            load.loadingGeneration = null
            val currentStatus = stateStore.state.value.shell.library.catalog(view).syncStatus.message
            if (currentStatus == "Loading library...") publishLibraryStatus(view, null, loading = false)
        }
        return result.getOrNull()?.first == true && isCurrentLibraryLoad(load, generation, sourceKey)
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
        if (view == NaviampLibraryView.Albums && albumIndex != null) {
            updateLibraryCatalog(view) { it.copy(query = query, pendingJump = null, jumpFailed = false) }
            return
        }
        val load = libraryLoads.getValue(view)
        load.generation += 1
        load.loadedSource = null
        load.nextRequest = MediaPageRequest(limit = libraryPageSize)
        updateLibraryCatalog(view) { it.copy(query = query, pendingJump = null, jumpFailed = false) }
    }

    private fun selectLibraryView(view: NaviampLibraryView) {
        val previous = stateStore.state.value.shell.library.selectedView
        if (previous == view) return
        libraryLoads.getValue(previous).generation += 1
        updateLibraryCatalog(previous) { it.copy(pendingJump = null) }
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
                shell = state.shell.copy(
                    library = state.shell.library.copy(
                        jumpRequest = app.naviamp.ui.NaviampLibraryJumpUi(
                            state.shell.library.selectedView,
                            letter.uppercaseChar(),
                            ++jumpGeneration,
                        ),
                    ),
                ),
            )
        }
    }

    private suspend fun jumpToLetter(letter: Char) {
        val normalized = letter.uppercaseChar()
        val library = stateStore.state.value.shell.library
        val view = library.selectedView
        if (view == NaviampLibraryView.Albums && albumIndex != null) {
            val load = libraryLoads.getValue(view)
            if (load.loadingGeneration == load.generation && !load.completeAlbumCatalog) {
                updateLibraryCatalog(view) { it.copy(pendingJump = normalized) }
            } else {
                publishLibraryJump(normalized)
            }
            return
        }
        val query = library.catalog(view).query
        val provider = providerSource.current() ?: return
        val sourceKey = provider.id.value to provider.cacheNamespace
        val load = libraryLoads.getValue(view)
        if (library.catalog(view).pendingJump == normalized) return
        val generation = ++load.generation
        updateLibraryCatalog(view) { it.copy(pendingJump = normalized, jumpFailed = false) }
        fun current() = isCurrentLibraryLoad(load, generation, sourceKey) &&
            stateStore.state.value.shell.library.selectedView == view &&
            stateStore.state.value.shell.library.catalog(view).query == query
        try {
            if (query.isBlank()) {
                val kind = when (view) {
                    NaviampLibraryView.Artists -> null
                    NaviampLibraryView.Albums -> AlphabeticalLibraryKind.Albums
                    NaviampLibraryView.Songs -> AlphabeticalLibraryKind.Tracks
                }
                val offset = if (normalized != '#' && kind != null) {
                    provider.alphabeticalLibraryOffset(kind, normalized)
                } else null
                if (!current()) return
                val first = MediaPageRequest(offset = offset ?: 0, limit = libraryPageSize)
                load.nextRequest = first
                if (!loadLibraryPage(view, first, replace = true, generation = generation) || !current()) return
                if (offset == null && normalized != '#') {
                    var remainingPages = 1_000
                    while (current() && remainingPages-- > 0) {
                        val catalog = stateStore.state.value.shell.library.catalog(view)
                        // Providers may ignore articles or use sort tags ("The Aquabats" sorts
                        // under A). A displayed title beyond the requested letter does not prove
                        // that its server-sorted range has been reached.
                        val titles = if (view == NaviampLibraryView.Songs) {
                            catalog.tracks.map { it.title }
                        } else {
                            catalog.items.map { it.title }
                        }
                        if (titles.any { app.naviamp.domain.library.libraryTitleLetter(it) == normalized }) break
                        val next = load.nextRequest ?: break
                        if (!loadLibraryPage(view, next, replace = false, generation = generation)) return
                        if (load.nextRequest == next) return
                    }
                }
            }
            if (current()) publishLibraryJump(normalized)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            if (current()) updateLibraryCatalog(view) { it.copy(jumpFailed = true) }
        } finally {
            if (generation == load.generation) updateLibraryCatalog(view) { it.copy(pendingJump = null) }
        }
    }
    private inline fun handled(action: () -> Unit): NaviampCoreImmediateCommandResult {
        action()
        return NaviampCoreImmediateCommandResult.Handled()
    }
}
