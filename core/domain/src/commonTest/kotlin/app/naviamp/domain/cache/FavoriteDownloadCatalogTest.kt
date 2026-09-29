package app.naviamp.domain.cache

import app.naviamp.domain.provider.MediaPage
import app.naviamp.domain.provider.MediaPageRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FavoriteDownloadCatalogTest {
    @Test
    fun collectsAllPagesBeforeAcceptingFavoriteCollection() = runTest {
        val offsets = mutableListOf<Int>()
        val favorites = (1..203).map(Int::toString)
        val result = enumerateFavoriteDownloadCatalog(maximumItems = 250) { request ->
            offsets += request.offset
            MediaPage(
                items = favorites.drop(request.offset).take(request.limit),
                offset = request.offset,
                limit = request.limit,
                hasMore = request.offset + request.limit < favorites.size,
            )
        }

        assertEquals(favorites, assertIs<FavoriteDownloadCatalog.Complete<String>>(result).items)
        assertEquals(listOf(0, 200), offsets)
    }

    @Test
    fun refusesCollectionWhenItsSizeExceedsTheExplicitBound() = runTest {
        val result = enumerateFavoriteDownloadCatalog(maximumItems = 2) { request ->
            page(request, listOf("one", "two", "three").drop(request.offset).take(request.limit))
        }

        assertEquals(FavoriteDownloadCatalog.TooLarge(2), result)
    }

    @Test
    fun acceptsCollectionExactlyAtTheBoundAfterAnEmptyProbe() = runTest {
        val result = enumerateFavoriteDownloadCatalog(maximumItems = 2) { request ->
            request.toPage(listOf("one", "two").drop(request.offset).take(request.limit))
        }

        assertEquals(listOf("one", "two"), assertIs<FavoriteDownloadCatalog.Complete<String>>(result).items)
    }

    @Test
    fun refusesProviderWithoutCompleteFavoriteEnumeration() = runTest {
        val result = enumerateFavoriteDownloadCatalog<String>(maximumItems = 2) { null }
        assertEquals(FavoriteDownloadCatalog.Unsupported, result)
    }

    private fun page(request: MediaPageRequest, items: List<String>) = MediaPage(
        items = items,
        offset = request.offset,
        limit = request.limit,
        hasMore = request.offset + items.size < 3,
    )

    private fun MediaPageRequest.toPage(items: List<String>) = MediaPage(
        items = items,
        offset = offset,
        limit = limit,
        hasMore = items.size == limit,
    )
}
