package app.naviamp.domain.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SettingsSyncFileName = "naviamp-settings.json"
const val CurrentSettingsSyncSchemaVersion = 1
const val ProviderIdNavidrome = "navidrome"

@Serializable
data class SettingsSyncDocument(
    val schemaVersion: Int = CurrentSettingsSyncSchemaVersion,
    val updatedAtEpochMillis: Long = 0L,
    val lastWriterDeviceId: String? = null,
    val serverProfiles: List<SettingsSyncServerProfile> = emptyList(),
    val preferences: SettingsSyncPreferences = SettingsSyncPreferences(),
) {
    fun normalized(): SettingsSyncDocument =
        copy(
            lastWriterDeviceId = lastWriterDeviceId?.trim()?.takeIf { it.isNotEmpty() },
            serverProfiles = serverProfiles.mapNotNull { it.normalized() },
            preferences = preferences.normalized(),
        )
}

@Serializable
data class SettingsSyncServerProfile(
    val id: String,
    val providerId: String = ProviderIdNavidrome,
    val displayName: String,
    val username: String,
    val primaryUrl: String,
    val secondaryUrls: List<SettingsSyncServerEndpoint> = emptyList(),
    val tls: SettingsSyncTlsSettings = SettingsSyncTlsSettings(),
    val customHeaders: List<SettingsSyncHeaderDefinition> = emptyList(),
) {
    fun normalized(): SettingsSyncServerProfile? {
        val normalizedId = id.trim()
        val normalizedPrimaryUrl = normalizeSyncUrl(primaryUrl)
        if (normalizedId.isEmpty() || normalizedPrimaryUrl == null) return null
        return copy(
            id = normalizedId,
            providerId = providerId.trim().ifEmpty { ProviderIdNavidrome },
            displayName = displayName.trim().ifEmpty { normalizedPrimaryUrl },
            username = username.trim(),
            primaryUrl = normalizedPrimaryUrl,
            secondaryUrls = secondaryUrls
                .mapNotNull { it.normalized() }
                .filterNot { it.url == normalizedPrimaryUrl }
                .distinctBy { it.url },
            tls = tls.normalized(),
            customHeaders = customHeaders
                .mapNotNull { it.normalized() }
                .distinctBy { it.name.lowercase() },
        )
    }
}

@Serializable
data class SettingsSyncServerEndpoint(
    val url: String,
    val label: String? = null,
    val priority: Int = 0,
) {
    fun normalized(): SettingsSyncServerEndpoint? {
        val normalizedUrl = normalizeSyncUrl(url) ?: return null
        return copy(
            url = normalizedUrl,
            label = label?.trim()?.takeIf { it.isNotEmpty() },
            priority = priority.coerceAtLeast(0),
        )
    }
}

@Serializable
data class SettingsSyncTlsSettings(
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String? = null,
    val clientCertificateKeyStorePath: String? = null,
) {
    fun normalized(): SettingsSyncTlsSettings =
        copy(
            customCertificatePath = customCertificatePath?.trim()?.takeIf { it.isNotEmpty() },
            clientCertificateKeyStorePath = clientCertificateKeyStorePath?.trim()?.takeIf { it.isNotEmpty() },
        )
}

@Serializable
data class SettingsSyncHeaderDefinition(
    val name: String,
    val value: String? = null,
    val valueIsSecret: Boolean = false,
) {
    fun normalized(): SettingsSyncHeaderDefinition? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return null
        val normalizedValue = value?.trim()?.takeIf { it.isNotEmpty() }
        return copy(
            name = normalizedName,
            value = normalizedValue.takeUnless { valueIsSecret },
            valueIsSecret = valueIsSecret,
        )
    }
}

@Serializable
data class SettingsSyncPreferences(
    val playback: PlaybackSettings = PlaybackSettings(),
    val visualizer: VisualizerSettings = VisualizerSettings(),
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentInternetRadioStations: List<SavedInternetRadioStation> = emptyList(),
) {
    fun normalized(): SettingsSyncPreferences =
        copy(
            playback = playback.copy(
                wifiStreamingQuality = playback.wifiStreamingQuality.normalized(),
                mobileStreamingQuality = playback.mobileStreamingQuality.normalized(),
                downloadQuality = playback.downloadQuality.normalized(),
                radioDjs = playback.radioDjs.map { it.normalized() },
            ),
            recentRadioStreams = recentRadioStreams.take(MaxSyncedRecentItems),
            recentInternetRadioStations = recentInternetRadioStations.take(MaxSyncedRecentItems),
        )
}

object SettingsSyncJson {
    val format: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(document: SettingsSyncDocument): String =
        format.encodeToString(SettingsSyncDocument.serializer(), document.normalized())

    fun decode(text: String): SettingsSyncDocument =
        format.decodeFromString(SettingsSyncDocument.serializer(), text).normalized()
}

private fun normalizeSyncUrl(url: String): String? =
    url.trim().trimEnd('/').takeIf { it.isNotEmpty() }

private const val MaxSyncedRecentItems = 50
