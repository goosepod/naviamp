package app.naviamp.domain.connect

import app.naviamp.domain.settings.SettingsSyncDocument
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A one-time copy of every saved account and, optionally, the portable settings (#714).
 *
 * [document] is the shared settings sync document with no secrets in it. Long-lived credentials
 * travel only in [credentials], inside the authenticated encrypted session, keyed by profile id.
 * Session tokens are never part of a transfer: the receiver signs in with its own device identity.
 */
@Serializable
data class NaviampConnectAccountTransfer(
    val document: SettingsSyncDocument,
    val credentials: List<NaviampConnectAccountCredential> = emptyList(),
    /** The sender's active account; the receiver activates it when it validates. */
    val activeProfileId: String? = null,
    /** Whether the receiver applies the portable preferences in [document]. */
    val includeSettings: Boolean = false,
    /** Accounts the sender could not include, so the receiver can report every account. */
    val skipped: List<NaviampConnectSkippedAccount> = emptyList(),
) {
    override fun toString(): String =
        "NaviampConnectAccountTransfer(profiles=${document.serverProfiles.size}, " +
            "credentials=${credentials.size}, activeProfileId=$activeProfileId, " +
            "includeSettings=$includeSettings, skipped=${skipped.size})"
}

@Serializable
enum class NaviampConnectCredentialKind {
    Password,
    ApiKey,
}

/** A long-lived secret for one profile. Never logged; [toString] is redacted. */
@Serializable
data class NaviampConnectAccountCredential(
    val profileId: String,
    val kind: NaviampConnectCredentialKind,
    val secret: String,
    /** Secret header values by header name. */
    val headerSecrets: Map<String, String> = emptyMap(),
    val companionPassword: String? = null,
    /** Secret companion header values by header name. */
    val companionHeaderSecrets: Map<String, String> = emptyMap(),
) {
    init {
        require(profileId.isNotBlank()) { "A credential must name its profile." }
        require(secret.isNotBlank()) { "A transferred credential must not be blank." }
    }

    override fun toString(): String =
        "NaviampConnectAccountCredential(profileId=$profileId, kind=$kind, secret=<redacted>, " +
            "headerSecrets=${headerSecrets.keys}, companionPassword=${companionPassword?.let { "<redacted>" }}, " +
            "companionHeaderSecrets=${companionHeaderSecrets.keys})"
}

@Serializable
enum class NaviampConnectAccountSkipReason {
    /** The sender holds no password or API key for this account. */
    NeedsPassword,

    /** The account uses certificate files that exist only on the sending device. */
    SetUpOnDevice,
}

@Serializable
data class NaviampConnectSkippedAccount(
    val displayName: String,
    val providerId: String,
    val reason: NaviampConnectAccountSkipReason,
)

@Serializable
enum class NaviampConnectAccountOutcome {
    Added,
    Updated,
    NeedsPassword,
    SetUpOnDevice,
    Failed,
}

@Serializable
data class NaviampConnectAccountResult(
    val displayName: String,
    val providerId: String,
    val outcome: NaviampConnectAccountOutcome,
)

/** Send direction: the controller signs the target in. */
@Serializable
@SerialName("offer_account_transfer")
data class NaviampConnectOfferAccountTransfer(
    val transferId: String,
    val transfer: NaviampConnectAccountTransfer,
) : NaviampConnectCommand {
    init {
        require(transferId.isNotBlank()) { "An account transfer requires an ID." }
    }
}

/** Pull direction: the controller asks the target for its accounts. */
@Serializable
@SerialName("request_account_transfer")
data class NaviampConnectRequestAccountTransfer(
    val transferId: String,
    val includeSettings: Boolean = false,
) : NaviampConnectCommand {
    init {
        require(transferId.isNotBlank()) { "An account transfer request requires an ID." }
    }
}

/** The receiver's per-account outcome for a sent transfer. */
@Serializable
@SerialName("account_transfer_result")
data class NaviampConnectAccountTransferResult(
    val transferId: String,
    val results: List<NaviampConnectAccountResult>,
    val settingsApplied: Boolean = false,
    /** The sender's active account is now the receiver's active account. */
    val activated: Boolean = false,
) : NaviampConnectMessage

/** The target's answer to [NaviampConnectRequestAccountTransfer]; null [transfer] means none to send. */
@Serializable
@SerialName("account_transfer_payload")
data class NaviampConnectAccountTransferPayload(
    val transferId: String,
    val transfer: NaviampConnectAccountTransfer? = null,
) : NaviampConnectMessage {
    override fun toString(): String = "NaviampConnectAccountTransferPayload(transferId=$transferId, transfer=$transfer)"
}
