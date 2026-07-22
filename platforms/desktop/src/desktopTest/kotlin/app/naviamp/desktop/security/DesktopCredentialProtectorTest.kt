package app.naviamp.desktop.security

import java.nio.file.Files
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopCredentialProtectorTest {
    private val protector = DesktopCredentialProtector {
        SecretKeySpec(ByteArray(32) { index -> index.toByte() }, "AES")
    }

    @Test
    fun protectsAndRevealsCredential() {
        val protected = protector.protect("secret-token")

        assertNotEquals("secret-token", protected)
        assertTrue(protector.isProtected(protected))
        assertEquals("secret-token", protector.reveal(protected))
    }

    @Test
    fun leavesEmptyAndAlreadyProtectedValuesStable() {
        val protected = protector.protect("secret-token")

        assertEquals("", protector.protect(""))
        assertEquals(protected, protector.protect(protected))
        assertFalse(protector.isProtected("plain"))
        assertEquals("plain", protector.reveal("plain"))
    }

    @Test
    fun rejectsEncryptedValuesFromAnotherKey() {
        val protected = protector.protect("secret-token")
        val other = DesktopCredentialProtector {
            SecretKeySpec(ByteArray(32) { index -> (index + 1).toByte() }, "AES")
        }

        assertEquals(null, other.reveal(protected))
    }

    @Test
    fun windowsDpapiStoreRoundTripsAKeyAtAPathContainingSpaces() {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val directory = Files.createTempDirectory("naviamp credential store test")
        val path = directory.resolve("credential key.dpapi")
        val value = Base64.getEncoder().encodeToString(ByteArray(32) { index -> index.toByte() })
        try {
            val store = WindowsDpapiValueStore(path)

            store.write(value)

            assertTrue(path.exists())
            assertEquals(value, store.read())
        } finally {
            path.deleteIfExists()
            directory.deleteIfExists()
        }
    }
}
