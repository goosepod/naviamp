package app.naviamp.android.security

import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidCredentialProtectorTest {
    private val protector = AesGcmCredentialProtector(
        key = { SecretKeySpec(ByteArray(32) { index -> index.toByte() }, "AES") },
    )

    @Test
    fun protectsAndRevealsCredentialWithoutPlaintext() {
        val protected = protector.protect("secret-password")

        assertTrue(protector.isProtected(protected))
        assertFalse(protected.orEmpty().contains("secret-password"))
        assertEquals("secret-password", protector.reveal(protected))
    }

    @Test
    fun protectsSameCredentialWithDifferentCiphertext() {
        assertFalse(protector.protect("secret") == protector.protect("secret"))
    }

    @Test
    fun readsLegacyPlaintextForMigrationAndRejectsDamagedCiphertext() {
        assertEquals("legacy-password", protector.reveal("legacy-password"))
        assertNull(protector.reveal("naviamp-secure-v1:not-valid-base64"))
    }
}
