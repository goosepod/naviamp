package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectPublicIdentity
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmNaviampConnectIdentityVerifierTest {
    @Test
    fun verifiesProofBoundToSessionAndOrderedIdentities() {
        val controllerKeys = keyPair()
        val targetKeys = keyPair()
        val controller = identity("controller", controllerKeys.public.encoded)
        val target = identity("target", targetKeys.public.encoded)
        val payload = naviampConnectIdentityProofPayload(1, "session", controller, target)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(controllerKeys.private)
            update(payload)
            sign()
        }

        assertTrue(JvmNaviampConnectIdentityVerifier.verify(controller.publicKeyBase64, payload, signature))
        assertFalse(
            JvmNaviampConnectIdentityVerifier.verify(
                controller.publicKeyBase64,
                naviampConnectIdentityProofPayload(1, "other-session", controller, target),
                signature,
            ),
        )
        assertFalse(
            JvmNaviampConnectIdentityVerifier.verify(
                controller.publicKeyBase64,
                naviampConnectIdentityProofPayload(1, "session", target, controller),
                signature,
            ),
        )
    }

    private fun keyPair() = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun identity(deviceId: String, encodedKey: ByteArray): NaviampConnectPublicIdentity =
        NaviampConnectPublicIdentity(
            deviceId = deviceId,
            identityFingerprint = MessageDigest.getInstance("SHA-256").digest(encodedKey).toHex(),
            publicKeyBase64 = Base64.getEncoder().encodeToString(encodedKey),
        )

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte) }
}
