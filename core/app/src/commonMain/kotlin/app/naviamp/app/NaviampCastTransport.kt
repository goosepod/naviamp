package app.naviamp.app

/** Native TLS byte stream only. Core authenticates the device before sending product requests. */
interface NaviampCastTransportConnection {
    val peerCertificateDer: ByteArray
    val localAddress: String
    suspend fun send(frame: ByteArray)
    suspend fun receive(): ByteArray?
    fun close()
}

fun interface NaviampCastTransportFactory {
    suspend fun connect(endpoint: NaviampCastEndpoint): NaviampCastTransportConnection
}

data class NaviampCastCertificateDates(val notBeforeEpochMillis: Long, val notAfterEpochMillis: Long)

/** X.509 parsing/path validation and RSA verification are cryptographic library operations. */
interface NaviampCastCryptoEffect {
    fun randomBytes(count: Int): ByteArray
    fun certificateDates(der: ByteArray): NaviampCastCertificateDates
    fun verifyCertificateChain(chain: List<ByteArray>, roots: List<ByteArray>, atEpochMillis: Long): Boolean
    fun verifySha256Rsa(certificate: ByteArray, signature: ByteArray, data: ByteArray): Boolean
}
