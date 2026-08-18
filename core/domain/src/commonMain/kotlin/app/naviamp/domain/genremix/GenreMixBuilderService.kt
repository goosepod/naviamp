package app.naviamp.domain.genremix

import app.naviamp.domain.Genre
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.home.HomeContent
import app.naviamp.domain.library.LibraryGenreInventoryLimit
import app.naviamp.domain.library.LibraryGenreOntologyProjection
import app.naviamp.domain.provider.MediaProvider

class GenreMixBuilderService(
    private val ontologyProjection: suspend () -> LibraryGenreOntologyProjection = {
        LibraryGenreOntologyProjection()
    },
    private val genres: suspend (Long) -> List<Genre>,
) {
    suspend fun allGenres(limit: Int = GenreMixGenreLimit): List<Genre> =
        genres(limit.toLong()).genreMixSuggestions(emptyList(), limit)

    suspend fun searchSuggestions(
        query: String,
        selectedGenres: List<Genre>,
        limit: Int = GenreMixGenreLimit,
    ): List<Genre> {
        val allGenres = genres(LibraryGenreInventoryLimit.toLong())
        val filtered = query.trim().takeIf { it.isNotBlank() }?.let { trimmed ->
            allGenres.filter { genre -> genre.name.contains(trimmed, ignoreCase = true) }
        } ?: allGenres
        return filtered.genreMixSuggestions(selectedGenres, limit)
    }

    suspend fun browseProjection(): LibraryGenreOntologyProjection =
        ontologyProjection()
}

fun genreMixBuilderService(
    provider: () -> MediaProvider?,
    homeContent: () -> HomeContent,
    sourceId: () -> String? = { provider()?.cacheNamespace },
    libraryIndex: LocalLibraryIndexRepository? = null,
): GenreMixBuilderService {
    suspend fun loadStoredProjection(): LibraryGenreOntologyProjection {
        val activeSourceId = sourceId() ?: return LibraryGenreOntologyProjection()
        val repository = libraryIndex ?: return LibraryGenreOntologyProjection()
        if (repository.libraryGenreInventory(activeSourceId).isEmpty()) {
            val loaded = provider()?.genres(LibraryGenreInventoryLimit).orEmpty()
            repository.replaceLibraryGenreInventory(activeSourceId, loaded)
        }
        return repository.libraryGenreOntologyProjection(activeSourceId)
    }

    return GenreMixBuilderService(
        genres = { limit ->
            val home = homeContent()
            val recentGenreOrder = home.recentlyPlayedTracks
                .flatMap { track -> track.genres }
                .distinctBy(String::lowercase)
                .mapIndexed { index, name -> name.lowercase() to index }
                .toMap()
            val activeSourceId = sourceId()
            val storedGenres = loadStoredProjection().selectableGenres
            val genres = storedGenres.ifEmpty {
                provider()?.genres(maxOf(limit.toInt(), LibraryGenreInventoryLimit)).orEmpty().also { loaded ->
                    if (activeSourceId != null && libraryIndex != null) {
                        libraryIndex.replaceLibraryGenreInventory(activeSourceId, loaded)
                    }
                }.let { loaded ->
                    activeSourceId
                        ?.let { libraryIndex?.libraryGenreOntologyProjection(it)?.selectableGenres }
                        .orEmpty()
                        .ifEmpty { loaded }
                }
            }
            genres.ifEmpty { home.genres }
                .sortedWith(
                    compareBy<Genre> { genre -> recentGenreOrder[genre.name.lowercase()] ?: Int.MAX_VALUE }
                        .thenBy { genre -> genre.name.lowercase() },
                )
        },
        ontologyProjection = ::loadStoredProjection,
    )
}

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
