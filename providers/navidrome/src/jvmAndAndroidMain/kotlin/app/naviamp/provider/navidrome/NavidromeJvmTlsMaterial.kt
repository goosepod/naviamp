package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.bouncycastle.jce.provider.BouncyCastleProvider

// Explicit provider instance: older Android PKCS12 implementations lack modern PBKDF2 support.
// Do not install/replace a process-wide JCA provider or change any connection's trust store.
private val NavidromePkcs12Provider by lazy { BouncyCastleProvider() }

/** JVM/Android KeyStore and SSLSocketFactory boundary, shared between both JVM-based hosts. */
internal class NavidromeJvmTlsMaterial(settings: NavidromeTlsSettings) {
    val trust: X509TrustManager = when {
        settings.insecureSkipTlsVerification -> object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        else -> {
            val store = if (settings.hasCustomCertificate) KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                val certificates = navidromeCertificateDerChain(File(checkNotNull(settings.customCertificatePath)).readBytes())
                certificates.forEachIndexed { index, der ->
                    setCertificateEntry("naviamp-custom-$index", CertificateFactory.getInstance("X.509").generateCertificate(der.inputStream()))
                }
            } else null
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(store)
            factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
    private val keys: Array<KeyManager> = if (settings.hasClientCertificate) {
        val password = settings.clientCertificateKeyStorePassword.orEmpty().toCharArray()
        try {
            val store = KeyStore.getInstance("PKCS12", NavidromePkcs12Provider).apply {
                File(checkNotNull(settings.clientCertificateKeyStorePath)).inputStream().use { load(it, password) }
            }
            require(store.aliases().asSequence().any { store.isKeyEntry(it) })
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password) }.keyManagers
        } finally { password.fill('\u0000') }
    } else emptyArray()
    val context: SSLContext = SSLContext.getInstance("TLS").apply { init(keys, arrayOf(trust), SecureRandom()) }
}

internal fun createNavidromeJvmKtorClient(settings: NavidromeTlsSettings): HttpClient {
    val material = NavidromeJvmTlsMaterial(settings)
    return HttpClient(OkHttp) {
        expectSuccess = false
        engine { config {
            sslSocketFactory(material.context.socketFactory, material.trust)
            // OkHttp performs hostname/IP SAN validation by default, even when SANs are absent.
            if (settings.insecureSkipTlsVerification) hostnameVerifier { _, _ -> true }
        } }
        install(HttpTimeout) {
            connectTimeoutMillis = NavidromeConnectTimeoutMillis
            requestTimeoutMillis = NavidromeRequestTimeoutMillis
            socketTimeoutMillis = NavidromeSocketTimeoutMillis
        }
    }
}
