package app.naviamp.android.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AndroidCredentialProtector {
    fun protect(value: String?): String?

    fun reveal(value: String?): String?

    fun isProtected(value: String?): Boolean
}

internal class AesGcmCredentialProtector(
    private val key: () -> SecretKey,
) : AndroidCredentialProtector {
    override fun protect(value: String?): String? {
        value ?: return null
        if (value.isEmpty() || isProtected(value)) return value
        val cipher = Cipher.getInstance(CipherTransformation).apply {
            // Android Keystore keys with randomized encryption enabled reject caller-supplied IVs.
            // Let the cipher provider generate the IV, then persist it alongside the ciphertext.
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(CredentialAad)
        }
        val iv = cipher.iv
        require(iv.size == GcmIvBytes)
        val encrypted = cipher.doFinal(value.encodeToByteArray())
        return CredentialPrefix + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    override fun reveal(value: String?): String? {
        value ?: return null
        if (!isProtected(value)) return value
        return runCatching {
            val payload = Base64.getDecoder().decode(value.removePrefix(CredentialPrefix))
            require(payload.size > GcmIvBytes)
            val iv = payload.copyOfRange(0, GcmIvBytes)
            val encrypted = payload.copyOfRange(GcmIvBytes, payload.size)
            val cipher = Cipher.getInstance(CipherTransformation).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(GcmTagBits, iv))
                updateAAD(CredentialAad)
            }
            cipher.doFinal(encrypted).decodeToString()
        }.getOrNull()
    }

    override fun isProtected(value: String?): Boolean =
        value?.startsWith(CredentialPrefix) == true
}

internal class AndroidKeystoreCredentialProtector : AndroidCredentialProtector by AesGcmCredentialProtector(
    key = ::loadOrCreateCredentialKey,
)

private fun loadOrCreateCredentialKey(): SecretKey {
    val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
    (keyStore.getKey(CredentialKeyAlias, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
        init(
            KeyGenParameterSpec.Builder(
                CredentialKeyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        generateKey()
    }
}

private const val AndroidKeyStore = "AndroidKeyStore"
private const val CredentialKeyAlias = "naviamp.credentials.v1"
private const val CredentialPrefix = "naviamp-secure-v1:"
private const val CipherTransformation = "AES/GCM/NoPadding"
private const val GcmIvBytes = 12
private const val GcmTagBits = 128
private val CredentialAad = "app.naviamp.android.credentials.v1".encodeToByteArray()
