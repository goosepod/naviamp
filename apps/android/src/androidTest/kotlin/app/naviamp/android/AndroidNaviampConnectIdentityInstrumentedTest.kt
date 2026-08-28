package app.naviamp.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNaviampConnectIdentityInstrumentedTest {
    @Test
    fun keystoreIdentityIsStableAndProducesVerifiableSignatures() {
        val effect = AndroidNaviampConnectDeviceIdentityEffect()
        val first = effect.loadOrCreate()
        val second = effect.loadOrCreate()
        val payload = "naviamp-connect-identity-test".encodeToByteArray()
        val signature = effect.sign(payload)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(first.publicKeyBase64)),
        )

        assertEquals(first, second)
        assertEquals(64, first.identityFingerprint.length)
        assertEquals(first.identityFingerprint.take(32), first.deviceId)
        assertTrue(
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(payload)
                verify(signature)
            },
        )
    }
}
