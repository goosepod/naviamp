package app.naviamp.provider.navidrome

import app.naviamp.domain.network.urlEncodedParameter
import io.ktor.client.HttpClient

internal const val NavidromeConnectTimeoutMillis = 15_000L
internal const val NavidromeRequestTimeoutMillis = 30_000L
internal const val NavidromeSocketTimeoutMillis = 30_000L

fun navidromeMd5(value: String): String =
    md5Hex(value.encodeToByteArray())

fun String.urlEncode(): String =
    urlEncodedParameter()

expect fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient

expect fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient

internal expect fun navidromeCurrentTimeMillis(): Long

data class NavidromeTlsCapabilities(
    val insecureSkipVerification: Boolean,
    val customServerCertificates: Boolean,
    val clientCertificates: Boolean,
)

expect fun navidromeTlsCapabilities(): NavidromeTlsCapabilities

internal fun NavidromeTlsSettings.requireSupportedBy(capabilities: NavidromeTlsCapabilities) {
    if (insecureSkipTlsVerification && !capabilities.insecureSkipVerification) {
        throw NavidromeException("Skipping TLS certificate verification is not supported on this platform.")
    }
    if (hasCustomCertificate && !capabilities.customServerCertificates) {
        throw NavidromeException("Custom server certificates are not supported on this platform.")
    }
    if (hasClientCertificate && !capabilities.clientCertificates) {
        throw NavidromeException("Client certificates are not supported on this platform.")
    }
}
