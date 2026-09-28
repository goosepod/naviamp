package app.naviamp.app

import app.naviamp.domain.connect.NaviampConnectDevice
import app.naviamp.domain.connect.NaviampConnectDeviceRole
import app.naviamp.domain.connect.NaviampConnectTrustRecord
import app.naviamp.domain.connect.visibleDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NaviampConnectRemoteOutputStatus {
    Armed,
    Connecting,
    Connected,
    Reconnecting,
    Unavailable,
    Incompatible,
}

data class NaviampConnectSelectedPlaybackDevice(
    val trustedDeviceId: String,
    val deviceId: String,
    val displayName: String,
) {
    init {
        require(trustedDeviceId.isNotBlank()) { "A trusted device ID is required." }
        require(deviceId.isNotBlank()) { "A playback device ID is required." }
        require(displayName.isNotBlank()) { "A playback device name is required." }
    }
}

sealed interface NaviampConnectPlaybackDestination {
    data object Local : NaviampConnectPlaybackDestination

    data class Remote(
        val device: NaviampConnectSelectedPlaybackDevice,
        val status: NaviampConnectRemoteOutputStatus,
        /** True only after this controller has successfully established the target queue. */
        val playbackAuthorityActive: Boolean = false,
    ) : NaviampConnectPlaybackDestination
}

/**
 * Shared owner of the user's playback-output intent.
 *
 * A remote selection is deliberately independent from the socket session: selecting a device arms
 * remote output without touching either queue, and transient connection loss preserves that intent
 * for automatic recovery. Only an explicit local selection or session displacement returns output
 * ownership to this device.
 */
class NaviampConnectPlaybackDestinationController(
    val outputSelection: NaviampPlaybackOutputSelectionController = NaviampPlaybackOutputSelectionController(),
) {
    private val mutableState = MutableStateFlow<NaviampConnectPlaybackDestination>(NaviampConnectPlaybackDestination.Local)
    private var selectionId: Long? = null

    val state: StateFlow<NaviampConnectPlaybackDestination> = mutableState.asStateFlow()

    fun select(trust: NaviampConnectTrustRecord) {
        require(trust.peerDevice.canActAs(NaviampConnectDeviceRole.Target)) {
            "The selected device cannot act as a playback target."
        }
        selectionId = outputSelection.select(NaviampRemoteOutputTarget(
            kind = NaviampRemoteOutputKind.Connect,
            id = trust.trustedDeviceId,
            displayName = trust.visibleDisplayName(),
        ))
        mutableState.value = NaviampConnectPlaybackDestination.Remote(
            device = trust.toSelectedPlaybackDevice(),
            status = NaviampConnectRemoteOutputStatus.Armed,
        )
    }

    fun connecting(trustedDeviceId: String): Boolean {
        if (currentSelectionId() == null) return false
        return updateRemote(trustedDeviceId) { remote ->
            if (selectionId?.let(outputSelection::connecting) != true) remote
            else remote.copy(status = NaviampConnectRemoteOutputStatus.Connecting, playbackAuthorityActive = false)
        }
    }

    fun connected(device: NaviampConnectDevice): Boolean {
        if (!device.canActAs(NaviampConnectDeviceRole.Target)) return false
        if (currentSelectionId() == null) return false
        return updateRemoteByDeviceId(device.deviceId) { remote ->
            if (selectionId?.let { outputSelection.connected(it, device.displayName) } != true) remote
            else remote.copy(
                device = remote.device.copy(displayName = device.displayName),
                status = NaviampConnectRemoteOutputStatus.Connected,
            )
        }
    }

    fun activatePlaybackAuthority(trustedDeviceId: String): Boolean {
        val current = snapshot() as? NaviampConnectPlaybackDestination.Remote ?: return false
        if (current.device.trustedDeviceId != trustedDeviceId ||
            current.status != NaviampConnectRemoteOutputStatus.Connected
        ) {
            return false
        }
        if (selectionId?.let(outputSelection::activatePlaybackAuthority) != true) return false
        mutableState.value = current.copy(playbackAuthorityActive = true)
        return true
    }

    fun reconnecting(): Boolean {
        if (currentSelectionId() == null) return false
        return updateRemote { remote ->
            outputSelection.reconnecting(requireNotNull(selectionId))
            remote.copy(status = NaviampConnectRemoteOutputStatus.Reconnecting, playbackAuthorityActive = false)
        }
    }

    fun unavailable(): Boolean {
        if (currentSelectionId() == null) return false
        return updateRemote { remote ->
            outputSelection.unavailable(requireNotNull(selectionId))
            remote.copy(status = NaviampConnectRemoteOutputStatus.Unavailable, playbackAuthorityActive = false)
        }
    }

    fun incompatible(): Boolean {
        if (currentSelectionId() == null) return false
        return updateRemote { remote ->
            outputSelection.incompatible(requireNotNull(selectionId))
            remote.copy(status = NaviampConnectRemoteOutputStatus.Incompatible, playbackAuthorityActive = false)
        }
    }

    fun selectLocal() {
        if (currentSelectionId() != null) outputSelection.selectLocal()
        selectionId = null
        mutableState.value = NaviampConnectPlaybackDestination.Local
    }

    fun selectedTrustedDeviceId(): String? =
        (snapshot() as? NaviampConnectPlaybackDestination.Remote)?.device?.trustedDeviceId

    fun isConnectedRemote(): Boolean =
        (snapshot() as? NaviampConnectPlaybackDestination.Remote)?.status ==
            NaviampConnectRemoteOutputStatus.Connected

    fun hasRemotePlaybackAuthority(): Boolean =
        (snapshot() as? NaviampConnectPlaybackDestination.Remote)?.let { remote ->
            remote.status == NaviampConnectRemoteOutputStatus.Connected &&
                remote.playbackAuthorityActive && outputSelection.hasRemotePlaybackAuthority() &&
                (outputSelection.state.value as? NaviampPlaybackOutputSelection.Remote)
                    ?.target?.kind == NaviampRemoteOutputKind.Connect
        } == true

    fun snapshot(): NaviampConnectPlaybackDestination =
        mutableState.value.takeIf { currentSelectionId() != null } ?: NaviampConnectPlaybackDestination.Local

    private fun currentSelectionId(): Long? = selectionId?.takeIf { id ->
        (outputSelection.state.value as? NaviampPlaybackOutputSelection.Remote)?.let { selection ->
            selection.selectionId == id && selection.target.kind == NaviampRemoteOutputKind.Connect
        } == true
    }

    private fun updateRemote(
        transform: (NaviampConnectPlaybackDestination.Remote) -> NaviampConnectPlaybackDestination.Remote,
    ): Boolean {
        val current = mutableState.value as? NaviampConnectPlaybackDestination.Remote ?: return false
        mutableState.value = transform(current)
        return true
    }

    private fun updateRemote(
        trustedDeviceId: String,
        transform: (NaviampConnectPlaybackDestination.Remote) -> NaviampConnectPlaybackDestination.Remote,
    ): Boolean {
        val current = mutableState.value as? NaviampConnectPlaybackDestination.Remote ?: return false
        if (current.device.trustedDeviceId != trustedDeviceId) return false
        mutableState.value = transform(current)
        return true
    }

    private fun updateRemoteByDeviceId(
        deviceId: String,
        transform: (NaviampConnectPlaybackDestination.Remote) -> NaviampConnectPlaybackDestination.Remote,
    ): Boolean {
        val current = mutableState.value as? NaviampConnectPlaybackDestination.Remote ?: return false
        if (current.device.deviceId != deviceId) return false
        mutableState.value = transform(current)
        return true
    }
}

private fun NaviampConnectTrustRecord.toSelectedPlaybackDevice() = NaviampConnectSelectedPlaybackDevice(
    trustedDeviceId = trustedDeviceId,
    deviceId = peerDevice.deviceId,
    displayName = visibleDisplayName(),
)
