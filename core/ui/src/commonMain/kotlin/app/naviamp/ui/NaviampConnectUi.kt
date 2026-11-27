package app.naviamp.ui

import app.naviamp.ui.generated.resources.connect_remote_unavailable
import app.naviamp.ui.generated.resources.connect_connection_timed_out

enum class NaviampConnectRecoveryProblem { LocalNetworkPermission, DiscoveryUnavailable, AdvertisingUnavailable, SecureStorageUnavailable }

data class NaviampConnectRecoveryUi(
    val problem: NaviampConnectRecoveryProblem,
    val canOpenSettings: Boolean = false,
    val settingsOpenFailed: Boolean = false,
)

enum class NaviampConnectStatusNotice { RemoteUnavailable, ConnectionTimedOut }

@androidx.compose.runtime.Composable
internal fun NaviampConnectSettingsUi.displayStatus(): String? = recovery?.description() ?: when (notice) {
    NaviampConnectStatusNotice.RemoteUnavailable -> org.jetbrains.compose.resources.stringResource(
        app.naviamp.ui.generated.resources.Res.string.connect_remote_unavailable)
    NaviampConnectStatusNotice.ConnectionTimedOut -> org.jetbrains.compose.resources.stringResource(
        app.naviamp.ui.generated.resources.Res.string.connect_connection_timed_out)
    null -> statusMessage?.localized()
}

enum class NaviampConnectUiRole {
    Controller,
    Target,
    ControllerAndTarget,
}

enum class NaviampConnectPairingUiPhase {
    Inactive,
    Starting,
    Advertising,
    AwaitingCode,
    Handshaking,
    Paired,
    Failed,
}

enum class NaviampConnectPlaybackDestinationUiStatus {
    Local,
    Armed,
    Connecting,
    Connected,
    Reconnecting,
    Unavailable,
    Incompatible,
}

data class NaviampConnectDiscoveredTargetUi(
    val instanceId: String,
    val displayName: String,
    val detail: String,
    val compatible: Boolean = true,
)

data class NaviampConnectTrustedDeviceUi(
    val deviceId: String,
    val displayName: String,
    val localAlias: String? = null,
    val reconnectAvailable: Boolean = false,
    val playbackTarget: Boolean = false,
)

/** Shown on the connecting device right after a code connection. */
data class NaviampConnectPostConnectUi(
    val deviceName: String,
    /** The other device can be set up and this device has a current account to send. */
    val canSignIn: Boolean,
    val signInSelected: Boolean,
    val deviceHasAccounts: Boolean,
    val canPlayOn: Boolean,
    val busy: Boolean = false,
)

data class NaviampConnectSourceMismatchUi(
    val targetName: String?,
    val canProvisionTarget: Boolean,
)

data class NaviampConnectSettingsUi(
    val available: Boolean = false,
    val role: NaviampConnectUiRole = NaviampConnectUiRole.Controller,
    val pairingPhase: NaviampConnectPairingUiPhase = NaviampConnectPairingUiPhase.Inactive,
    val pairingCode: String? = null,
    /** True while this device's "Let another device connect" panel is open. */
    val codeVisible: Boolean = false,
    val pairingCodeExpiresAtEpochMillis: Long? = null,
    val enteredPairingCode: String = "",
    /** True while the "Connect a device" panel is open; discovery runs only then. */
    val connectScreenVisible: Boolean = false,
    /** An outgoing code handshake is running; code entry waits for its result. */
    val codeEntryBusy: Boolean = false,
    val selectedTargetId: String? = null,
    val statusMessage: NaviampConnectStatusMessage? = null,
    val notice: NaviampConnectStatusNotice? = null,
    val recovery: NaviampConnectRecoveryUi? = null,
    val localDeviceName: String = "This device",
    val selectedPlaybackDeviceId: String? = null,
    val selectedPlaybackDeviceName: String? = null,
    val remotePlaybackAuthorityActive: Boolean = false,
    val playbackDestinationStatus: NaviampConnectPlaybackDestinationUiStatus =
        NaviampConnectPlaybackDestinationUiStatus.Local,
    val connectedTargetName: String? = null,
    val connectedControllerDeviceId: String? = null,
    val connectedControllerName: String? = null,
    val remoteTrackTitle: String? = null,
    val remoteArtistName: String? = null,
    val remotePlaying: Boolean = false,
    val remoteHasPrevious: Boolean = false,
    val remoteHasNext: Boolean = false,
    val remoteNowPlaying: NowPlayingUi? = null,
    val canHandoffLocalQueue: Boolean = false,
    val canReceiveRemoteQueue: Boolean = false,
    val canProvisionTarget: Boolean = false,
    val sourceMismatchRecovery: NaviampConnectSourceMismatchUi? = null,
    val postConnect: NaviampConnectPostConnectUi? = null,
    val needsProvisioningCredential: Boolean = false,
    val provisioningBusy: Boolean = false,
    val discoveredTargets: List<NaviampConnectDiscoveredTargetUi> = emptyList(),
    val trustedDevices: List<NaviampConnectTrustedDeviceUi> = emptyList(),
    val listeningPort: Int? = null,
    val manualEndpointAwaitingCode: Boolean = false,
) {
    val remoteOutputSelected: Boolean
        get() = playbackDestinationStatus != NaviampConnectPlaybackDestinationUiStatus.Local

    val canAdvertise: Boolean
        get() = role == NaviampConnectUiRole.Target || role == NaviampConnectUiRole.ControllerAndTarget

    val canDiscover: Boolean
        get() = role == NaviampConnectUiRole.Controller || role == NaviampConnectUiRole.ControllerAndTarget

    val pairingActive: Boolean
        get() = pairingPhase != NaviampConnectPairingUiPhase.Inactive &&
            pairingPhase != NaviampConnectPairingUiPhase.Failed
}

data class NaviampConnectSettingsActions(
    val onStartPairingMode: () -> Unit,
    val onStopPairingMode: () -> Unit,
    val onTargetSelected: (NaviampConnectDiscoveredTargetUi) -> Unit,
    val onTrustedDeviceSelected: (NaviampConnectTrustedDeviceUi) -> Unit,
    val onPlaybackDeviceSelected: (String?) -> Unit,
    val onLocalDeviceNameChanged: (String) -> Unit,
    val onTrustedDeviceAliasChanged: (String, String) -> Unit,
    val onForgetTrustedDevice: (String) -> Unit,
    val onPairingCodeChanged: (String) -> Unit,
    val onSubmitPairingCode: () -> Unit,
    val onRemotePrevious: () -> Unit,
    val onRemotePlayPause: () -> Unit,
    val onRemoteNext: () -> Unit,
    val onStopControlling: () -> Unit,
    val onRemoteHandoffQueue: () -> Unit,
    val onReceiveRemoteQueue: () -> Unit,
    val onProvisionTarget: () -> Unit,
    val onDismissSourceMismatchRecovery: () -> Unit,
    val remoteNowPlayingActions: NaviampNowPlayingActions,
    val onSubmitProvisioningCredential: (String) -> Unit = {},
    val onCancelProvisioningCredential: () -> Unit = {},
    val onRetryConnection: () -> Unit = {},
    val onOpenPermissionSettings: () -> Unit = {},
    val onManualEndpointSelected: (String) -> Unit = {},
    val onManualTrustedEndpointSelected: (NaviampConnectTrustedDeviceUi, String) -> Unit = { _, _ -> },
    val onConnectScreenVisibilityChanged: (Boolean) -> Unit = {},
    val onPostConnectSignInChanged: (Boolean) -> Unit = {},
    val onPostConnectPlayOnDevice: () -> Unit = {},
    val onPostConnectDone: () -> Unit = {},
    /** Back, Escape or tapping outside: close the sheet without sending anything. */
    val onPostConnectDismiss: () -> Unit = {},
)

fun disambiguateNaviampConnectDeviceNames(names: List<String>): List<String> {
    val totals = names.groupingBy { it.lowercase() }.eachCount()
    val seen = mutableMapOf<String, Int>()
    return names.map { name ->
        val key = name.lowercase()
        if (totals[key] == 1) name else "$name (${(seen[key] ?: 0) + 1})".also {
            seen[key] = (seen[key] ?: 0) + 1
        }
    }
}
