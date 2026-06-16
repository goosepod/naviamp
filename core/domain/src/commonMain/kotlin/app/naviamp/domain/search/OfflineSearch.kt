package app.naviamp.domain.search

import app.naviamp.domain.Track
import app.naviamp.domain.provider.MediaSearchResults

fun offlineTrackSearchResults(
    downloadedTracks: List<Track>,
    query: String,
    limit: Int = 50,
): MediaSearchResults {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank()) return MediaSearchResults()
    val matchedTracks = downloadedTracks
        .asSequence()
        .distinctBy { it.id.value }
        .filter { track -> track.matchesOfflineSearch(trimmedQuery) }
        .take(limit)
        .toList()
    return MediaSearchResults(tracks = matchedTracks)
}

private fun Track.matchesOfflineSearch(query: String): Boolean =
    title.contains(query, ignoreCase = true) ||
        artistName.contains(query, ignoreCase = true) ||
        albumTitle.orEmpty().contains(query, ignoreCase = true)
