@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.naviamp.provider.navidrome

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLAuthenticationMethodClientCertificate
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust

actual fun createDefaultNavidromeHttpClient(tlsSettings: NavidromeTlsSettings): NavidromeHttpClient =
    KtorNavidromeHttpClient(createDefaultNavidromeKtorClient(tlsSettings))

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings): HttpClient {
    tlsSettings.requireSupportedBy(navidromeTlsCapabilities())
    val material = NavidromeAppleTlsMaterial(tlsSettings)
    return HttpClient(Darwin) {
        engine {
            handleChallenge { _, _, challenge, completionHandler ->
                    val protectionSpace = challenge.protectionSpace
                    val kind = when (protectionSpace.authenticationMethod) {
                        NSURLAuthenticationMethodServerTrust -> NavidromeTlsChallenge.ServerTrust
                        NSURLAuthenticationMethodClientCertificate -> NavidromeTlsChallenge.ClientIdentity
                        else -> NavidromeTlsChallenge.Other
                    }
                    when (tlsSettings.challengeAction(kind, challenge.previousFailureCount)) {
                        NavidromeTlsChallengeAction.Cancel -> completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        NavidromeTlsChallengeAction.DefaultHandling -> completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                        NavidromeTlsChallengeAction.UseClientIdentity -> {
                            val credential = material.credential
                            completionHandler(if (credential != null) NSURLSessionAuthChallengeUseCredential else NSURLSessionAuthChallengeCancelAuthenticationChallenge, credential)
                        }
                        NavidromeTlsChallengeAction.EvaluateServerTrust -> {
                            val trust = protectionSpace.serverTrust
                            val accepted = trust != null && runCatching { material.evaluateServerTrust(trust, tlsSettings.insecureSkipTlsVerification) }.getOrDefault(false)
                            completionHandler(if (accepted) NSURLSessionAuthChallengeUseCredential else NSURLSessionAuthChallengeCancelAuthenticationChallenge,
                                if (accepted) NSURLCredential.credentialForTrust(trust!!) else null)
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

actual fun navidromeTlsCapabilities(): NavidromeTlsCapabilities =
    NavidromeTlsCapabilities(
        insecureSkipVerification = true,
        customServerCertificates = true,
        clientCertificates = true,
    )
