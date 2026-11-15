@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.storage

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.*

class AppleRecoveryFilesTest {
    @Test fun atomicBytesAndBoundedReadUseOnlyTheSelectedNativeDirectory() {
        val directory = NSTemporaryDirectory().trimEnd('/') + "/naviamp-590-certificates-" + NSUUID.UUID().UUIDString
        val port = AppleRecoveryFiles(directory)
        try {
            val bytes = ByteArray(1024) { (it % 251).toByte() }
            val path = assertNotNull(port.write("certificate.bin", bytes))
            assertContentEquals(bytes, port.read(path, 1024))
            assertEquals(33, port.read(path, 32)?.size)
            assertTrue(port.remove("certificate.bin"))
            assertNull(port.read(path, 1024))
            assertTrue(port.remove("certificate.bin"))
        } finally { NSFileManager.defaultManager.removeItemAtPath(directory, null) }
    }

    @Test fun writeAndDeletionRejectTraversalAndMissingReadsRemainFailures() {
        val port = AppleRecoveryFiles(NSTemporaryDirectory().trimEnd('/') + "/naviamp-590-unused-" + NSUUID.UUID().UUIDString)
        for (name in listOf("", ".", "..", "../outside", "nested/file", "nested\\file")) {
            assertNull(port.write(name, byteArrayOf(1)))
            assertFalse(port.remove(name))
        }
        assertNull(port.read("/nonexistent-naviamp-590-certificate", 32))
    }
}
