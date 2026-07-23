package app.naviamp.android.playback

import android.annotation.SuppressLint
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object AndroidPlaybackTls {
    private val platformSslContext: SSLContext = SSLContext.getDefault()
    private val platformHostnameVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    fun applyDefaults(tlsSettings: NavidromeTlsSettings) {
        if (tlsSettings == NavidromeTlsSettings()) {
            SSLContext.setDefault(platformSslContext)
            HttpsURLConnection.setDefaultSSLSocketFactory(platformSslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier(platformHostnameVerifier)
            return
        }

        val sslContext = createSslContext(tlsSettings)
        SSLContext.setDefault(sslContext)
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(
            if (tlsSettings.insecureSkipTlsVerification) {
                HostnameVerifier { _, _ -> true }
            } else {
                platformHostnameVerifier
            },
        )
    }

    private fun createSslContext(tlsSettings: NavidromeTlsSettings): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(
            tlsSettings.keyManagers(),
            tlsSettings.trustManagers(),
            SecureRandom(),
        )
        return context
    }

    private fun NavidromeTlsSettings.trustManagers(): Array<TrustManager>? =
        when {
            insecureSkipTlsVerification -> arrayOf(TrustAllCertificates)
            hasCustomCertificate -> {
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null, null)
                    FileInputStream(customCertificatePath!!).use { input ->
                        CertificateFactory.getInstance("X.509").generateCertificates(input)
                            .forEachIndexed { index, certificate ->
                                setCertificateEntry("naviamp-playback-$index", certificate)
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

    private fun NavidromeTlsSettings.keyManagers(): Array<KeyManager>? {
        if (!hasClientCertificate) return null
        val password = clientCertificateKeyStorePassword.orEmpty().toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            FileInputStream(clientCertificateKeyStorePath!!).use { input ->
                load(input, password)
            }
        }
        return KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            init(keyStore, password)
            keyManagers
        }
    }

    // This manager is reachable only when the user explicitly enables skip-TLS-verification.
    @SuppressLint("CustomX509TrustManager")
    private object TrustAllCertificates : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }
}
