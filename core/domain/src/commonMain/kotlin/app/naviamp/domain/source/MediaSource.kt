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
    val serverConnectionKey: String = "",
    val libraryScopeKey: String = "",
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
    val selectedMusicFolderIds: List<String> = emptyList(),
    val serverConnectionKey: String = "",
    val libraryScopeKey: String = "",
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

fun stableServerConnectionKey(
    providerId: String,
    baseUrl: String,
    username: String,
): String =
    listOf(providerId, normalizedBaseUrl(baseUrl), username.trim())
        .joinToString(":")

fun stableLibraryScopeKey(selectedMusicFolderIds: List<String>): String {
    val normalizedIds = normalizedMusicFolderIds(selectedMusicFolderIds)
    return if (normalizedIds.isEmpty()) {
        "folders=all"
    } else {
        "folders=${normalizedIds.joinToString(",")}"
    }
}

fun stableMediaSourceId(cacheNamespace: String): String {
    var hash = FnvOffsetBasis
    cacheNamespace.encodeToByteArray().forEach { byte ->
        hash = hash xor byte.toULong()
        hash *= FnvPrime
    }
    return "source_${hash.toString(radix = 16).padStart(16, '0')}"
}

fun normalizedMusicFolderIds(ids: List<String>): List<String> =
    ids.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

fun SavedMediaSource.effectiveServerConnectionKey(): String =
    serverConnectionKey.ifBlank {
        stableServerConnectionKey(
            providerId = providerId,
            baseUrl = baseUrl,
            username = username,
        )
    }

fun List<SavedMediaSource>.visibleServerConnections(currentSourceId: String? = null): List<SavedMediaSource> =
    groupBy { it.effectiveServerConnectionKey() }
        .values
        .map { sources ->
            sources.firstOrNull { it.id == currentSourceId }
                ?: sources.maxWith(
                    compareBy<SavedMediaSource> { it.lastConnectedAtEpochMillis ?: it.createdAtEpochMillis }
                        .thenBy { it.createdAtEpochMillis }
                        .thenBy { it.id },
                )
        }
        .sortedWith(
            compareByDescending<SavedMediaSource> { it.lastConnectedAtEpochMillis ?: it.createdAtEpochMillis }
                .thenBy { it.displayName }
                .thenBy { it.id },
        )

private const val FnvOffsetBasis = 14695981039346656037UL
private const val FnvPrime = 1099511628211UL
