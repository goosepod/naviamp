package app.naviamp.presentation

import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.SearchDisconnectedStatus
import app.naviamp.domain.provider.normalizedSearchQuery
import app.naviamp.domain.provider.searchResultsUpdate
import app.naviamp.ui.NaviampLibrarySyncStatusUi
import app.naviamp.ui.NaviampSearchScreenUi
import app.naviamp.ui.SharedSearchResultsUi
import app.naviamp.ui.toSharedMediaItemUi
import app.naviamp.ui.toSharedSearchResultsUi

fun interface NaviampCoreMediaProviderSource {
    fun current(): MediaProvider?
}

/** Owns Search and Library state, provider transactions, paging, and stale-result rejection. */
class NaviampCoreCatalogController(
    private val stateStore: NaviampCoreStateStore,
    private val providerSource: NaviampCoreMediaProviderSource,
    private val libraryPageSize: Int = 50,
    private val mediaRegistry: NaviampCoreMediaRegistry = NaviampCoreMediaRegistry(),
) : NaviampCoreCommandController {
    private var searchGeneration = 0L
    private var libraryGeneration = 0L
    private var libraryNextRequest: MediaPageRequest? = MediaPageRequest(limit = libraryPageSize)
    private var libraryLoadingGeneration: Long? = null
    private var jumpGeneration = 0L

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult = when (command) {
        is NaviampCoreCommand.Search.ChangeQuery -> handled { updateSearchQuery(command.query) }
        NaviampCoreCommand.Search.Clear -> handled(::clearSearch)
        NaviampCoreCommand.Search.Submit -> NaviampCoreImmediateCommandResult.Deferred
        is NaviampCoreCommand.Library.ChangeQuery -> handled { updateLibraryQuery(command.query) }
        is NaviampCoreCommand.Library.JumpToLetter -> handled { publishLibraryJump(command.letter) }
        NaviampCoreCommand.Library.Refresh,
        NaviampCoreCommand.Library.LoadMore,
        -> NaviampCoreImmediateCommandResult.Deferred
        else -> NaviampCoreImmediateCommandResult.Unhandled
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            NaviampCoreCommand.Search.Submit -> search()
            NaviampCoreCommand.Library.Refresh -> refreshLibrary()
            NaviampCoreCommand.Library.LoadMore -> loadMoreLibrary()
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

    private suspend fun refreshLibrary() {
        val generation = ++libraryGeneration
        val request = MediaPageRequest(limit = libraryPageSize)
        libraryNextRequest = request
        loadLibraryPage(request, replace = true, generation = generation)
    }

    private suspend fun loadMoreLibrary() {
        val request = libraryNextRequest ?: return
        val generation = libraryGeneration
        if (libraryLoadingGeneration == generation) return
        loadLibraryPage(request, replace = request.offset == 0, generation = generation)
    }

    private suspend fun loadLibraryPage(
        request: MediaPageRequest,
        replace: Boolean,
        generation: Long,
    ) {
        val provider = providerSource.current()
        if (provider == null) {
            publishLibraryStatus(SearchDisconnectedStatus, loading = false)
            return
        }
        libraryLoadingGeneration = generation
        publishLibraryStatus("Loading library...", loading = true)
        val query = stateStore.state.value.shell.library.query
        runCatching {
            if (query.isBlank()) provider.artistsPage(request) else provider.searchArtistsPage(query.trim(), request)
        }.onSuccess { page ->
            if (generation != libraryGeneration) return@onSuccess
            mediaRegistry.updateLibraryArtists(page.items, replace)
            val mapped = page.items.map { artist ->
                artist.toSharedMediaItemUi(
                    coverArtUrl = { id -> id?.let { provider.coverArtUrl(it) } },
                    canFavorite = provider.capabilities.supportsArtistFavorites,
                )
            }
            stateStore.updateShell { shell ->
                shell.copy(
                    library = shell.library.copy(
                        artists = if (replace) mapped else (shell.library.artists + mapped).distinctBy { it.id },
                        syncStatus = NaviampLibrarySyncStatusUi(),
                    ),
                )
            }
            libraryNextRequest = page.nextRequest
        }.onFailure { cause ->
            if (generation == libraryGeneration) {
                publishLibraryStatus(cause.message ?: "Could not load library.", loading = false)
            }
        }
        if (generation == libraryGeneration) {
            libraryLoadingGeneration = null
            val currentStatus = stateStore.state.value.shell.library.syncStatus.message
            if (currentStatus == "Loading library...") publishLibraryStatus(null, loading = false)
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
        stateStore.updateShell { shell -> shell.copy(library = shell.library.copy(query = query)) }
    }

    private fun publishLibraryStatus(message: String?, loading: Boolean) {
        stateStore.updateShell { shell ->
            shell.copy(
                library = shell.library.copy(
                    syncStatus = NaviampLibrarySyncStatusUi(message = message, isSyncing = loading),
                ),
            )
        }
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

    private inline fun handled(action: () -> Unit): NaviampCoreImmediateCommandResult {
        action()
        return NaviampCoreImmediateCommandResult.Handled()
    }
}
