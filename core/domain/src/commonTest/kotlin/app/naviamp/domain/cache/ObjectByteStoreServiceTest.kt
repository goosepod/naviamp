package app.naviamp.domain.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ObjectByteStoreServiceTest {
    @Test
    fun returnsCachedBytesWithoutFetchingAgain() = runTest {
        val store = InMemoryObjectByteStore()
        val service = ObjectByteStoreService(store)
        var fetchCount = 0

        val first = service.bytes("cover-1") {
            fetchCount++
            byteArrayOf(1, 2, 3)
        }
        val second = service.bytes("cover-1") {
            fetchCount++
            byteArrayOf(4, 5, 6)
        }

        assertContentEquals(byteArrayOf(1, 2, 3), first)
        assertContentEquals(byteArrayOf(1, 2, 3), second)
        assertEquals(1, fetchCount)
    }

    @Test
    fun deleteRemovesCachedBytes() = runTest {
        val store = InMemoryObjectByteStore()
        val service = ObjectByteStoreService(store)
        service.bytes("cover-1") { byteArrayOf(1) }

        store.deleteObjectBytes("cover-1")
        val bytes = service.bytes("cover-1") { byteArrayOf(2) }

        assertContentEquals(byteArrayOf(2), bytes)
    }

    @Test
    fun cachedBytesReturnsOnlyStoredBytes() = runTest {
        val store = InMemoryObjectByteStore()
        val service = ObjectByteStoreService(store)

        assertEquals(null, service.cachedBytes("cover-1"))

        service.bytes("cover-1") { byteArrayOf(7, 8, 9) }

        assertContentEquals(byteArrayOf(7, 8, 9), service.cachedBytes("cover-1"))
    }

    @Test
    fun promotesEquivalentLegacyKeyWithoutFetching() = runTest {
        val store = InMemoryObjectByteStore()
        val service = ObjectByteStoreService(store)
        store.writeObjectBytes("cover-1?token=old", byteArrayOf(4, 5, 6))
        var fetched = false

        val bytes = service.bytes(
            key = "cover-1",
            equivalentKey = { candidate -> candidate.substringBefore('?') == "cover-1" },
            fetch = {
                fetched = true
                byteArrayOf(9)
            },
        )

        assertContentEquals(byteArrayOf(4, 5, 6), bytes)
        assertEquals(false, fetched)
        assertContentEquals(byteArrayOf(4, 5, 6), store.objectBytes("cover-1"))
    }
}
