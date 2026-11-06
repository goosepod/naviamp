package app.naviamp.provider.navidrome

import app.naviamp.testkit.NaviampMutualTlsFixture
import org.junit.AfterClass
import org.junit.BeforeClass
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Real KeyStore validation with two independent authorities; never changes global/OS trust. */
class NavidromeCustomAndPlatformTrustTest {
    @Test fun addingACustomAuthorityPreservesPlatformTrustedServers() {
        material().trust.checkServerTrusted(chain(platform), "RSA")
    }

    @Test fun customAuthorityStillAuthenticatesThePrivateServer() {
        material().trust.checkServerTrusted(chain(custom), "RSA")
    }

    @Test fun unrelatedAuthorityRemainsRejected() {
        val untrusted = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(custom.untrustedClientKeyStore).use { load(it, custom.password.toCharArray()) }
        }.getCertificate("identity") as X509Certificate
        assertFailsWith<CertificateException> { material().trust.checkServerTrusted(arrayOf(untrusted), "RSA") }
    }

    private fun material(): NavidromeJvmTlsMaterial {
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setCertificateEntry("platform", certificate(platform.serverCa))
        }
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val platformTrust = factory.trustManagers.filterIsInstance<X509TrustManager>().single()
        return NavidromeJvmTlsMaterial(NavidromeTlsSettings(customCertificatePath = custom.serverCa.toString())) { platformTrust }
    }

    private fun certificate(path: java.nio.file.Path): X509Certificate = Files.newInputStream(path).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

    private fun chain(fixture: NaviampMutualTlsFixture) = arrayOf(
        certificate(fixture.directory.resolve("server.crt")), certificate(fixture.serverCa))

    companion object {
        private lateinit var platform: NaviampMutualTlsFixture
        private lateinit var custom: NaviampMutualTlsFixture
        @BeforeClass @JvmStatic fun createAuthorities() {
            platform = NaviampMutualTlsFixture()
            custom = NaviampMutualTlsFixture()
        }
        @AfterClass @JvmStatic fun closeAuthorities() {
            if (::platform.isInitialized) platform.close()
            if (::custom.isInitialized) custom.close()
        }
    }
}
