package app.naviamp.domain.settings

import app.naviamp.domain.source.SavedMediaSource

data class SettingsSyncLocalSnapshot(
    val serverProfiles: List<SavedMediaSource> = emptyList(),
    val playback: PlaybackSettings = PlaybackSettings(),
    val visualizer: VisualizerSettings = VisualizerSettings(),
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentInternetRadioStations: List<SavedInternetRadioStation> = emptyList(),
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
        tls = SettingsSyncTlsSettings(
            insecureSkipTlsVerification = tlsSettings.insecureSkipTlsVerification,
            customCertificatePath = tlsSettings.customCertificatePath,
            clientCertificateKeyStorePath = tlsSettings.clientCertificateKeyStorePath,
        ),
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
            )
        }
        ?: ConnectionFormState(password = password)

private fun PlaybackSettings.withoutDeviceLocalSettings(): PlaybackSettings =
    copy(
        volumePercent = PlaybackSettings().volumePercent,
        debugLoggingEnabled = false,
        allowMobileDownloads = false,
    )
