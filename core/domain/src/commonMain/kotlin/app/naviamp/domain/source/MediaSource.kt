package app.naviamp.domain.source

data class ConnectionTlsSettings(
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String? = null,
    val clientCertificateKeyStorePath: String? = null,
    val clientCertificateKeyStorePassword: String? = null,
) {
    val hasCustomCertificate: Boolean
        get() = !customCertificatePath.isNullOrBlank()

    val hasClientCertificate: Boolean
        get() = !clientCertificateKeyStorePath.isNullOrBlank()
}

data class MediaSourceIdentity(
    val id: String,
    val cacheNamespace: String,
    val displayName: String,
)

data class SavedMediaSource(
    val id: String,
    val providerId: String,
    val cacheNamespace: String,
    val displayName: String,
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    val createdAtEpochMillis: Long,
    val lastConnectedAtEpochMillis: Long?,
    val lastSyncStartedAtEpochMillis: Long?,
    val lastSyncCompletedAtEpochMillis: Long?,
)

fun normalizedBaseUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/')

fun resolvedConnectionDisplayName(displayName: String?, fallbackBaseUrl: String): String =
    displayName?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedBaseUrl(fallbackBaseUrl)

fun stableMediaSourceId(cacheNamespace: String): String {
    var hash = FnvOffsetBasis
    cacheNamespace.encodeToByteArray().forEach { byte ->
        hash = hash xor byte.toULong()
        hash *= FnvPrime
    }
    return "source_${hash.toString(radix = 16).padStart(16, '0')}"
}

private const val FnvOffsetBasis = 14695981039346656037UL
private const val FnvPrime = 1099511628211UL
