package app.naviamp.domain.connect

import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncPreferences
import app.naviamp.domain.settings.buildSettingsSyncDocument
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.SubsonicAuthApiKey
import app.naviamp.domain.source.visibleServerConnections

/** What the sending device can include for one saved account. */
enum class NaviampConnectTransferReadiness {
    Ready,

    /** No password or API key is stored (only session tokens, or none); the user may type one. */
    NeedsPassword,

    /** Certificate files exist only on this device. */
    SetUpOnDevice,
}

/** Readiness of each account the transfer would offer, keyed by saved source id. */
fun naviampConnectTransferReadiness(
    sources: List<SavedMediaSource>,
    activeSourceId: String? = null,
    typedSecrets: Map<String, String> = emptyMap(),
): Map<String, NaviampConnectTransferReadiness> =
    sources.visibleServerConnections(activeSourceId).associate { source ->
        source.id to source.transferReadiness(typedSecrets[source.id])
    }

/**
 * Builds a one-time copy of the selected accounts (all visible accounts when [selectedSourceIds]
 * is null) and, when [includeSettings], the portable preferences. Only long-lived secrets travel:
 * a password, or an API key held in the token column for API-key accounts. Session tokens never do.
 */
fun buildNaviampConnectAccountTransfer(
    snapshot: SettingsSyncLocalSnapshot,
    activeSourceId: String?,
    includeSettings: Boolean,
    selectedSourceIds: Set<String>? = null,
    typedSecrets: Map<String, String> = emptyMap(),
    nowEpochMillis: Long,
    deviceId: String,
): NaviampConnectAccountTransfer {
    val chosen = snapshot.serverProfiles.visibleServerConnections(activeSourceId)
        .filter { selectedSourceIds == null || it.id in selectedSourceIds }
        // Keep the device's own account order rather than the most-recent ordering.
        .sortedBy { source -> snapshot.serverProfiles.indexOfFirst { it.id == source.id } }
    val ready = chosen.filter { it.transferReadiness(typedSecrets[it.id]) == NaviampConnectTransferReadiness.Ready }
    val document = buildSettingsSyncDocument(
        snapshot = snapshot.copy(serverProfiles = ready),
        nowEpochMillis = nowEpochMillis,
        deviceId = deviceId,
    ).let { built -> if (includeSettings) built else built.copy(preferences = SettingsSyncPreferences()) }
    return NaviampConnectAccountTransfer(
        document = document,
        credentials = ready.mapNotNull { it.portableCredential(typedSecrets[it.id]) },
        activeProfileId = activeSourceId?.takeIf { active -> ready.any { it.id == active } },
        includeSettings = includeSettings,
        skipped = (chosen - ready.toSet()).map { source ->
            NaviampConnectSkippedAccount(
                displayName = source.displayName,
                providerId = source.providerId,
                reason = when (source.transferReadiness(typedSecrets[source.id])) {
                    NaviampConnectTransferReadiness.SetUpOnDevice -> NaviampConnectAccountSkipReason.SetUpOnDevice
                    else -> NaviampConnectAccountSkipReason.NeedsPassword
                },
            )
        },
    )
}

private fun SavedMediaSource.transferReadiness(typedSecret: String?): NaviampConnectTransferReadiness = when {
    usesCertificateFiles() -> NaviampConnectTransferReadiness.SetUpOnDevice
    portableCredential(typedSecret) == null -> NaviampConnectTransferReadiness.NeedsPassword
    else -> NaviampConnectTransferReadiness.Ready
}

private fun SavedMediaSource.usesCertificateFiles(): Boolean =
    tlsSettings.hasCustomCertificate || tlsSettings.hasClientCertificate ||
        aurralCompanion?.takeIf { it.enabled }?.tlsSettings?.let { it.hasCustomCertificate || it.hasClientCertificate } == true

private fun SavedMediaSource.portableCredential(typedSecret: String?): NaviampConnectAccountCredential? {
    val apiKeyMode = authenticationMode == SubsonicAuthApiKey
    val secret = typedSecret?.takeIf { it.isNotBlank() }
        ?: (if (apiKeyMode) token else password)?.takeIf { it.isNotBlank() }
        ?: return null
    val companion = aurralCompanion?.takeIf { it.enabled }
    return NaviampConnectAccountCredential(
        profileId = id,
        kind = if (apiKeyMode) NaviampConnectCredentialKind.ApiKey else NaviampConnectCredentialKind.Password,
        secret = secret,
        headerSecrets = customHeaders.secretValues(),
        companionPassword = companion?.password?.takeIf { it.isNotBlank() },
        companionHeaderSecrets = companion?.customHeaders.orEmpty().secretValues(),
    )
}

private fun List<app.naviamp.domain.source.ConnectionHeaderDefinition>.secretValues(): Map<String, String> =
    filter { it.valueIsSecret && !it.value.isNullOrBlank() }.associate { it.name to it.value.orEmpty() }
