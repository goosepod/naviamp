package app.naviamp.provider.navidrome

import app.naviamp.domain.Album
import kotlin.time.Instant

internal fun mergeSubsonicAlbumLists(
    folders: List<List<Album>>, type: String, limit: Int, descendingYears: Boolean = false,
): List<Album> {
    if (folders.size == 1) return folders.single().take(limit)
    // Round robin is the fallback when a server omits the fields needed for exact ranking.
    val albums = (0 until (folders.maxOfOrNull { it.size } ?: 0))
        .flatMap { index -> folders.mapNotNull { it.getOrNull(index) } }.distinctBy { it.id }
    return when (type) {
        "random" -> albums.shuffled()
        "newest" -> albums.sortedByDescending { it.recentlyAddedAtIso8601.timestamp() }
        "recent" -> albums.sortedByDescending { it.lastPlayedAtIso8601.timestamp() }
        "frequent" -> albums.sortedByDescending { it.playCount }
        "starred" -> albums.sortedByDescending { it.favoritedAtIso8601.timestamp() }
        "byYear" -> if (descendingYears) albums.sortedByDescending { it.releaseYear } else albums.sortedBy { it.releaseYear }
        "alphabeticalByName" -> albums.sortedBy { it.title.lowercase() }
        else -> albums
    }.take(limit)
}

private fun String?.timestamp(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
