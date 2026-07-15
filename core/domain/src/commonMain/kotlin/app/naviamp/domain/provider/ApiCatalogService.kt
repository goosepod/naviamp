package app.naviamp.domain.provider

import app.naviamp.domain.Album
import app.naviamp.domain.Artist
import app.naviamp.domain.Track
import kotlinx.coroutines.CoroutineScope

class ApiCatalogService(
    private val provider: () -> MediaProvider?,
) {
    suspend fun artistsPage(
        query: String,
        request: MediaPageRequest,
    ): MediaPage<Artist> {
        val activeProvider = requireProvider()
        return query.trim().takeIf { it.isNotEmpty() }
            ?.let { activeProvider.searchArtistsPage(it, request) }
            ?: activeProvider.artistsPage(request)
    }

    suspend fun albumsPage(
        query: String,
        request: MediaPageRequest,
    ): MediaPage<Album> {
        val activeProvider = requireProvider()
        return query.trim().takeIf { it.isNotEmpty() }
            ?.let { activeProvider.searchAlbumsPage(it, request) }
            ?: activeProvider.albumsPage(request)
    }

    suspend fun tracksPage(
        query: String,
        request: MediaPageRequest,
    ): MediaPage<Track> {
        val activeProvider = requireProvider()
        return query.trim().takeIf { it.isNotEmpty() }
            ?.let { activeProvider.searchTracksPage(it, request) }
            ?: activeProvider.tracksPage(request)
    }

    fun artistsPager(
        scope: CoroutineScope,
        query: () -> String = { "" },
        initialRequest: MediaPageRequest = MediaPageRequest(),
    ): MediaPager<Artist> =
        MediaPager(
            scope = scope,
            itemKey = { artist -> artist.id.value },
            initialRequest = initialRequest,
            loadPage = { request -> artistsPage(query(), request) },
        )

    fun albumsPager(
        scope: CoroutineScope,
        query: () -> String = { "" },
        initialRequest: MediaPageRequest = MediaPageRequest(),
    ): MediaPager<Album> =
        MediaPager(
            scope = scope,
            itemKey = { album -> album.id.value },
            initialRequest = initialRequest,
            loadPage = { request -> albumsPage(query(), request) },
        )

    fun tracksPager(
        scope: CoroutineScope,
        query: () -> String = { "" },
        initialRequest: MediaPageRequest = MediaPageRequest(),
    ): MediaPager<Track> =
        MediaPager(
            scope = scope,
            itemKey = { track -> track.id.value },
            initialRequest = initialRequest,
            loadPage = { request -> tracksPage(query(), request) },
        )

    private fun requireProvider(): MediaProvider =
        provider() ?: throw IllegalStateException("Connect to Navidrome before loading the catalog.")
}
