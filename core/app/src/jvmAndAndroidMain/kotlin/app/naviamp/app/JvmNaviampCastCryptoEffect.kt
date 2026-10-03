package app.naviamp.app

import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertPathBuilder
import java.security.cert.CertStore
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Date

/** JCA X.509/PKIX/RSA boundary. Nonce, algorithms, trusted roots, and TLS lifetime policy stay common. */
class JvmNaviampCastCryptoEffect : NaviampCastCryptoEffect {
    private val random = SecureRandom()
    override fun randomBytes(count: Int) = ByteArray(count).also(random::nextBytes)
    private fun certificate(der: ByteArray) = CertificateFactory.getInstance("X.509")
        .generateCertificate(der.inputStream()) as X509Certificate

    override fun certificateDates(der: ByteArray): NaviampCastCertificateDates = certificate(der).let {
        NaviampCastCertificateDates(it.notBefore.time, it.notAfter.time)
    }

    override fun verifyCertificateChain(chain: List<ByteArray>, roots: List<ByteArray>, atEpochMillis: Long): Boolean =
        runCatching {
            val certificates = chain.map(::certificate)
            val anchors = roots.map { TrustAnchor(certificate(it), null) }.toSet()
            val parameters = PKIXBuilderParameters(anchors, X509CertSelector().apply { certificate = certificates.first() })
            parameters.date = Date(atEpochMillis)
            // This operation validates a supplied path only. Cast's signed protobuf CRL is a separate common protocol.
            parameters.isRevocationEnabled = false
            parameters.addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(certificates)))
            CertPathBuilder.getInstance("PKIX").build(parameters)
            true
        }.getOrDefault(false)

    override fun verifySha256Rsa(certificate: ByteArray, signature: ByteArray, data: ByteArray): Boolean =
        runCatching {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(certificate(certificate).publicKey)
                update(data)
                verify(signature)
            }
        }.getOrDefault(false)
}
