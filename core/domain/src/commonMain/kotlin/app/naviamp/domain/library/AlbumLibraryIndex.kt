package app.naviamp.domain.library

import app.naviamp.domain.Album
import app.naviamp.domain.provider.MediaPageRequest
import app.naviamp.domain.provider.MediaProvider
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** A complete, atomically published catalog, separate from opportunistic detail-cache rows. */
data class AlbumCatalogSnapshot(val albums: List<Album>, val refreshedAtEpochMillis: Long)

data class AlbumCatalogScope(val sourceId: String, val catalogKey: String)

interface AlbumCatalogRepository {
    fun readAlbumCatalog(scope: AlbumCatalogScope): AlbumCatalogSnapshot?
    fun replaceAlbumCatalog(scope: AlbumCatalogScope, snapshot: AlbumCatalogSnapshot)
}

class AlbumLibraryIndex(
    private val repository: AlbumCatalogRepository,
    private val sourceId: () -> String?,
    private val nowEpochMillis: () -> Long,
) {
    private val refreshFavoriteChanges = mutableMapOf<AlbumCatalogScope, MutableMap<String, String?>>()

    fun scope(provider: MediaProvider): AlbumCatalogScope = AlbumCatalogScope(
        requireNotNull(sourceId()) { "No active library source" },
        // Length-prefix each component so library IDs cannot collide with separators.
        (listOf(provider.id.value, provider.cacheNamespace) + provider.selectedMusicFolderIds.sorted())
            .joinToString("") { "${it.length}:$it" },
    )

    fun snapshot(scope: AlbumCatalogScope): AlbumCatalogSnapshot? = repository.readAlbumCatalog(scope)

    fun isFresh(snapshot: AlbumCatalogSnapshot): Boolean =
        (nowEpochMillis() - snapshot.refreshedAtEpochMillis) in 0 until 15 * 60 * 1000L

    suspend fun refresh(
        scope: AlbumCatalogScope,
        provider: MediaProvider,
        isCurrent: () -> Boolean = { true },
        onProgress: (List<Album>) -> Unit = {},
    ): AlbumCatalogSnapshot? {
        val favoriteChanges = mutableMapOf<String, String?>()
        refreshFavoriteChanges[scope] = favoriteChanges
        try {
            val albums = linkedMapOf<String, Album>()
            var request: MediaPageRequest? = MediaPageRequest(limit = app.naviamp.domain.provider.MaximumMediaPageSize)
            val visited = mutableSetOf<MediaPageRequest>()
            while (request != null) {
                coroutineContext.ensureActive()
                if (!isCurrent()) return null
                val pageRequest = request
                check(visited.add(pageRequest)) { "Album paging did not advance" }
                val page = provider.albumsPage(pageRequest)
                coroutineContext.ensureActive()
                if (!isCurrent()) return null
                val before = albums.size
                page.items.forEach { albums[it.id.value] = it }
                request = page.nextRequest
                check(request == null || albums.size > before) { "Album paging returned no new albums" }
                onProgress(orderAlbumCatalog(albums.values.map { album ->
                    if (favoriteChanges.containsKey(album.id.value)) album.copy(favoritedAtIso8601 = favoriteChanges[album.id.value]) else album
                }))
            }
            val merged = albums.values.map { album ->
                if (favoriteChanges.containsKey(album.id.value)) album.copy(favoritedAtIso8601 = favoriteChanges[album.id.value]) else album
            }
            val snapshot = AlbumCatalogSnapshot(orderAlbumCatalog(merged), nowEpochMillis())
            coroutineContext.ensureActive()
            if (!isCurrent()) return null
            repository.replaceAlbumCatalog(scope, snapshot)
            return snapshot
        } finally {
            if (refreshFavoriteChanges[scope] === favoriteChanges) refreshFavoriteChanges.remove(scope)
        }
    }

    fun updateAlbum(provider: MediaProvider, album: Album) {
        val scope = scope(provider)
        refreshFavoriteChanges[scope]?.set(album.id.value, album.favoritedAtIso8601)
        val snapshot = snapshot(scope) ?: return
        if (snapshot.albums.none { it.id == album.id }) return
        repository.replaceAlbumCatalog(scope, snapshot.copy(
            albums = orderAlbumCatalog(snapshot.albums.map { if (it.id == album.id) album else it }),
        ))
    }
}

/** Display-title ordering: symbols/numbers first, then A–Z. Articles stay in the title. */
fun orderAlbumCatalog(albums: List<Album>): List<Album> = albums.sortedWith(
    compareBy<Album>({ libraryTitleLetter(it.title) }, { it.title.trimStart().lowercase() },
        { it.artistName.lowercase() }, { it.id.value }),
)
