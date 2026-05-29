package app.naviamp.domain.provider

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track

const val SearchDebounceMillis: Long = 250
const val SearchResultLimit: Int = 20

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

fun allKnownTracks(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks
