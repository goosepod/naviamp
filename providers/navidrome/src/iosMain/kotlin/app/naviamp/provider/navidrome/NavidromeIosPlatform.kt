package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlin.time.Clock

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    KtorNavidromeHttpClient(createDefaultNavidromeKtorClient(tlsSettings))

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient {
    tlsSettings.requireSupportedBy(navidromeTlsCapabilities())
    return HttpClient(Darwin) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }
}

internal actual fun navidromeCurrentTimeMillis(): Long =
    Clock.System.now().toEpochMilliseconds()

actual fun navidromeTlsCapabilities(): NavidromeTlsCapabilities =
    NavidromeTlsCapabilities(
        insecureSkipVerification = false,
        customServerCertificates = false,
        clientCertificates = false,
    )
