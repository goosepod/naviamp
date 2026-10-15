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

actual fun createDefaultNavidromeKtorClient(tlsSettings: NavidromeTlsSettings, onTlsChallenge: (NavidromeTlsChallengeEvent) -> Unit): HttpClient {
    tlsSettings.requireSupportedBy(navidromeTlsCapabilities())
    val material = NavidromeAppleTlsMaterial(tlsSettings)
    return HttpClient(Darwin) {
        engine {
            configureSession {
                URLCredentialStorage = null
                URLCache = null
            }
            handleChallenge { _, _, challenge, completionHandler ->
                    val protectionSpace = challenge.protectionSpace
                    val kind = when (protectionSpace.authenticationMethod) {
                        NSURLAuthenticationMethodServerTrust -> NavidromeTlsChallenge.ServerTrust
                        NSURLAuthenticationMethodClientCertificate -> NavidromeTlsChallenge.ClientIdentity
                        else -> NavidromeTlsChallenge.Other
                    }
                    val action = tlsSettings.challengeAction(kind, challenge.previousFailureCount)
                    fun report(accepted: Boolean? = null, error: String? = null) = reportNavidromeTlsChallenge(onTlsChallenge,
                        NavidromeTlsChallengeEvent(kind, challenge.previousFailureCount, action, accepted, error))
                    when (action) {
                        NavidromeTlsChallengeAction.Cancel -> {
                            report(false)
                            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                        }
                        NavidromeTlsChallengeAction.DefaultHandling -> {
                            report()
                            completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
                        }
                        NavidromeTlsChallengeAction.UseClientIdentity -> {
                            val credential = material.credential
                            report(credential != null)
                            completionHandler(if (credential != null) NSURLSessionAuthChallengeUseCredential else NSURLSessionAuthChallengeCancelAuthenticationChallenge, credential)
                        }
                        NavidromeTlsChallengeAction.EvaluateServerTrust -> {
                            val trust = protectionSpace.serverTrust
                            val result = if (trust == null) false to null else runCatching { material.evaluateServerTrust(trust, tlsSettings.insecureSkipTlsVerification) }
                                .getOrElse { false to it.message }
                            val accepted = result.first
                            report(accepted, result.second)
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
