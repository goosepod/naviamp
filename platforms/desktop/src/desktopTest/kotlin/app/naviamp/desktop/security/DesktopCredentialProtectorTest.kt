package app.naviamp.desktop.security

import javax.crypto.spec.SecretKeySpec
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
}
