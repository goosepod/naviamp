package app.naviamp.domain.settings

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
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
    mediaSourceRepository: ProviderMediaSourceRepository,
    radioDjPresetRepository: RadioDjPresetRepository,
    localPlayback: PlaybackSettings = PlaybackSettings(),
): AppliedSettingsSyncDocument {
    val preferences = document.preferences
    // A receiving device's capabilities must never erase portable preferences, and a document
    // never carries another device's volume, output device, or diagnostic settings.
    val importedPlayback = preferences.playback.normalized().withDeviceLocalSettingsFrom(localPlayback)
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

/**
 * A saved connection is the same identity as an imported profile when provider, server,
 * username, and library selection match. An import can therefore only reuse credentials the
 * device already holds for that exact account; it can never redirect them to another server.
 */
internal fun SettingsSyncServerProfile.identifies(saved: SavedMediaSource): Boolean =
    saved.providerId == providerId &&
        app.naviamp.domain.source.normalizedBaseUrl(saved.baseUrl) == primaryUrl &&
        saved.username == username &&
        saved.selectedMusicFolderIds.normalizedLibraryIds() == selectedMusicFolderIds.normalizedLibraryIds()

private fun List<String>.normalizedLibraryIds(): Set<String> =
    mapNotNull { id -> id.trim().takeIf { it.isNotEmpty() } }.toSet()

/** Whether a saved connection can reconnect without asking for a password. */
fun SavedMediaSource.hasStoredCredentials(): Boolean =
    token.isNotBlank() || !password.isNullOrBlank() || !nativeToken.isNullOrBlank()

/**
 * Whether finishing an import needs the user's password: true only when the document's first
 * server profile has no saved connection on this device that already holds credentials.
 */
fun settingsSyncImportNeedsCredentials(
    document: SettingsSyncDocument,
    savedConnections: List<SavedMediaSource>,
): Boolean {
    if (document.serverProfiles.none { it.normalized() != null }) return false
    return settingsSyncImportedConnection(document, savedConnections)?.hasStoredCredentials() != true
}

/** The saved connection an applied document's first server profile resolved to on this device. */
fun settingsSyncImportedConnection(
    document: SettingsSyncDocument,
    savedConnections: List<SavedMediaSource>,
): SavedMediaSource? {
    val profile = document.serverProfiles.firstNotNullOfOrNull { it.normalized() } ?: return null
    return savedConnections.firstOrNull { profile.identifies(it) }
}

fun importSettingsSyncServerProfiles(
    serverProfiles: List<SettingsSyncServerProfile>,
    repository: ProviderMediaSourceRepository,
): ImportedSettingsSyncServerProfiles {
    val profiles = serverProfiles.mapNotNull { it.normalized() }
    var importedCount = 0
    profiles.forEach { profile ->
        val existing = (repository as? app.naviamp.domain.cache.MediaSourceRepository)?.mediaSources()
            ?.firstOrNull { profile.identifies(it) }
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
    withDeviceLocalSettingsFrom(PlaybackSettings())

/** Device-local playback settings are never exported and never replaced by an import. */
private fun PlaybackSettings.withDeviceLocalSettingsFrom(local: PlaybackSettings): PlaybackSettings =
    copy(
        outputDevice = local.outputDevice,
        volumePercent = local.volumePercent,
        debugLoggingEnabled = local.debugLoggingEnabled,
        allowMobileDownloads = local.allowMobileDownloads,
    )
