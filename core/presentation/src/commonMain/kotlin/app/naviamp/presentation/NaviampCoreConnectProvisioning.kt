package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectPortableSettings
import app.naviamp.domain.connect.NaviampConnectProvisioningEndpoint
import app.naviamp.domain.connect.NaviampConnectProvisioningHeader
import app.naviamp.domain.connect.NaviampConnectProvisioningProfile
import app.naviamp.domain.connect.NaviampConnectProvisioningCompanion
import app.naviamp.domain.source.AurralCompanion
import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionTlsSettings
import app.naviamp.domain.settings.ConnectionFormHeader
import app.naviamp.domain.settings.ConnectionFormSecondaryUrl
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.normalized

internal sealed interface NaviampCoreConnectProvisioningExport {
    data class Ready(val profile: NaviampConnectProvisioningProfile) : NaviampCoreConnectProvisioningExport
    data class Unsupported(
        val message: String,
        val credentialUnavailable: Boolean = false,
    ) : NaviampCoreConnectProvisioningExport
}

/** Removes machine-local certificate paths before any connection data enters the wire contract. */
internal fun ConnectionFormState.toConnectProvisioningExport(): NaviampCoreConnectProvisioningExport {
    val companion = aurralCompanion?.normalized()?.takeUnless {
        providerId.trim() == app.naviamp.domain.provider.ProviderIdAurral
    }
    if (customCertificatePath.isNotBlank() || clientCertificatePath.isNotBlank() ||
        !companion?.tlsSettings?.customCertificatePath.isNullOrBlank() ||
        !companion?.tlsSettings?.clientCertificateKeyStorePath.isNullOrBlank()) {
        return NaviampCoreConnectProvisioningExport.Unsupported(
            "Connections using local certificate files must still be configured directly on the TV.",
        )
    }
    if (password.isBlank()) {
        return NaviampCoreConnectProvisioningExport.Unsupported(
            "The current connection credential is unavailable for secure transfer.",
            credentialUnavailable = true,
        )
    }
    return NaviampCoreConnectProvisioningExport.Ready(
        NaviampConnectProvisioningProfile(
            aurralCompanion = companion?.let { value -> NaviampConnectProvisioningCompanion(
                enabled = value.enabled, serverUrl = value.baseUrl, username = value.username,
                password = value.password, skipTlsVerification = value.tlsSettings.insecureSkipTlsVerification,
                customHeaders = value.customHeaders.map { NaviampConnectProvisioningHeader(it.name, it.value.orEmpty(), it.valueIsSecret) },
            ) },
            providerId = providerId.trim(),
            displayName = displayName.trim(),
            serverUrl = serverUrl.trim(),
            username = username.trim(),
            password = password,
            skipTlsVerification = skipTlsVerification,
            secondaryUrls = secondaryUrls.mapNotNull { endpoint ->
                endpoint.url.trim().takeIf(String::isNotEmpty)?.let { url ->
                    NaviampConnectProvisioningEndpoint(url, endpoint.label.trim())
                }
            },
            customHeaders = customHeaders.mapNotNull { header ->
                header.name.trim().takeIf(String::isNotEmpty)?.let { name ->
                    NaviampConnectProvisioningHeader(name, header.value, header.valueIsSecret)
                }
            },
            selectedLibraryIds = selectedMusicFolderIds.map(String::trim)
                .filter(String::isNotEmpty)
                .distinct(),
        ),
    )
}

internal fun NaviampConnectProvisioningProfile.toConnectionFormState() = ConnectionFormState(
    aurralCompanion = aurralCompanion?.let { value -> AurralCompanion(
        enabled = value.enabled, baseUrl = value.serverUrl, username = value.username, password = value.password,
        tlsSettings = ConnectionTlsSettings(insecureSkipTlsVerification = value.skipTlsVerification),
        customHeaders = value.customHeaders.map { ConnectionHeaderDefinition(it.name, it.value, it.secret) },
    ).normalized() },
    providerId = providerId,
    displayName = displayName,
    serverUrl = serverUrl,
    username = username,
    password = password,
    skipTlsVerification = skipTlsVerification,
    secondaryUrls = secondaryUrls.map { ConnectionFormSecondaryUrl(it.url, it.label) },
    customHeaders = customHeaders.map { ConnectionFormHeader(it.name, it.value, it.secret) },
    selectedMusicFolderIds = selectedLibraryIds,
)

internal fun NaviampCoreState.toConnectPortableSettings() = NaviampConnectPortableSettings(
    interfaceSettings = shell.general.interfaceSettings,
    playbackSettings = shell.playback.settings,
)

/** Applies only cross-device preferences; paths, device choice, gestures, and host policy remain local. */
internal fun InterfaceSettings.withConnectPortableValues(offered: InterfaceSettings): InterfaceSettings =
    offered.copy(
        checkForUpdates = checkForUpdates,
        applicationUpdateChannel = applicationUpdateChannel,
        startPlayingOnLaunch = startPlayingOnLaunch,
        showDesktopTooltips = showDesktopTooltips,
        globalKeyboardShortcuts = globalKeyboardShortcuts,
        trackSwipes = trackSwipes,
    ).normalized()

internal fun PlaybackSettings.withConnectPortableValues(offered: PlaybackSettings): PlaybackSettings =
    offered.copy(
        outputDevice = outputDevice,
        volumePercent = volumePercent,
        debugLoggingEnabled = debugLoggingEnabled,
        mobileStreamingQuality = mobileStreamingQuality,
        downloadQuality = downloadQuality,
        downloadedTrackPlayback = downloadedTrackPlayback,
        allowMobileDownloads = allowMobileDownloads,
    ).normalized()
