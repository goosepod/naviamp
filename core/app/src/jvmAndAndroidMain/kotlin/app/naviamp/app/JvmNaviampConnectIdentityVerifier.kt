package app.naviamp.app

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object JvmNaviampConnectIdentityVerifier : NaviampConnectIdentityVerifier {
    override fun fingerprint(publicKeyBase64: String): String? = try {
        val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
        try {
            MessageDigest.getInstance("SHA-256").digest(publicKeyBytes).toHexString()
        } finally {
            publicKeyBytes.fill(0)
        }
    } catch (_: Exception) {
        null
    }

    override fun verify(
        publicKeyBase64: String,
        payload: ByteArray,
        signature: ByteArray,
    ): Boolean = try {
        val publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64)
        try {
            val publicKey = KeyFactory.getInstance(KeyAlgorithm)
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
            Signature.getInstance(SignatureAlgorithm).run {
                initVerify(publicKey)
                update(payload)
                verify(signature)
            }
        } finally {
            publicKeyBytes.fill(0)
        }
    } catch (_: Exception) {
        false
    }
}

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    this@toHexString.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HexDigits[value ushr 4])
        append(HexDigits[value and 0x0f])
    }
}

private const val KeyAlgorithm = "EC"
private const val SignatureAlgorithm = "SHA256withECDSA"
private const val HexDigits = "0123456789abcdef"
