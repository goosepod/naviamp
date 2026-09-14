package app.naviamp.app

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Shared Android/Desktop AES-256-GCM effect using the platform JCA provider. */
object JvmNaviampConnectAuthenticatedCipherFactory : NaviampConnectAuthenticatedCipherFactory {
    override fun create(
        sessionSecret: NaviampConnectSessionSecret,
        role: NaviampConnectPakeRole,
        protocolVersion: Int,
        sessionId: String,
    ): NaviampConnectAuthenticatedCipher {
        require(protocolVersion > 0)
        require(sessionId.isNotBlank())
        val root = sessionSecret.copyBytes()
        return try {
            val contextText = "Naviamp Connect AEAD v1|$protocolVersion|${sessionId.length}:$sessionId"
            val context = contextText.toByteArray(StandardCharsets.UTF_8)
            val salt = MessageDigest.getInstance("SHA-256").digest(context)
            val controllerToTarget = derive(root, salt, "$contextText|controller-to-target")
            val targetToController = derive(root, salt, "$contextText|target-to-controller")
            try {
                val outbound = if (role == NaviampConnectPakeRole.Controller) {
                    controllerToTarget
                } else {
                    targetToController
                }
                val inbound = if (role == NaviampConnectPakeRole.Controller) {
                    targetToController
                } else {
                    controllerToTarget
                }
                JvmNaviampConnectAuthenticatedCipher(
                    outboundKey = outbound.copyOfRange(0, KEY_BYTES),
                    outboundNoncePrefix = outbound.copyOfRange(KEY_BYTES, DERIVED_BYTES),
                    inboundKey = inbound.copyOfRange(0, KEY_BYTES),
                    inboundNoncePrefix = inbound.copyOfRange(KEY_BYTES, DERIVED_BYTES),
                )
            } finally {
                controllerToTarget.fill(0)
                targetToController.fill(0)
                context.fill(0)
                salt.fill(0)
            }
        } finally {
            root.fill(0)
            sessionSecret.destroy()
        }
    }

    private fun derive(root: ByteArray, salt: ByteArray, info: String): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        val infoBytes = info.toByteArray(StandardCharsets.UTF_8)
        return try {
            generator.init(HKDFParameters(root, salt, infoBytes))
            ByteArray(DERIVED_BYTES).also { generator.generateBytes(it, 0, it.size) }
        } finally {
            infoBytes.fill(0)
        }
    }

    private const val KEY_BYTES = 32
    private const val NONCE_PREFIX_BYTES = 4
    private const val DERIVED_BYTES = KEY_BYTES + NONCE_PREFIX_BYTES
}

private class JvmNaviampConnectAuthenticatedCipher(
    private val outboundKey: ByteArray,
    private val outboundNoncePrefix: ByteArray,
    private val inboundKey: ByteArray,
    private val inboundNoncePrefix: ByteArray,
) : NaviampConnectAuthenticatedCipher {
    private var destroyed = false

    @Synchronized
    override fun seal(sequence: Long, plaintext: ByteArray, authenticatedData: ByteArray): ByteArray =
        crypt(Cipher.ENCRYPT_MODE, sequence, plaintext, authenticatedData, outboundKey, outboundNoncePrefix)

    @Synchronized
    override fun open(sequence: Long, ciphertext: ByteArray, authenticatedData: ByteArray): ByteArray =
        crypt(Cipher.DECRYPT_MODE, sequence, ciphertext, authenticatedData, inboundKey, inboundNoncePrefix)

    @Synchronized
    override fun destroy() {
        if (destroyed) return
        destroyed = true
        outboundKey.fill(0)
        outboundNoncePrefix.fill(0)
        inboundKey.fill(0)
        inboundNoncePrefix.fill(0)
    }

    private fun crypt(
        mode: Int,
        sequence: Long,
        input: ByteArray,
        authenticatedData: ByteArray,
        key: ByteArray,
        noncePrefix: ByteArray,
    ): ByteArray {
        if (destroyed) throw NaviampConnectCipherException(NaviampConnectCipherFailure.InvalidState)
        require(sequence >= 0)
        val nonce = ByteBuffer.allocate(NONCE_BYTES).put(noncePrefix).putLong(sequence).array()
        return try {
            Cipher.getInstance(CIPHER_TRANSFORMATION).run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                updateAAD(authenticatedData)
                doFinal(input)
            }
        } catch (cause: AEADBadTagException) {
            throw NaviampConnectCipherException(NaviampConnectCipherFailure.AuthenticationFailed, cause)
        } catch (cause: java.security.GeneralSecurityException) {
            throw NaviampConnectCipherException(NaviampConnectCipherFailure.InvalidKey, cause)
        } finally {
            nonce.fill(0)
        }
    }

    private companion object {
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val NONCE_BYTES = 12
    }
}
