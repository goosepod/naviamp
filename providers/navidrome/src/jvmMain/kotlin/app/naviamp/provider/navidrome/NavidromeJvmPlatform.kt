package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    KtorNavidromeHttpClient(createDefaultNavidromeKtorClient(tlsSettings))

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings, onTlsChallenge: (NavidromeTlsChallengeEvent) -> Unit): HttpClient {
    NavidromeTls.applyJvmDefaults(tlsSettings)
    return createNavidromeJvmKtorClient(tlsSettings)
}

/** JVM legacy URLConnection defaults; provider clients always retain their own TLS material. */
object NavidromeTls {
    private val platformSslContext: SSLContext = SSLContext.getDefault()
    private val platformHostnameVerifier: HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()

    fun applyJvmDefaults(tlsSettings: NavidromeTlsSettings) {
        val context = if (tlsSettings == NavidromeTlsSettings()) platformSslContext else createSslContext(tlsSettings)
        SSLContext.setDefault(context)
        HttpsURLConnection.setDefaultSSLSocketFactory(context.socketFactory)
        HttpsURLConnection.setDefaultHostnameVerifier(
            if (tlsSettings.insecureSkipTlsVerification) HostnameVerifier { _, _ -> true } else platformHostnameVerifier,
        )
    }

    fun createSslContext(tlsSettings: NavidromeTlsSettings): SSLContext = NavidromeJvmTlsMaterial(tlsSettings).context
}
