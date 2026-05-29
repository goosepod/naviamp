package app.naviamp.desktop

import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.normalizedSearchQuery
import app.naviamp.domain.provider.searchResultsUpdate
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
    fun updateQuery(query: String) {
        setQuery(query)
        settingsStore.saveSearchSettings(SearchSettings(query = query))
    }

    suspend fun loadSearchResults(query: String) {
        val activeProvider = provider()
        val normalizedQuery = normalizedSearchQuery(query)
        if (normalizedQuery == null) {
            setResults(MediaSearchResults())
            setStatus(if (activeProvider == null) SearchDisconnectedStatus else null)
            setSearching(false)
            return
        }
        if (activeProvider == null) {
            setResults(MediaSearchResults())
            setStatus(SearchDisconnectedStatus)
            setSearching(false)
            return
        }

        delay(SearchDebounceMillis)
        setSearching(true)
        setStatus(null)
        val update = searchResultsUpdate(
            query = normalizedQuery,
            emptyStatus = null,
            matchedStatus = null,
        ) { searchQuery, limit ->
            sessionCache.search(activeProvider, searchQuery, limit = limit)
        }
        setResults(update.results)
        setStatus(update.status)
        setSearching(false)
    }

    private companion object {
        const val SearchDisconnectedStatus = "Connect to Navidrome to search."
    }
}
