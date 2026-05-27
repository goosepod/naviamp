package app.naviamp.domain.provider

import app.naviamp.domain.AlbumDetails
import app.naviamp.domain.Track

const val SearchDebounceMillis: Long = 250
const val SearchResultLimit: Int = 20

fun normalizedSearchQuery(query: String): String? =
    query.trim().takeIf { it.isNotEmpty() }

fun MediaSearchResults.totalCount(): Int =
    artists.size + albums.size + tracks.size

fun allKnownTracks(
    searchResults: MediaSearchResults,
    albumDetail: AlbumDetails?,
): List<Track> =
    albumDetail?.tracks ?: searchResults.tracks
