package app.naviamp.domain.library

import app.naviamp.domain.cache.LibrarySnapshot
import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.provider.MediaProvider

class ArtistLibraryIndex(
    private val repository: LocalLibraryIndexRepository,
) {
    fun snapshot(sourceId: String, query: String = ""): LibrarySnapshot {
        val indexed = if (query.isBlank()) {
            repository.librarySnapshot(sourceId, limit = MaximumIndexedArtists)
        } else {
            repository.searchLibrary(sourceId, query, limit = MaximumIndexedArtists)
        }
        return LibrarySnapshot(artists = indexed.artists)
    }

    suspend fun refresh(sourceId: String, provider: MediaProvider): LibrarySnapshot {
        val artists = provider.artists(limit = MaximumIndexedArtists.toInt())
            .distinctBy { artist -> artist.id }
            .sortedBy { artist -> artist.name.lowercase() }
        repository.replaceLibraryArtists(sourceId, artists)
        return snapshot(sourceId)
    }
}

private const val MaximumIndexedArtists = 100_000L
