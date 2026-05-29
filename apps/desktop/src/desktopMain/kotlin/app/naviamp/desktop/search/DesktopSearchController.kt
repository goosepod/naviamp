package app.naviamp.desktop

import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.SearchSessionController
import kotlinx.coroutines.delay

class DesktopSearchController(
    private val settingsStore: DesktopSettingsStore,
    private val sessionCache: DesktopCache,
    private val provider: () -> MediaProvider?,
    private val setQuery: (String) -> Unit,
    private val setResults: (MediaSearchResults) -> Unit,
    private val setStatus: (String?) -> Unit,
    private val setSearching: (Boolean) -> Unit,
) {
    private val searchSessionController = SearchSessionController(
        provider = provider,
        setResults = setResults,
        setStatus = setStatus,
        setSearching = setSearching,
        emptyStatus = null,
        matchedStatus = null,
    ) { activeProvider, searchQuery, limit ->
        sessionCache.search(activeProvider, searchQuery, limit = limit)
    }

    fun updateQuery(query: String) {
        setQuery(query)
        settingsStore.saveSearchSettings(SearchSettings(query = query))
    }

    suspend fun loadSearchResults(query: String) {
        searchSessionController.load(query) {
            delay(SearchDebounceMillis)
        }
    }
}
