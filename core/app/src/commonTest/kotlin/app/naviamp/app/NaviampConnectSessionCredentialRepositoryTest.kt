package app.naviamp.app

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaviampConnectSessionCredentialRepositoryTest {
    @Test
    fun storesDefensiveCopiesAndRemovesCredentialsByPeerIdentity() {
        val storage = MemoryCredentialStorage()
        val repository = NaviampConnectSessionCredentialRepository(storage)
        val original = byteArrayOf(1, 2, 3, 4)

        repository.write("tv", original)
        original.fill(0)

        assertTrue(repository.contains("tv"))
        val first = repository.read("tv")!!
        assertContentEquals(byteArrayOf(1, 2, 3, 4), first)
        first.fill(9)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), repository.read("tv"))

        repository.remove("tv")
        assertFalse(repository.contains("tv"))
        assertNull(repository.read("tv"))
    }

    private class MemoryCredentialStorage : NaviampConnectSessionCredentialStorageEffect {
        private val values = mutableMapOf<String, ByteArray>()
        override fun read(peerDeviceId: String) = values[peerDeviceId]?.copyOf()
        override fun write(peerDeviceId: String, value: ByteArray) { values[peerDeviceId] = value.copyOf() }
        override fun remove(peerDeviceId: String) { values.remove(peerDeviceId)?.fill(0) }
        override fun contains(peerDeviceId: String) = peerDeviceId in values
    }
}
