package app.naviamp.provider.navidrome

import app.naviamp.domain.network.urlEncodedParameter
import app.naviamp.domain.app.PlatformCapabilities
import app.naviamp.domain.app.PlatformCapability
import io.ktor.client.HttpClient

internal const val NavidromeConnectTimeoutMillis = 15_000L
internal const val NavidromeRequestTimeoutMillis = 30_000L
internal const val NavidromeSocketTimeoutMillis = 30_000L

fun navidromeMd5(value: String): String =
    md5Hex(value.encodeToByteArray())

fun String.urlEncode(): String =
    urlEncodedParameter()

expect fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient

/** Optional native challenge facts for diagnostics; engines without delegate challenges need not emit events. */
expect fun createDefaultNavidromeKtorClient(
    tlsSettings: NavidromeTlsSettings,
    onTlsChallenge: (NavidromeTlsChallengeEvent) -> Unit = {},
): HttpClient

@OptIn(kotlin.time.ExperimentalTime::class)
internal fun navidromeCurrentTimeMillis(): Long =
    kotlin.time.Clock.System.now().toEpochMilliseconds()

data class NavidromeTlsCapabilities(
    val insecureSkipVerification: Boolean,
    val customServerCertificates: Boolean,
    val clientCertificates: Boolean,
)

fun navidromeTlsCapabilities(): NavidromeTlsCapabilities = NavidromeTlsCapabilities(
    insecureSkipVerification = PlatformCapabilities().supports(PlatformCapability.InsecureServerVerification),
    customServerCertificates = PlatformCapabilities().supports(PlatformCapability.CustomServerCertificates),
    clientCertificates = PlatformCapabilities().supports(PlatformCapability.ClientCertificates),
)

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
