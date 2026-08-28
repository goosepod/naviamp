package app.naviamp.ui

enum class NaviampConnectUiRole {
    Controller,
    Target,
    ControllerAndTarget,
}

enum class NaviampConnectPairingUiPhase {
    Inactive,
    Starting,
    Advertising,
    AwaitingApproval,
    AwaitingCode,
    Handshaking,
    Paired,
    Failed,
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
    val detail: String,
)

data class NaviampConnectSettingsUi(
    val available: Boolean = false,
    val role: NaviampConnectUiRole = NaviampConnectUiRole.Controller,
    val pairingPhase: NaviampConnectPairingUiPhase = NaviampConnectPairingUiPhase.Inactive,
    val pairingCode: String? = null,
    val enteredPairingCode: String = "",
    val pendingControllerName: String? = null,
    val selectedTargetId: String? = null,
    val status: String? = null,
    val connectedTargetName: String? = null,
    val remoteTrackTitle: String? = null,
    val remoteArtistName: String? = null,
    val remotePlaying: Boolean = false,
    val remoteHasPrevious: Boolean = false,
    val remoteHasNext: Boolean = false,
    val remoteNowPlaying: NowPlayingUi? = null,
    val canHandoffLocalQueue: Boolean = false,
    val canReceiveRemoteQueue: Boolean = false,
    val canProvisionTarget: Boolean = false,
    val pendingProvisioningControllerName: String? = null,
    val pendingProvisioningConnectionName: String? = null,
    val discoveredTargets: List<NaviampConnectDiscoveredTargetUi> = emptyList(),
    val trustedDevices: List<NaviampConnectTrustedDeviceUi> = emptyList(),
) {
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
    val onRefreshTargets: () -> Unit,
    val onTargetSelected: (NaviampConnectDiscoveredTargetUi) -> Unit,
    val onPairingCodeChanged: (String) -> Unit,
    val onSubmitPairingCode: () -> Unit,
    val onApproveController: () -> Unit,
    val onRejectController: () -> Unit,
    val onTrustedDeviceSelected: (NaviampConnectTrustedDeviceUi) -> Unit,
    val onRemotePrevious: () -> Unit,
    val onRemotePlayPause: () -> Unit,
    val onRemoteNext: () -> Unit,
    val onRemoteHandoffQueue: () -> Unit,
    val onReceiveRemoteQueue: () -> Unit,
    val onProvisionTarget: () -> Unit,
    val onApproveProvisioning: () -> Unit,
    val onRejectProvisioning: () -> Unit,
    val remoteNowPlayingActions: NaviampNowPlayingActions,
)
