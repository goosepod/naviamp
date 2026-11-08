package app.naviamp.presentation

enum class NaviampConnectionCertificateKind { ServerAuthority, ClientIdentity }

const val NaviampCertificateFileMaxBytes: Int = 16_777_216

/** Native document selection and a private, durable file copy; Core owns the form transaction. */
fun interface NaviampCoreCertificateFilePort {
    suspend fun chooseAndCopy(kind: NaviampConnectionCertificateKind): String?
}
