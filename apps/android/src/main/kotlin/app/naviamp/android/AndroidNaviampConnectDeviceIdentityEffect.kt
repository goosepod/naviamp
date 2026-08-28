package app.naviamp.android

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.naviamp.app.NaviampConnectDeviceIdentity
import app.naviamp.app.NaviampConnectDeviceIdentityEffect
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Android Keystore identity adapter for Connect target authentication and fingerprinting. */
class AndroidNaviampConnectDeviceIdentityEffect : NaviampConnectDeviceIdentityEffect {
    override fun loadOrCreate(): NaviampConnectDeviceIdentity {
        val publicKey = keyStore().let { store ->
            if (!store.containsAlias(KeyAlias)) createKeyPair()
            requireNotNull(store.getCertificate(KeyAlias)?.publicKey) {
                "Android Keystore did not return the Naviamp Connect identity public key."
            }
        }
        val encoded = publicKey.encoded
        val fingerprint = sha256(encoded).toHexString()
        return NaviampConnectDeviceIdentity(
            deviceId = fingerprint.take(32),
            identityFingerprint = fingerprint,
            publicKeyBase64 = Base64.getEncoder().encodeToString(encoded),
        )
    }

    override fun sign(payload: ByteArray): ByteArray {
        val store = keyStore()
        if (!store.containsAlias(KeyAlias)) loadOrCreate()
        val privateKey = requireNotNull(store.getKey(KeyAlias, null)) {
            "Android Keystore did not return the Naviamp Connect identity private key."
        }
        return Signature.getInstance(SignatureAlgorithm).run {
            initSign(privateKey as java.security.PrivateKey)
            update(payload)
            sign()
        }
    }

    private fun createKeyPair() {
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, AndroidKeyStore).run {
            initialize(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generateKeyPair()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
}

private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    this@toHexString.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HexDigits[value ushr 4])
        append(HexDigits[value and 0x0f])
    }
}

private const val AndroidKeyStore = "AndroidKeyStore"
private const val KeyAlias = "app.naviamp.connect.device-identity.v1"
private const val SignatureAlgorithm = "SHA256withECDSA"
private const val HexDigits = "0123456789abcdef"
