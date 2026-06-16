package app.naviamp.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.SearchSettings
import app.naviamp.domain.cache.ProviderResponseCacheRepository
import app.naviamp.domain.cache.ProviderResponseService
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.provider.MediaSearchResults
import app.naviamp.domain.provider.SearchDebounceMillis
import app.naviamp.domain.provider.SearchSessionController
import app.naviamp.domain.search.offlineTrackSearchResults
import app.naviamp.domain.settings.CacheSettings
import kotlinx.coroutines.delay

class DesktopSearchController(
    private val settingsStore: DesktopSettingsStore,
    providerResponseCacheRepository: ProviderResponseCacheRepository,
    private val provider: () -> MediaProvider?,
    private val cacheSettings: () -> CacheSettings,
    private val downloadedTracks: () -> List<app.naviamp.domain.Track>,
    initialQuery: String,
) {
    var query by mutableStateOf(initialQuery)
        private set
    var results by mutableStateOf(MediaSearchResults())
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var searching by mutableStateOf(false)
        private set

    private val providerResponseService = ProviderResponseService(providerResponseCacheRepository)
    private val searchSessionController = SearchSessionController(
        provider = provider,
        setResults = { searchResults -> results = searchResults },
        setStatus = { searchStatus -> status = searchStatus },
        setSearching = { isSearching -> searching = isSearching },
        emptyStatus = null,
        matchedStatus = null,
    ) { activeProvider, searchQuery, limit ->
        providerResponseService.search(activeProvider, searchQuery, limit = limit)
    }

    fun updateQuery(query: String) {
        this.query = query
        settingsStore.saveSearchSettings(SearchSettings(query = query))
    }

    fun clearSearch() {
        query = ""
        results = MediaSearchResults()
        status = null
        searching = false
        settingsStore.saveSearchSettings(SearchSettings(query = ""))
    }

    fun updateResults(searchResults: MediaSearchResults) {
        results = searchResults
    }

    suspend fun loadSearchResults(query: String) {
        if (cacheSettings().offlineModeEnabled) {
            results = offlineTrackSearchResults(downloadedTracks(), query)
            status = if (results.isEmpty && query.isNotBlank()) {
                "No downloaded tracks matched."
            } else {
                "Offline Mode: downloaded tracks only"
            }
            searching = false
            return
        }
        searchSessionController.load(query) {
            delay(SearchDebounceMillis)
        }
    }
}
