package app.naviamp.domain.genremix

import app.naviamp.domain.Genre

class GenreMixBuilderService(
    private val genres: suspend (Long) -> List<Genre>,
) {
    suspend fun allGenres(limit: Int = GenreMixGenreLimit): List<Genre> =
        genres(limit.toLong()).genreMixSuggestions(emptyList(), limit)

    suspend fun searchSuggestions(
        query: String,
        selectedGenres: List<Genre>,
        limit: Int = GenreMixGenreLimit,
    ): List<Genre> {
        val allGenres = allGenres(limit)
        val filtered = query.trim().takeIf { it.isNotBlank() }?.let { trimmed ->
            allGenres.filter { genre -> genre.name.contains(trimmed, ignoreCase = true) }
        } ?: allGenres
        return filtered.genreMixSuggestions(selectedGenres, limit)
    }
}

fun List<Genre>.genreMixSuggestions(
    selectedGenres: List<Genre>,
    limit: Int = GenreMixGenreLimit,
): List<Genre> {
    val selectedNames = selectedGenres.map { it.name.lowercase() }.toSet()
    return distinctBy { it.name.lowercase() }
        .filterNot { it.name.lowercase() in selectedNames }
        .sortedBy { it.name.lowercase() }
        .take(limit)
}

fun genreMixSelectedGenresAfterSelect(
    selectedGenres: List<Genre>,
    genre: Genre,
): List<Genre> =
    (selectedGenres + genre).distinctBy { it.name.lowercase() }

fun genreMixSelectedGenresAfterRemove(
    selectedGenres: List<Genre>,
    genre: Genre,
): List<Genre> =
    selectedGenres.filterNot { it.name.equals(genre.name, ignoreCase = true) }

const val GenreMixGenreLimit = 500
