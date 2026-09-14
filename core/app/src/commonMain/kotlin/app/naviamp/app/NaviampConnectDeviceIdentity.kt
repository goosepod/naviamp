package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectPublicIdentity

data class NaviampConnectDeviceIdentity(
    val deviceId: String,
    val identityFingerprint: String,
    val publicKeyBase64: String,
) {
    init {
        require(deviceId.isNotBlank()) { "A Connect device identity requires a device ID." }
        require(identityFingerprint.isNotBlank()) { "A Connect device identity requires a fingerprint." }
        require(publicKeyBase64.isNotBlank()) { "A Connect device identity requires a public key." }
    }
}

fun NaviampConnectDeviceIdentity.asPublicIdentity(): NaviampConnectPublicIdentity =
    NaviampConnectPublicIdentity(
        deviceId = deviceId,
        identityFingerprint = identityFingerprint,
        publicKeyBase64 = publicKeyBase64,
    )

/** Native secure-key effect. Private identity key material never enters shared application state. */
interface NaviampConnectDeviceIdentityEffect {
    fun loadOrCreate(): NaviampConnectDeviceIdentity
    fun sign(payload: ByteArray): ByteArray
}

/** Public-signature verification effect; private keys never enter Core. */
interface NaviampConnectIdentityVerifier {
    fun fingerprint(publicKeyBase64: String): String?
    fun verify(publicKeyBase64: String, payload: ByteArray, signature: ByteArray): Boolean
}

/** Canonical proof binds both durable identities to this PAKE-authenticated session and their roles. */
fun naviampConnectIdentityProofPayload(
    protocolVersion: Int,
    sessionId: String,
    controller: NaviampConnectPublicIdentity,
    target: NaviampConnectPublicIdentity,
): ByteArray {
    require(protocolVersion > 0) { "The identity proof protocol version must be positive." }
    require(sessionId.isNotBlank()) { "The identity proof requires a session ID." }
    return buildString {
        append("Naviamp Connect identity proof v1|")
        append(protocolVersion)
        appendLengthPrefixed(sessionId)
        appendLengthPrefixed(controller.deviceId)
        appendLengthPrefixed(controller.identityFingerprint)
        appendLengthPrefixed(controller.publicKeyBase64)
        appendLengthPrefixed(target.deviceId)
        appendLengthPrefixed(target.identityFingerprint)
        appendLengthPrefixed(target.publicKeyBase64)
    }.encodeToByteArray()
}

private fun StringBuilder.appendLengthPrefixed(value: String) {
    append('|')
    append(value.length)
    append(':')
    append(value)
}
