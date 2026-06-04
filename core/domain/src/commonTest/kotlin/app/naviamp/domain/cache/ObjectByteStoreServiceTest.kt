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
}
