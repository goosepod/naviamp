@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.errSecSuccess
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustSetAnchorCertificates
import kotlin.time.Clock

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    KtorNavidromeHttpClient(createDefaultNavidromeKtorClient(tlsSettings))

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient {
    tlsSettings.requireSupportedBy(navidromeTlsCapabilities())
    return HttpClient(Darwin) {
        engine {
            if (tlsSettings.insecureSkipTlsVerification) {
                handleChallenge { _, _, challenge, completionHandler ->
                    val protectionSpace = challenge.protectionSpace
                    val serverTrust = protectionSpace.serverTrust
                    if (
                        protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust &&
                        serverTrust != null
                    ) {
                        val peerChain = SecTrustCopyCertificateChain(serverTrust)
                        val anchorStatus = peerChain?.let {
                            SecTrustSetAnchorCertificates(serverTrust, it)
                        }
                        val reevaluated = anchorStatus == errSecSuccess &&
                            SecTrustEvaluateWithError(serverTrust, null)
                        val accepted = peerChain != null && reevaluated
                        if (accepted) {
                            completionHandler(
                                NSURLSessionAuthChallengeUseCredential,
                                NSURLCredential.credentialForTrust(serverTrust),
                            )
                        } else {
                            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                        }
                    } else {
                        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                    }
                }
            }
        }
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = NavidromeConnectTimeoutMillis
            requestTimeoutMillis = NavidromeRequestTimeoutMillis
            socketTimeoutMillis = NavidromeSocketTimeoutMillis
        }
    }
}

internal actual fun navidromeCurrentTimeMillis(): Long =
    Clock.System.now().toEpochMilliseconds()

actual fun navidromeTlsCapabilities(): NavidromeTlsCapabilities =
    NavidromeTlsCapabilities(
        insecureSkipVerification = true,
        customServerCertificates = false,
        clientCertificates = false,
    )
