package app.naviamp.android

import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.SearchSessionController
import app.naviamp.domain.search.offlineTrackSearchResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidSearchController(
    private val state: AndroidAppState,
    private val storage: AndroidStorageDependencies,
) {
    private val providerResponseService = ProviderResponseService(storage)
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
        providerResponseService.search(activeProvider, searchQuery, limit = limit)
    }

    suspend fun load(query: String, debounce: Boolean = false) {
        if (state.cacheSettings.offlineModeEnabled) {
            val downloads = state.activeSourceId
                ?.let(storage::downloadedTracks)
                .orEmpty()
                .map { it.track }
            val offlineResults = offlineTrackSearchResults(downloads, query)
            state.contentState = state.contentState.clearDetails().copy(searchResults = offlineResults)
            state.tracks = offlineResults.tracks
            state.status = if (offlineResults.isEmpty && query.isNotBlank()) {
                "No downloaded tracks matched."
            } else {
                "Offline Mode: downloaded tracks only"
            }
            return
        }
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
