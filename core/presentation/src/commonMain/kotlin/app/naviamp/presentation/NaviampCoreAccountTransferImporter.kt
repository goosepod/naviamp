package app.naviamp.presentation

import app.naviamp.domain.connect.NaviampConnectAccountOutcome
import app.naviamp.domain.connect.NaviampConnectAccountResult
import app.naviamp.domain.connect.NaviampConnectAccountSkipReason
import app.naviamp.domain.connect.NaviampConnectAccountTransfer
import app.naviamp.domain.connect.plannedAccounts
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import kotlinx.coroutines.withTimeoutOrNull

data class NaviampCoreAccountImport(
    val results: List<NaviampConnectAccountResult>,
    val settingsApplied: Boolean,
    val activated: Boolean,
)

/**
 * Applies a received account transfer on any device (#714): validates and saves every account
 * without switching accounts, reports each one, applies the portable settings when they were sent,
 * then activates the sender's active account. Existing accounts are never removed and the
 * receiving device never asks for a password.
 */
internal class NaviampCoreAccountTransferImporter(
    private val connection: NaviampCoreConnectionController,
    private val localSnapshot: () -> SettingsSyncLocalSnapshot?,
    private val applyPreferences: ((SettingsSyncDocument) -> Unit)?,
    private val currentInterface: () -> InterfaceSettings,
    private val currentPlayback: () -> PlaybackSettings,
    private val currentSourceId: () -> String?,
    private val perAccountTimeoutMillis: Long = NaviampCoreAccountTransferAccountTimeoutMillis,
) {
    suspend fun import(transfer: NaviampConnectAccountTransfer): NaviampCoreAccountImport {
        val hadActiveAccount = currentSourceId() != null
        val planned = transfer.plannedAccounts(localSnapshot()?.serverProfiles.orEmpty())
        val savedByProfile = linkedMapOf<String, String>()
        val results = planned.map { account ->
            val savedId = withTimeoutOrNull(perAccountTimeoutMillis) {
                connection.saveTransferredConnection(account.form, account.existingSourceId)
            }
            if (savedId != null) savedByProfile[account.profileId] = savedId
            NaviampConnectAccountResult(
                displayName = account.displayName,
                providerId = account.providerId,
                outcome = when {
                    savedId == null -> NaviampConnectAccountOutcome.Failed
                    account.existingSourceId != null -> NaviampConnectAccountOutcome.Updated
                    else -> NaviampConnectAccountOutcome.Added
                },
            )
        } + transfer.skipped.map { skipped ->
            NaviampConnectAccountResult(
                displayName = skipped.displayName,
                providerId = skipped.providerId,
                outcome = when (skipped.reason) {
                    NaviampConnectAccountSkipReason.NeedsPassword -> NaviampConnectAccountOutcome.NeedsPassword
                    NaviampConnectAccountSkipReason.SetUpOnDevice -> NaviampConnectAccountOutcome.SetUpOnDevice
                },
            )
        }
        val settingsApplied = transfer.includeSettings && applyPreferences?.let { apply ->
            val preferences = transfer.document.preferences
            // The receiver keeps its own device-local choices (updates, gestures, output, quality).
            apply(transfer.document.copy(serverProfiles = emptyList(), preferences = preferences.copy(
                interfaceSettings = currentInterface().withConnectPortableValues(preferences.interfaceSettings),
                playback = currentPlayback().withConnectPortableValues(preferences.playback),
            )))
            true
        } == true
        // The sender's active account becomes active; a device that had none uses the first saved one.
        val toActivate = transfer.activeProfileId?.let(savedByProfile::get)
            ?: savedByProfile.values.firstOrNull()?.takeIf { !hadActiveAccount }
        val activated = toActivate != null && withTimeoutOrNull(perAccountTimeoutMillis) {
            connection.activateTransferredConnection(toActivate)
        } == true
        return NaviampCoreAccountImport(results, settingsApplied, activated)
    }
}

/** Each account is validated against its server; the whole transfer is bounded by the count. */
internal const val NaviampCoreAccountTransferAccountTimeoutMillis = 45_000L

internal fun naviampCoreAccountTransferTimeoutMillis(accounts: Int): Long =
    NaviampCoreAccountTransferAccountTimeoutMillis * (accounts.coerceAtLeast(1) + 1) + 15_000L
