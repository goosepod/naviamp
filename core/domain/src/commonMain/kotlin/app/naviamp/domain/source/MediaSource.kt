package app.naviamp.domain.source

import kotlinx.serialization.Serializable

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

@Serializable
data class ConnectionSecondaryUrl(
    val url: String,
    val label: String? = null,
    val priority: Int = 0,
) {
    fun normalized(): ConnectionSecondaryUrl? {
        val normalizedUrl = normalizedBaseUrl(url).takeIf { it.isNotEmpty() } ?: return null
        return copy(
            url = normalizedUrl,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            priority = priority.coerceAtLeast(0),
        )
    }
}

@Serializable
data class ConnectionHeaderDefinition(
    val name: String,
    val value: String? = null,
    val valueIsSecret: Boolean = false,
) {
    fun normalized(): ConnectionHeaderDefinition? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return null
        return copy(
            name = normalizedName,
            value = value?.trim()?.takeIf { it.isNotEmpty() },
            valueIsSecret = valueIsSecret,
        )
    }
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
    val nativeToken: String? = null,
    val tlsSettings: ConnectionTlsSettings = ConnectionTlsSettings(),
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    val createdAtEpochMillis: Long,
    val lastConnectedAtEpochMillis: Long?,
    val lastSyncStartedAtEpochMillis: Long?,
    val lastSyncCompletedAtEpochMillis: Long?,
    val lastLibraryScanSignature: String? = null,
    val lastLibraryScanCheckedAtEpochMillis: Long? = null,
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
