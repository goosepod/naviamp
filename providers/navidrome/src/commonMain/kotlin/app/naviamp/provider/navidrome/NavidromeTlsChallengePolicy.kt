package app.naviamp.provider.navidrome

import kotlin.io.encoding.Base64

/** Platform TLS engines report facts; this policy chooses connection-scoped authentication. */
internal enum class NavidromeTlsChallenge { ServerTrust, ClientIdentity, Other }
internal enum class NavidromeTlsChallengeAction { DefaultHandling, EvaluateServerTrust, UseClientIdentity, Cancel }

internal fun NavidromeTlsSettings.challengeAction(
    challenge: NavidromeTlsChallenge,
    previousFailures: Long,
): NavidromeTlsChallengeAction = when {
    previousFailures > 0 -> NavidromeTlsChallengeAction.Cancel
    challenge == NavidromeTlsChallenge.ClientIdentity && hasClientCertificate -> NavidromeTlsChallengeAction.UseClientIdentity
    challenge == NavidromeTlsChallenge.ClientIdentity -> NavidromeTlsChallengeAction.Cancel
    challenge == NavidromeTlsChallenge.ServerTrust && (hasCustomCertificate || insecureSkipTlsVerification) ->
        NavidromeTlsChallengeAction.EvaluateServerTrust
    else -> NavidromeTlsChallengeAction.DefaultHandling
}

/** PEM bundles and a single DER certificate are accepted consistently by native TLS adapters. */
internal fun navidromeCertificateDerChain(bytes: ByteArray): List<ByteArray> {
    require(bytes.isNotEmpty())
    val text = bytes.decodeToString()
    if (!text.contains("-----BEGIN")) return listOf(bytes)
    val marker = "-----BEGIN CERTIFICATE-----"
    val end = "-----END CERTIFICATE-----"
    val result = mutableListOf<ByteArray>()
    var remaining = text.trim()
    while (remaining.isNotEmpty()) {
        require(remaining.startsWith(marker))
        val stop = remaining.indexOf(end, marker.length)
        require(stop >= 0)
        val encoded = remaining.substring(marker.length, stop).filterNot(Char::isWhitespace)
        result += Base64.decode(encoded).also { require(it.isNotEmpty()) }
        remaining = remaining.substring(stop + end.length).trim()
    }
    require(result.isNotEmpty())
    return result
}
