package app.naviamp.domain.settings

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.SavedMediaSource

data class SettingsSyncLocalSnapshot(
    val serverProfiles: List<SavedMediaSource> = emptyList(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val visualizer: VisualizerSettings = VisualizerSettings(),
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentInternetRadioStations: List<SavedInternetRadioStation> = emptyList(),
)

data class ImportedSettingsSyncServerProfiles(
    val importedCount: Int = 0,
    val firstConnectionForm: ConnectionFormState? = null,
)

fun buildSettingsSyncDocument(
    snapshot: SettingsSyncLocalSnapshot,
    nowEpochMillis: Long,
    deviceId: String,
): SettingsSyncDocument =
    SettingsSyncDocument(
        updatedAtEpochMillis = nowEpochMillis,
        lastWriterDeviceId = deviceId,
        serverProfiles = snapshot.serverProfiles.map { it.toSettingsSyncServerProfile() },
        preferences = SettingsSyncPreferences(
            playback = snapshot.playback.withoutDeviceLocalSettings(),
            visualizer = snapshot.visualizer,
            recentRadioStreams = snapshot.recentRadioStreams,
            recentInternetRadioStations = snapshot.recentInternetRadioStations,
        ),
    ).normalized()

fun SavedMediaSource.toSettingsSyncServerProfile(): SettingsSyncServerProfile =
    SettingsSyncServerProfile(
        id = id,
        providerId = providerId,
        displayName = displayName,
        username = username,
        primaryUrl = baseUrl,
        secondaryUrls = secondaryUrls.map { url ->
            SettingsSyncServerEndpoint(
                url = url.url,
                label = url.label,
                priority = url.priority,
            )
        },
        tls = SettingsSyncTlsSettings(
            insecureSkipTlsVerification = tlsSettings.insecureSkipTlsVerification,
            customCertificatePath = tlsSettings.customCertificatePath,
            clientCertificateKeyStorePath = tlsSettings.clientCertificateKeyStorePath,
        ),
        customHeaders = customHeaders.map { header ->
            SettingsSyncHeaderDefinition(
                name = header.name,
                value = header.value.takeUnless { header.valueIsSecret },
                valueIsSecret = header.valueIsSecret,
            )
        },
    )

fun SettingsSyncServerProfile.toConnectionFormState(
    password: String = "",
): ConnectionFormState =
    normalized()
        ?.let { profile ->
            ConnectionFormState(
                displayName = profile.displayName,
                serverUrl = profile.primaryUrl,
                username = profile.username,
                password = password,
                skipTlsVerification = profile.tls.insecureSkipTlsVerification,
                customCertificatePath = profile.tls.customCertificatePath.orEmpty(),
                clientCertificatePath = profile.tls.clientCertificateKeyStorePath.orEmpty(),
                clientCertificatePassword = "",
                secondaryUrls = profile.secondaryUrls.map { endpoint ->
                    ConnectionFormSecondaryUrl(
                        url = endpoint.url,
                        label = endpoint.label.orEmpty(),
                    )
                },
                customHeaders = profile.customHeaders.map { header ->
                    ConnectionFormHeader(
                        name = header.name,
                        value = header.value.orEmpty(),
                        valueIsSecret = header.valueIsSecret,
                    )
                },
            )
        }
        ?: ConnectionFormState(password = password)

fun importSettingsSyncServerProfiles(
    serverProfiles: List<SettingsSyncServerProfile>,
    repository: ProviderMediaSourceRepository,
): ImportedSettingsSyncServerProfiles {
    val profiles = serverProfiles.mapNotNull { it.normalized() }
    var importedCount = 0
    profiles.forEach { profile ->
        repository.upsertProviderMediaSource(
            connection = profile.toProviderMediaSourceConnection(),
            cacheNamespace = profile.cacheNamespace,
            providerId = profile.providerId,
        )
        importedCount++
    }
    return ImportedSettingsSyncServerProfiles(
        importedCount = importedCount,
        firstConnectionForm = profiles.firstOrNull()?.toConnectionFormState(password = ""),
    )
}

private fun SettingsSyncServerProfile.toProviderMediaSourceConnection(): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        displayName = displayName,
        baseUrl = primaryUrl,
        username = username,
        token = "",
        salt = "",
        nativeToken = null,
        tlsSettings = ConnectionTlsSettings(
            insecureSkipTlsVerification = tls.insecureSkipTlsVerification,
            customCertificatePath = tls.customCertificatePath,
            clientCertificateKeyStorePath = tls.clientCertificateKeyStorePath,
            clientCertificateKeyStorePassword = null,
        ),
        secondaryUrls = secondaryUrls.map {
            ConnectionSecondaryUrl(
                url = it.url,
                label = it.label,
                priority = it.priority,
            )
        },
        customHeaders = customHeaders.map {
            ConnectionHeaderDefinition(
                name = it.name,
                value = it.value,
                valueIsSecret = it.valueIsSecret,
            )
        },
    )

private val SettingsSyncServerProfile.cacheNamespace: String
    get() = "${providerId}:${primaryUrl}:${username}"

private fun PlaybackSettings.withoutDeviceLocalSettings(): PlaybackSettings =
    copy(
        outputDevice = PlaybackSettings().outputDevice,
        volumePercent = PlaybackSettings().volumePercent,
        debugLoggingEnabled = false,
        allowMobileDownloads = false,
    )
