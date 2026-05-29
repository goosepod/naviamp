package app.naviamp.domain.provider

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track

const val SearchDebounceMillis: Long = 250
const val SearchResultLimit: Int = 20
const val SearchDisconnectedStatus: String = "Connect to Navidrome to search."

data class SearchResultsUpdate(
    val results: MediaSearchResults,
    val status: String?,
)

fun normalizedSearchQuery(query: String): String? =
    query.trim().takeIf { it.isNotEmpty() }

fun MediaSearchResults.totalCount(): Int =
    artists.size + albums.size + tracks.size

fun searchResultsStatus(
    results: MediaSearchResults,
    emptyStatus: String? = "No matches found.",
    matchedStatus: ((Int) -> String?)? = { count -> "Found $count matches." },
): String? =
    if (results.isEmpty) {
        emptyStatus
    } else {
        matchedStatus?.invoke(results.totalCount())
    }

fun searchErrorStatus(error: Throwable): String =
    error.message ?: "Search failed."

suspend fun searchResultsUpdate(
    query: String,
    limit: Int = SearchResultLimit,
    emptyStatus: String? = "No matches found.",
    matchedStatus: ((Int) -> String?)? = { count -> "Found $count matches." },
    search: suspend (query: String, limit: Int) -> MediaSearchResults,
): SearchResultsUpdate =
    runCatching {
        val results = search(query, limit)
        SearchResultsUpdate(
            results = results,
            status = searchResultsStatus(
                results = results,
                emptyStatus = emptyStatus,
                matchedStatus = matchedStatus,
            ),
        )
    }.getOrElse { error ->
        SearchResultsUpdate(
            results = MediaSearchResults(),
            status = searchErrorStatus(error),
        )
    }

class SearchSessionController<Provider : Any>(
    private val provider: () -> Provider?,
    private val setResults: (MediaSearchResults) -> Unit,
    private val setStatus: (String?) -> Unit,
    private val setSearching: (Boolean) -> Unit = {},
    private val disconnectedStatus: String? = SearchDisconnectedStatus,
    private val loadingStatus: String? = null,
    private val clearWhenProviderMissing: Boolean = true,
    private val emptyStatus: String? = "No matches found.",
    private val matchedStatus: ((Int) -> String?)? = { count -> "Found $count matches." },
    private val search: suspend (provider: Provider, query: String, limit: Int) -> MediaSearchResults,
) {
    suspend fun load(query: String, debounce: suspend () -> Unit = {}) {
        val activeProvider = provider()
        val normalizedQuery = normalizedSearchQuery(query)
        if (normalizedQuery == null) {
            setResults(MediaSearchResults())
            if (activeProvider != null || disconnectedStatus != null) {
                setStatus(if (activeProvider == null) disconnectedStatus else null)
            }
            setSearching(false)
            return
        }
        if (activeProvider == null) {
            if (clearWhenProviderMissing) {
                setResults(MediaSearchResults())
            }
            disconnectedStatus?.let(setStatus)
            setSearching(false)
            return
        }

        debounce()
        setSearching(true)
        setStatus(loadingStatus)
        val update = searchResultsUpdate(
            query = normalizedQuery,
            emptyStatus = emptyStatus,
            matchedStatus = matchedStatus,
        ) { searchQuery, limit ->
            search(activeProvider, searchQuery, limit)
        }
        setResults(update.results)
        setStatus(update.status)
        setSearching(false)
    }
}

fun allKnownTracks(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks
