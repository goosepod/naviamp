package app.naviamp.domain.provider

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaPageCollectionTest {
    @Test
    fun collectsEveryPageAndShrinksTheFinalRequestToTheSafetyBound() = runTest {
        val requests = mutableListOf<MediaPageRequest>()
        val items = collectBoundedMediaPages(maximumItems = 5, pageSize = 2) { request ->
            requests += request
            MediaPage(
                items = List(request.limit) { request.offset + it },
                offset = request.offset,
                limit = request.limit,
                hasMore = true,
            )
        }

        assertEquals(listOf(0, 1, 2, 3, 4), items)
        assertEquals(listOf(2, 2, 1), requests.map { it.limit })
    }

    @Test
    fun stopsWhenAProviderReturnsAnEmptyPageMarkedAsHavingMore() = runTest {
        var calls = 0
        val items = collectBoundedMediaPages<Int> { request ->
            calls += 1
            MediaPage(emptyList(), request.offset, request.limit, hasMore = true)
        }

        assertEquals(emptyList(), items)
        assertEquals(1, calls)
    }

    @Test
    fun rejectsIncorrectProviderPageMetadata() = runTest {
        assertFailsWith<IllegalArgumentException> {
            collectBoundedMediaPages<Int> { request ->
                MediaPage(emptyList(), request.offset + 1, request.limit, hasMore = false)
            }
        }
    }
}
