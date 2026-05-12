package app.naviamp.provider.navidrome

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
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

actual fun navidromeMd5(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray())
    return digest.joinToString(separator = "") { byte ->
        byte.toUByte().toString(radix = 16).padStart(2, '0')
    }
}

actual fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    AndroidNavidromeHttpClient(tlsSettings)

class AndroidNavidromeHttpClient(
    private val tlsSettings: NavidromeTlsSettings = NavidromeTlsSettings(),
) : NavidromeHttpClient {
    override suspend fun get(url: String): String =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()

            try {
                val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                (connection as? HttpsURLConnection)?.applyTls(tlsSettings)
                if (connection.responseCode !in 200..299) {
                    throw NavidromeException("Navidrome returned HTTP ${connection.responseCode}.")
                }
                connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readText()
                }.also {
                    recordApiCall(
                        url = url,
                        startedAt = startedAt,
                        success = true,
                        errorMessage = null,
                    )
                }
            } catch (exception: Exception) {
                recordApiCall(
                    url = url,
                    startedAt = startedAt,
                    success = false,
                    errorMessage = exception.message ?: exception::class.simpleName,
                )
                throw exception
            }
        }

    private fun HttpsURLConnection.applyTls(tlsSettings: NavidromeTlsSettings) {
        if (tlsSettings == NavidromeTlsSettings()) return
        sslSocketFactory = createSslContext(tlsSettings).socketFactory
        if (tlsSettings.insecureSkipTlsVerification) {
            hostnameVerifier = HostnameVerifier { _, _ -> true }
        }
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

    private fun recordApiCall(
        url: String,
        startedAt: Long,
        success: Boolean,
        errorMessage: String?,
    ) {
        NavidromeApiCallHistory.record(
            NavidromeApiCall(
                endpoint = url.substringBefore("?").trimEnd('/').substringAfterLast('/').ifBlank { "unknown" },
                sanitizedUrl = url.sanitizedNavidromeUrl(),
                startedAtEpochMillis = startedAt,
                durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0),
                success = success,
                errorMessage = errorMessage,
            ),
        )
    }

    private object TrustAllCertificates : X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
    }
}

private fun String.sanitizedNavidromeUrl(): String =
    runCatching {
        val uri = URI.create(this)
        buildString {
            append(uri.path)
            val query = uri.rawQuery
                ?.split("&")
                ?.joinToString("&") { rawParam ->
                    val key = rawParam.substringBefore("=")
                    if (key in setOf("u", "t", "s")) {
                        "$key=<redacted>"
                    } else {
                        rawParam
                    }
                }
            if (!query.isNullOrBlank()) {
                append("?")
                append(query)
            }
        }
    }.getOrDefault("<unparseable url>")

