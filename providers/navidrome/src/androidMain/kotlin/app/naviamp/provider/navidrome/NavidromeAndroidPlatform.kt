package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.network.tls.CertificateAndKey
import java.io.FileInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.Key
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual fun navidromeMd5(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
    return digest.joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(2, '0')
    }
}

actual fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    KtorNavidromeHttpClient(createDefaultNavidromeKtorClient(tlsSettings))

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient =
    HttpClient(CIO) {
        expectSuccess = false
        engine {
            if (tlsSettings != NavidromeTlsSettings()) {
                https {
                    tlsSettings.trustManager()?.let { trustManager = it }
                    tlsSettings.certificateAndKey()?.let { certificates.add(it) }
                }
            }
        }
        install(io.ktor.client.plugins.HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

internal actual fun navidromeCurrentTimeMillis(): Long = System.currentTimeMillis()

private fun NavidromeTlsSettings.trustManager(): TrustManager? =
    trustManagers()?.firstOrNull()

private fun NavidromeTlsSettings.trustManagers(): Array<TrustManager>? =
    when {
        insecureSkipTlsVerification -> arrayOf(TrustAllCertificates)
        hasCustomCertificate -> {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                FileInputStream(customCertificatePath!!).use { input ->
                    val certificates = CertificateFactory.getInstance("X.509").generateCertificates(input)
                    certificates.forEachIndexed { index, certificate ->
                        setCertificateEntry("naviamp-custom-$index", certificate)
                    }
                }
            }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
                init(keyStore)
                trustManagers
            }
        }
        else -> null
    }

private fun NavidromeTlsSettings.certificateAndKey(): CertificateAndKey? {
    if (!hasClientCertificate) return null
    val password = clientCertificateKeyStorePassword.orEmpty().toCharArray()
    val keyStore = KeyStore.getInstance("PKCS12").apply {
        FileInputStream(clientCertificateKeyStorePath!!).use { input ->
            load(input, password)
        }
    }
    val alias = keyStore.aliases().asSequence().firstOrNull { keyStore.isKeyEntry(it) } ?: return null
    val key: Key = keyStore.getKey(alias, password) ?: return null
    val privateKey = key as? PrivateKey ?: return null
    val certificateChain = keyStore.getCertificateChain(alias)
        ?.mapNotNull { it as? X509Certificate }
        ?.toTypedArray()
        ?: return null
    return CertificateAndKey(certificateChain, privateKey)
}

private object TrustAllCertificates : X509TrustManager {
    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
}

