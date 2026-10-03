package app.naviamp.app

import app.naviamp.app.NaviampCastProto.bytes
import app.naviamp.app.NaviampCastProto.number

/** Device proof binds a trusted Cast identity to this TLS certificate and a fresh sender nonce. */
class NaviampCastDeviceAuthentication(
    private val crypto: NaviampCastCryptoEffect,
    private val nowEpochMillis: () -> Long,
    private val trustedRoots: List<ByteArray> = naviampCastTrustedRoots(),
) {
    fun nonce(): ByteArray = crypto.randomBytes(16).also { require(it.size == 16) }

    fun challenge(nonce: ByteArray): ByteArray {
        require(nonce.size == 16)
        return NaviampCastProto.encode(bytes(1, NaviampCastProto.encode(
            number(1, 1), bytes(2, nonce), number(3, 1), // PKCS1 v1.5 and SHA-256.
        )))
    }

    fun verify(reply: ByteArray, nonce: ByteArray, tlsCertificate: ByteArray): Boolean = try {
        val outer = NaviampCastProto.decode(reply)
        require(outer.none { it.id == 1 || it.id == 3 })
        val response = NaviampCastProto.decode(checkNotNull(outer.bytes(2)))
        require(response.number(4) in listOf(null, 1L) && response.number(6) == 1L)
        require(checkNotNull(response.bytes(5)).contentEquals(nonce))
        val leaf = checkNotNull(response.bytes(2))
        val signature = checkNotNull(response.bytes(1))
        val chain = listOf(leaf) + response.filter { it.id == 3 }.map { checkNotNull(it.bytes) }
        require(chain.size <= 8 && signature.isNotEmpty())
        val now = nowEpochMillis()
        val dates = crypto.certificateDates(tlsCertificate)
        require(dates.notBeforeEpochMillis <= now && dates.notAfterEpochMillis > now)
        require(dates.notAfterEpochMillis - now <= 4 * 24 * 60 * 60 * 1_000L)
        crypto.verifyCertificateChain(chain, trustedRoots, now) &&
            crypto.verifySha256Rsa(leaf, signature, nonce + tlsCertificate)
    } catch (_: Exception) {
        false
    }
}
