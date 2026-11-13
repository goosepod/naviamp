package app.naviamp.domain.settings

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.source.SavedMediaSource

data class SettingsSyncLocalSnapshot(
    val serverProfiles: List<SavedMediaSource> = emptyList(),
    val interfaceSettings: InterfaceSettings = InterfaceSettings(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val visualizer: VisualizerSettings = VisualizerSettings(),
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentInternetRadioStations: List<SavedInternetRadioStation> = emptyList(),
)

data class ImportedSettingsSyncServerProfiles(
    val importedCount: Int = 0,
    val firstConnectionForm: ConnectionFormState? = null,
)

data class AppliedSettingsSyncDocument(
    val interfaceSettings: InterfaceSettings,
    val playbackSettings: PlaybackSettings,
    val visualizer: VisualizerSettings,
    val recentRadioStreams: List<RecentRadioStream>,
    val recentInternetRadioStations: List<SavedInternetRadioStation>,
    val importedServerProfiles: ImportedSettingsSyncServerProfiles,
)

/** Applies the platform-neutral repository mutations and normalization for a synced document. */
fun applySettingsSyncDocument(
    document: SettingsSyncDocument,
    playbackEngine: PlaybackEngine,
    mediaSourceRepository: ProviderMediaSourceRepository,
    radioDjPresetRepository: RadioDjPresetRepository,
): AppliedSettingsSyncDocument {
    val preferences = document.preferences
    val importedPlayback = preferences.playback.effectiveForEngine(playbackEngine)
    radioDjPresetRepository.replaceRadioDjPresets(importedPlayback.radioDjs)
    return AppliedSettingsSyncDocument(
        interfaceSettings = preferences.interfaceSettings.normalized(),
        playbackSettings = importedPlayback.copy(radioDjs = radioDjPresetRepository.radioDjPresets()),
        visualizer = preferences.visualizer,
        recentRadioStreams = preferences.recentRadioStreams,
        recentInternetRadioStations = preferences.recentInternetRadioStations,
        importedServerProfiles = importSettingsSyncServerProfiles(
            serverProfiles = document.serverProfiles,
            repository = mediaSourceRepository,
        ),
    )
}

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
            interfaceSettings = snapshot.interfaceSettings,
            playback = snapshot.playback.withoutDeviceLocalSettings(),
            visualizer = snapshot.visualizer,
            recentRadioStreams = snapshot.recentRadioStreams,
            recentInternetRadioStations = snapshot.recentInternetRadioStations,
        ),
    ).normalized()

fun SavedMediaSource.toSettingsSyncServerProfile(): SettingsSyncServerProfile =
    SettingsSyncServerProfile(
        aurralCompanion = aurralCompanion?.withoutSecrets(),
        id = id,
        providerId = providerId,
        displayName = displayName,
        username = username,
        authenticationMode = authenticationMode,
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
        selectedMusicFolderIds = selectedMusicFolderIds,
    )

fun SettingsSyncServerProfile.toConnectionFormState(
    password: String = "",
): ConnectionFormState =
    normalized()
        ?.let { profile ->
            ConnectionFormState(
                aurralCompanion = profile.aurralCompanion?.withoutSecrets(),
                providerId = profile.providerId,
                displayName = profile.displayName,
                serverUrl = profile.primaryUrl,
                username = profile.username,
                authenticationMode = profile.authenticationMode,
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
                selectedMusicFolderIds = profile.selectedMusicFolderIds,
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
        val existing = (repository as? app.naviamp.domain.cache.MediaSourceRepository)?.mediaSources()?.firstOrNull {
            it.providerId == profile.providerId && app.naviamp.domain.source.normalizedBaseUrl(it.baseUrl) == profile.primaryUrl &&
                it.username == profile.username && it.selectedMusicFolderIds.toSet() == profile.selectedMusicFolderIds.toSet()
        }
        repository.upsertProviderMediaSource(
            connection = profile.toProviderMediaSourceConnection(existing),
            cacheNamespace = existing?.cacheNamespace ?: profile.cacheNamespace,
            preferredSourceId = existing?.id,
            providerId = profile.providerId,
        )
        importedCount++
    }
    return ImportedSettingsSyncServerProfiles(
        importedCount = importedCount,
        firstConnectionForm = profiles.firstOrNull()?.toConnectionFormState(password = ""),
    )
}

private fun SettingsSyncServerProfile.toProviderMediaSourceConnection(existing: SavedMediaSource? = null): ProviderMediaSourceConnection =
    ProviderMediaSourceConnection(
        aurralCompanion = aurralCompanion?.withoutSecrets(),
        displayName = displayName,
        baseUrl = primaryUrl,
        username = username,
        authenticationMode = authenticationMode,
        token = existing?.token.takeIf { existing?.authenticationMode == authenticationMode }.orEmpty(),
        salt = existing?.salt.takeIf { existing?.authenticationMode == authenticationMode }.orEmpty(),
        nativeToken = existing?.nativeToken.takeIf { existing?.authenticationMode == authenticationMode },
        tlsSettings = ConnectionTlsSettings(
            insecureSkipTlsVerification = tls.insecureSkipTlsVerification,
            customCertificatePath = tls.customCertificatePath,
            clientCertificateKeyStorePath = tls.clientCertificateKeyStorePath,
            clientCertificateKeyStorePassword = existing?.tlsSettings?.clientCertificateKeyStorePassword
                .takeIf { existing?.tlsSettings?.clientCertificateKeyStorePath == tls.clientCertificateKeyStorePath },
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
                value = it.value ?: existing?.customHeaders?.firstOrNull { previous ->
                    previous.valueIsSecret && it.valueIsSecret && previous.name.equals(it.name, ignoreCase = true)
                }?.value,
                valueIsSecret = it.valueIsSecret,
            )
        },
        selectedMusicFolderIds = selectedMusicFolderIds,
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
