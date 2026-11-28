package app.naviamp.domain.connect

import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.domain.settings.toConnectionFormState
import app.naviamp.domain.source.SavedMediaSource
import app.naviamp.domain.source.SubsonicAuthApiKey
import app.naviamp.domain.source.SubsonicAuthToken
import app.naviamp.domain.source.effectiveServerConnectionKey
import app.naviamp.domain.source.normalizedMusicFolderIds
import app.naviamp.domain.source.stableServerConnectionKey

/** One received account, ready for the receiver's normal validation. Contains secrets. */
data class NaviampConnectPlannedAccount(
    val profileId: String,
    val displayName: String,
    val providerId: String,
    val form: ConnectionFormState,
    /** The receiver's account for the same provider, server, and user; its credential is updated. */
    val existingSourceId: String?,
) {
    override fun toString(): String =
        "NaviampConnectPlannedAccount(profileId=$profileId, providerId=$providerId, existingSourceId=$existingSourceId)"
}

/**
 * Merges received profiles into the receiver's accounts: a new provider + server + user is added;
 * the same one keeps the receiver's library selection and name and only its credential and
 * connection details are updated. Existing accounts are never removed. Profiles without a
 * credential are not planned (the sender reports them as skipped).
 */
fun NaviampConnectAccountTransfer.plannedAccounts(existing: List<SavedMediaSource>): List<NaviampConnectPlannedAccount> {
    val credentialsById = credentials.associateBy { it.profileId }
    return document.serverProfiles.mapNotNull { raw ->
        val profile = raw.normalized() ?: return@mapNotNull null
        val credential = credentialsById[raw.id] ?: credentialsById[profile.id] ?: return@mapNotNull null
        val key = stableServerConnectionKey(profile.providerId, profile.primaryUrl, profile.username)
        val matches = existing.filter { it.effectiveServerConnectionKey() == key }
        val match = matches.firstOrNull {
            normalizedMusicFolderIds(it.selectedMusicFolderIds).toSet() ==
                normalizedMusicFolderIds(profile.selectedMusicFolderIds).toSet()
        } ?: matches.maxByOrNull { it.lastConnectedAtEpochMillis ?: it.createdAtEpochMillis }
        val base = profile.toConnectionFormState(password = "").copy(
            // Certificate files never travel; an account that needed them was not sent.
            customCertificatePath = "",
            clientCertificatePath = "",
            clientCertificatePassword = "",
            customHeaders = profile.toConnectionFormState().customHeaders.map { header ->
                if (!header.valueIsSecret) header
                else header.copy(value = credential.headerSecrets.secretFor(header.name).orEmpty())
            },
            aurralCompanion = profile.aurralCompanion?.let { companion ->
                companion.copy(
                    password = credential.companionPassword.orEmpty(),
                    customHeaders = companion.customHeaders.map { header ->
                        if (!header.valueIsSecret) header
                        else header.copy(value = credential.companionHeaderSecrets.secretFor(header.name))
                    },
                )
            },
        )
        val withCredential = when (credential.kind) {
            NaviampConnectCredentialKind.ApiKey ->
                base.copy(authenticationMode = SubsonicAuthApiKey, apiKey = credential.secret, password = "")
            NaviampConnectCredentialKind.Password -> base.copy(
                authenticationMode = profile.authenticationMode.takeUnless { it == SubsonicAuthApiKey }
                    ?: SubsonicAuthToken,
                password = credential.secret,
            )
        }
        val form = if (match == null) withCredential else withCredential.copy(
            selectedMusicFolderIds = match.selectedMusicFolderIds,
            displayName = match.displayName.takeUnless { it == match.baseUrl }.orEmpty(),
        )
        NaviampConnectPlannedAccount(
            profileId = profile.id,
            displayName = match?.displayName ?: profile.displayName,
            providerId = profile.providerId,
            form = form,
            existingSourceId = match?.id,
        )
    }
}

private fun Map<String, String>.secretFor(name: String): String? =
    entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
