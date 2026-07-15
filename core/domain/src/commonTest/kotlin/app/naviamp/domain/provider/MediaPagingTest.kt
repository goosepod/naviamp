package app.naviamp.domain.provider

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaPagingTest {
    @Test
    fun fullPageExposesTheNextBoundedRequest() {
        val page = MediaPageRequest(offset = 50, limit = 25)
            .toMediaPage((1..25).toList())

        assertTrue(page.hasMore)
        assertEquals(MediaPageRequest(offset = 75, limit = 25), page.nextRequest)
    }

    @Test
    fun partialPageMarksTheEndOfTheResultSet() {
        val page = MediaPageRequest(offset = 50, limit = 25)
            .toMediaPage((1..10).toList())

        assertFalse(page.hasMore)
        assertNull(page.nextRequest)
    }

    @Test
    fun pageSizesAreCentrallyBounded() {
        assertFailsWith<IllegalArgumentException> { MediaPageRequest(limit = 0) }
        assertFailsWith<IllegalArgumentException> { MediaPageRequest(limit = MaximumMediaPageSize + 1) }
        assertFailsWith<IllegalArgumentException> { MediaPageRequest(offset = -1) }
    }

    @Test
    fun pageRejectsProvidersReturningMoreThanRequested() {
        assertFailsWith<IllegalArgumentException> {
            MediaPageRequest(limit = 2).toMediaPage(listOf(1, 2, 3))
        }
    }

    @Test
    fun pagerLoadsIncrementallyAndDeduplicatesPageBoundaries() = runTest {
        val requests = mutableListOf<MediaPageRequest>()
        val pager = MediaPager(
            scope = this,
            itemKey = { value: String -> value },
            initialRequest = MediaPageRequest(limit = 2),
            loadPage = { request ->
                requests += request
                when (request.offset) {
                    0 -> request.toMediaPage(listOf("one", "two"))
                    else -> request.toMediaPage(listOf("two"))
                }
            },
        )

        pager.loadNext()
        advanceUntilIdle()
        pager.loadNext()
        advanceUntilIdle()

        assertEquals(listOf(MediaPageRequest(0, 2), MediaPageRequest(2, 2)), requests)
        assertEquals(listOf("one", "two"), pager.state.value.items)
        assertNull(pager.state.value.nextRequest)
    }

    @Test
    fun pagerIgnoresDuplicateLoadRequestsWhileAPageIsRunning() = runTest {
        val releaseLoad = CompletableDeferred<Unit>()
        var loadCount = 0
        val pager = MediaPager(
            scope = this,
            itemKey = { value: String -> value },
            loadPage = { request ->
                loadCount += 1
                releaseLoad.await()
                request.toMediaPage(emptyList())
            },
        )

        pager.loadNext()
        pager.loadNext()
        advanceUntilIdle()
        assertEquals(1, loadCount)

        releaseLoad.complete(Unit)
        advanceUntilIdle()
        assertFalse(pager.state.value.isLoading)
    }

    @Test
    fun refreshCancelsAndReplacesAnObsoleteLoad() = runTest {
        val firstLoad = CompletableDeferred<Unit>()
        var loadCount = 0
        val pager = MediaPager(
            scope = this,
            itemKey = { value: String -> value },
            loadPage = { request ->
                loadCount += 1
                if (loadCount == 1) firstLoad.await()
                request.toMediaPage(listOf("result"))
            },
        )

        pager.loadNext()
        advanceUntilIdle()
        pager.refresh()
        advanceUntilIdle()

        assertEquals(2, loadCount)
        assertEquals(listOf("result"), pager.state.value.items)
        assertFalse(pager.state.value.isLoading)
    }
}
