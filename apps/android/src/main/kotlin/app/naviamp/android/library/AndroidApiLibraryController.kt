package app.naviamp.android

import app.naviamp.domain.cache.LocalLibraryIndexRepository
import app.naviamp.domain.library.ArtistLibraryIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AndroidApiLibraryController(
    private val scope: CoroutineScope,
    private val state: AndroidAppState,
    libraryIndexRepository: LocalLibraryIndexRepository,
) {
    private val artistIndex = ArtistLibraryIndex(libraryIndexRepository)

    fun refresh() {
        if (state.isLibrarySyncing) return
        val provider = state.provider
        val sourceId = state.activeSourceId
        if (provider == null || sourceId == null) {
            state.libraryArtists = emptyList()
            state.isLibrarySyncing = false
            state.libraryStatus = "Connect to Navidrome before loading the library."
            return
        }
        state.isLibrarySyncing = true
        state.libraryStatus = "Refreshing artists…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { artistIndex.refresh(sourceId, provider) }
            }.onSuccess {
                loadIndexedArtists()
                state.libraryStatus = "${state.libraryArtists.size} artists indexed."
            }.onFailure { error ->
                loadIndexedArtists()
                state.libraryStatus = "Could not refresh artists: ${error.message ?: error::class.simpleName}"
            }
            state.isLibrarySyncing = false
        }
    }

    fun updateQuery(query: String) {
        state.libraryQuery = query
        loadIndexedArtists()
    }

    fun loadNext() = Unit

    private fun loadIndexedArtists() {
        val sourceId = state.activeSourceId
        state.libraryArtists = if (sourceId == null) {
            emptyList()
        } else {
            artistIndex.snapshot(sourceId, state.libraryQuery).artists
        }
    }
}
