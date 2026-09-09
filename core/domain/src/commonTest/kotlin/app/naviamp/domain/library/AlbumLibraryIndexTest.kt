package app.naviamp.domain.library

import app.naviamp.domain.*
import app.naviamp.domain.provider.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AlbumLibraryIndexTest {
    @Test
    fun completeCatalogHasStableLocalOrderingAndWarmSnapshotsNeedNoNetwork() = runTest {
        val repository = MemoryAlbumCatalog()
        val provider = AlbumIndexProvider(listOf("The Aquabats!", "G I R L", "25", "Les Années 80", "Another", "G I R L"))
        val index = AlbumLibraryIndex(repository, { "source" }, { 1000L })
        val scope = index.scope(provider)
        val result = index.refresh(scope, provider)!!
        assertEquals(listOf("25", "Another", "G I R L", "G I R L", "Les Années 80", "The Aquabats!"), result.albums.map { it.title })
        assertEquals(2, libraryLetterJumpIndex(result.albums.map { it.title }, 'G'))
        val calls = provider.calls
        val restarted = AlbumLibraryIndex(repository, { "source" }, { 2000L })
        assertEquals(result, restarted.snapshot(scope))
        assertTrue(restarted.isFresh(result))
        assertEquals(calls, provider.calls)
    }

    @Test
    fun failedCancelledAndStaleRefreshesKeepThePreviousCompleteSnapshot() = runTest {
        val repository = MemoryAlbumCatalog()
        val provider = AlbumIndexProvider(listOf("Old"))
        val index = AlbumLibraryIndex(repository, { "source" }, { 1000L })
        val scope = index.scope(provider)
        val old = index.refresh(scope, provider)
        provider.titles = listOf("New", "Newer", "Newest")
        provider.failureAt = 2
        assertFailsWith<IllegalStateException> { index.refresh(scope, provider) }
        assertEquals(old, repository.values[scope])
        provider.cancel = true
        assertFailsWith<CancellationException> { index.refresh(scope, provider) }
        assertEquals(old, repository.values[scope])
        provider.failureAt = null
        provider.cancel = false
        var current = true
        assertNull(index.refresh(scope, provider, { current }) { current = false })
        assertEquals(old, repository.values[scope])
    }

    @Test
    fun replacementHandlesRenamesRemovalsEmptyLibrariesAndScopeChanges() = runTest {
        val repository = MemoryAlbumCatalog()
        val provider = AlbumIndexProvider(listOf("Old", "Deleted"))
        var source = "source"
        val index = AlbumLibraryIndex(repository, { source }, { 1000L })
        val scope = index.scope(provider)
        index.refresh(scope, provider)
        provider.titles = listOf("Renamed")
        assertEquals(listOf("Renamed"), index.refresh(scope, provider)!!.albums.map { it.title })
        provider.folders = listOf("other")
        assertNull(index.snapshot(index.scope(provider)))
        source = "another source"
        assertNull(index.snapshot(index.scope(provider)))
        provider.titles = emptyList()
        assertEquals(emptyList(), index.refresh(scope, provider)!!.albums)
        assertNotNull(index.snapshot(scope))
    }

    @Test
    fun favoritesChangedDuringIndexingSurviveTheFinalSnapshot() = runTest {
        val repository = MemoryAlbumCatalog()
        val provider = AlbumIndexProvider(listOf("A", "B", "C"))
        val index = AlbumLibraryIndex(repository, { "source" }, { 1000L })
        val scope = index.scope(provider)
        val snapshot = index.refresh(scope, provider) { partial ->
            index.updateAlbum(provider, partial.first().copy(favoritedAtIso8601 = "2026-09-05"))
        }!!
        assertEquals("2026-09-05", snapshot.albums.first().favoritedAtIso8601)
        assertEquals(snapshot, index.snapshot(scope))
    }

    @Test
    fun repeatedPagesCannotPublishAnIncompleteReplacement() = runTest {
        val repository = MemoryAlbumCatalog()
        val provider = AlbumIndexProvider(listOf("A", "B", "C"))
        val index = AlbumLibraryIndex(repository, { "source" }, { 1000L })
        val scope = index.scope(provider)
        val old = index.refresh(scope, provider)
        provider.repeat = true
        assertFailsWith<IllegalStateException> { index.refresh(scope, provider) }
        assertEquals(old, index.snapshot(scope))
    }
}

private class MemoryAlbumCatalog : AlbumCatalogRepository {
    val values = mutableMapOf<AlbumCatalogScope, AlbumCatalogSnapshot>()
    override fun readAlbumCatalog(scope: AlbumCatalogScope) = values[scope]
    override fun replaceAlbumCatalog(scope: AlbumCatalogScope, snapshot: AlbumCatalogSnapshot) { values[scope] = snapshot }
}

private class AlbumIndexProvider(var titles: List<String>) : MediaProvider {
    var calls = 0
    var failureAt: Int? = null
    var cancel = false
    var repeat = false
    var folders = emptyList<String>()
    override val selectedMusicFolderIds get() = folders
    override val id = ProviderId("test")
    override val displayName = "Test"
    override val capabilities = ProviderCapabilities(false, false, false, false, false)
    override suspend fun albumsPage(request: MediaPageRequest): MediaPage<Album> {
        calls++
        if (request.offset == failureAt) {
            if (cancel) throw CancellationException() else error("Offline")
        }
        val offset = if (repeat) 0 else request.offset
        val rows = titles.mapIndexed { i, t -> Album(AlbumId("$i"), t, "Artist", null, null) }.drop(offset).take(2)
        return MediaPage(rows, request.offset, 2, repeat || offset + rows.size < titles.size)
    }
    override suspend fun validateConnection() = ConnectionValidation(null, null)
    override suspend fun recentlyAddedAlbums(limit: Int) = emptyList<Album>()
    override suspend fun album(albumId: AlbumId): AlbumDetails = error("Unused")
    override suspend fun artist(artistId: ArtistId): ArtistDetails = error("Unused")
    override suspend fun artists(limit: Int) = emptyList<Artist>()
    override suspend fun tracks(limit: Int) = emptyList<Track>()
    override suspend fun search(query: String, limit: Int) = MediaSearchResults()
    override suspend fun streamUrl(request: StreamRequest) = ""
    override fun coverArtUrl(coverArtId: String) = ""
}
