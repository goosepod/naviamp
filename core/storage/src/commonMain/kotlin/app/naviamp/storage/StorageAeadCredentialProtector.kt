package app.naviamp.storage

import app.naviamp.host.StorageSecretBox
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64

/** Portable framing, bounds and fail-closed behavior around the native authenticated cipher. */
class StorageAeadCredentialProtector(private val box: StorageSecretBox) : StorageCredentialProtector {
    private val json = Json

    override fun isProtected(value: String?): Boolean = value?.startsWith(Prefix) == true

    override fun protect(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        // A newly entered literal may start with our framing prefix. Only authenticated existing
        // ciphertext is idempotent; never accept a merely well-shaped forged frame as a credential.
        if (isProtected(value) && frame(value) != null && reveal(value) != null) return value
        if (value.length > MaximumPlaintextBytes || value.encodeToByteArray().size > MaximumPlaintextBytes) throw StorageRecoveryException(StorageRecoveryFailure.Capacity)
        val sealed = box.seal(value.encodeToByteArray()) ?: return null
        if (sealed.keyReference.isBlank() || sealed.keyReference.length > 256 || sealed.ciphertext.size > MaximumCiphertextBytes) return null
        return Prefix + json.encodeToString(SealedFrame(sealed.keyReference, Base64.encode(sealed.ciphertext)))
    }

    override fun reveal(value: String?): String? {
        if (value.isNullOrEmpty()) return value
        val frame = frame(value) ?: return null
        val ciphertext = runCatching { Base64.decode(frame.ciphertext) }.getOrNull() ?: return null
        if (ciphertext.size > MaximumCiphertextBytes) return null
        val clear = box.open(frame.keyReference, ciphertext) ?: return null
        if (clear.size > MaximumPlaintextBytes) return null
        return runCatching { clear.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
    }

    private fun frame(value: String): SealedFrame? {
        if (!value.startsWith(Prefix) || value.length > MaximumEncodedBytes) return null
        return runCatching { json.decodeFromString<SealedFrame>(value.removePrefix(Prefix)) }.getOrNull()
            ?.takeIf { it.keyReference.isNotBlank() && it.keyReference.length <= 256 }
    }

    @Serializable private data class SealedFrame(val keyReference: String, val ciphertext: String)

    companion object {
        private const val Prefix = "naviamp-keychain-aead-v1:"
        const val MaximumPlaintextBytes = 128 * 1024
        private const val MaximumCiphertextBytes = MaximumPlaintextBytes + 1024
        private const val MaximumEncodedBytes = 192 * 1024
    }
}
