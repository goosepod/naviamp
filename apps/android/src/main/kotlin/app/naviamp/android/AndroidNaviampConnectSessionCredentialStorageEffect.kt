package app.naviamp.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.naviamp.app.NaviampConnectSessionCredentialStorageEffect
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android Keystore-backed encryption for Connect resumption credentials. */
class AndroidNaviampConnectSessionCredentialStorageEffect(context: Context) :
    NaviampConnectSessionCredentialStorageEffect {
    private val preferences = context.applicationContext.getSharedPreferences(
        "naviamp-connect-session-credentials",
        Context.MODE_PRIVATE,
    )

    override fun read(peerDeviceId: String): ByteArray? {
        val encoded = preferences.getString(storageKey(peerDeviceId), null) ?: return null
        val payload = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
        if (payload.size <= IvBytes) return null
        val iv = payload.copyOfRange(0, IvBytes)
        val ciphertext = payload.copyOfRange(IvBytes, payload.size)
        return try {
            Cipher.getInstance(CipherTransformation).run {
                init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(TagBits, iv))
                doFinal(ciphertext)
            }
        } catch (_: Exception) {
            null
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
            payload.fill(0)
        }
    }

    override fun write(peerDeviceId: String, value: ByteArray) {
        val cipher = Cipher.getInstance(CipherTransformation).apply {
            init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        }
        val ciphertext = cipher.doFinal(value)
        val payload = cipher.iv + ciphertext
        try {
            val encoded = Base64.getEncoder().encodeToString(payload)
            check(preferences.edit().putString(storageKey(peerDeviceId), encoded).commit()) {
                "Android could not persist the Naviamp Connect session credential."
            }
        } finally {
            ciphertext.fill(0)
            payload.fill(0)
        }
    }

    override fun remove(peerDeviceId: String) {
        preferences.edit().remove(storageKey(peerDeviceId)).commit()
    }

    override fun contains(peerDeviceId: String): Boolean = preferences.contains(storageKey(peerDeviceId))

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        (store.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore).run {
            init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKey()
        }
    }

    private fun storageKey(peerDeviceId: String) = "peer:$peerDeviceId"

    private companion object {
        const val AndroidKeyStore = "AndroidKeyStore"
        const val KeyAlias = "app.naviamp.connect.session-credentials.v1"
        const val CipherTransformation = "AES/GCM/NoPadding"
        const val IvBytes = 12
        const val TagBits = 128
    }
}
