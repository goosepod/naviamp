package app.naviamp.domain.media

import app.naviamp.domain.Album
import app.naviamp.domain.settings.AlbumSortOrder

enum class AlbumReleaseSection(val label: String) {
    Albums("Albums"),
    Eps("EPs"),
    Singles("Singles"),
    Live("Live Releases"),
    Compilations("Compilations"),
    Remixes("Remixes"),
    Soundtracks("Soundtracks"),
    Other("Other Releases"),
}

data class AlbumReleaseSectionGroup(
    val section: AlbumReleaseSection,
    val albums: List<Album>,
)

fun List<Album>.groupedByReleaseSection(): List<AlbumReleaseSectionGroup> {
    if (isEmpty()) return emptyList()
    val grouped = groupBy(Album::releaseSection)
    return AlbumReleaseSection.entries.mapNotNull { section ->
        grouped[section]
            ?.takeIf { it.isNotEmpty() }
            ?.let { albums -> AlbumReleaseSectionGroup(section, albums) }
    }
}

fun Album.releaseSection(): AlbumReleaseSection {
    val types = releaseTypes.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    return when {
        types.any { "soundtrack" in it || "score" in it } -> AlbumReleaseSection.Soundtracks
        types.any { "live" in it } -> AlbumReleaseSection.Live
        types.any { "remix" in it } -> AlbumReleaseSection.Remixes
        types.any { "compilation" in it } -> AlbumReleaseSection.Compilations
        types.any { it == "ep" || "extended play" in it } -> AlbumReleaseSection.Eps
        types.any { "single" in it } -> AlbumReleaseSection.Singles
        types.isEmpty() || types.any { "album" in it } -> AlbumReleaseSection.Albums
        else -> AlbumReleaseSection.Other
    }
}

fun List<Album>.sortedForAlbumDisplay(order: AlbumSortOrder): List<Album> =
    when (order) {
        AlbumSortOrder.ReleaseYearAscending -> sortedWith(
            compareBy<Album> { it.releaseYear ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortOrder.ReleaseYearDescending -> sortedWith(
            compareByDescending<Album> { it.releaseYear ?: Int.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        AlbumSortOrder.Title -> sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, Album::title)
                .thenBy { it.releaseYear ?: Int.MAX_VALUE },
        )
    }
