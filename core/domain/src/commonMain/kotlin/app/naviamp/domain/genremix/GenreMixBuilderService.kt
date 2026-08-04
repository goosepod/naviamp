package app.naviamp.domain.genremix

import app.naviamp.domain.Genre
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.provider.MediaProvider

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

fun genreMixBuilderService(
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
): GenreMixBuilderService =
    GenreMixBuilderService(
        genres = { limit ->
            val home = homeContent()
            val recentGenreOrder = home.recentlyPlayedTracks
                .flatMap { track -> track.genres }
                .distinctBy(String::lowercase)
                .mapIndexed { index, name -> name.lowercase() to index }
                .toMap()
            provider()?.genres(limit.toInt()).orEmpty()
                .ifEmpty { home.genres }
                .sortedWith(
                    compareBy<Genre> { genre -> recentGenreOrder[genre.name.lowercase()] ?: Int.MAX_VALUE }
                        .thenBy { genre -> genre.name.lowercase() },
                )
        },
    )

fun List<Genre>.genreMixSuggestions(
    selectedGenres: List<Genre>,
    limit: Int = GenreMixGenreLimit,
): List<Genre> {
    val selectedNames = selectedGenres.map { it.name.lowercase() }.toSet()
    return distinctBy { it.name.lowercase() }
        .filterNot { it.name.lowercase() in selectedNames }
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
