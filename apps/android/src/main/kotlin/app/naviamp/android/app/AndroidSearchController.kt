package app.naviamp.android

import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.SearchSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidSearchController(
    private val state: AndroidAppState,
) {
    private val searchSessionController = SearchSessionController(
        provider = { state.provider },
        setResults = { results ->
            state.contentState = state.contentState.clearDetails().copy(searchResults = results)
            state.tracks = results.tracks
        },
        setStatus = { searchStatus -> state.status = searchStatus.orEmpty() },
        disconnectedStatus = null,
        loadingStatus = "Searching...",
        clearWhenProviderMissing = false,
    ) { activeProvider: MediaProvider, searchQuery, limit ->
        activeProvider.search(searchQuery, limit = limit)
    }

    suspend fun load(query: String, debounce: Boolean = false) {
        searchSessionController.load(query) {
            if (debounce) {
                delay(SearchDebounceMillis)
            }
        }
    }

    fun launchSearch(scope: CoroutineScope, query: String = state.query) {
        scope.launch {
            load(query)
        }
    }
}
