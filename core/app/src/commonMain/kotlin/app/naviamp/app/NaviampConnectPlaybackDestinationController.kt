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
    initialDestination: NaviampConnectPlaybackDestination = NaviampConnectPlaybackDestination.Local,
) {
    private val mutableState = MutableStateFlow(initialDestination)

    val state: StateFlow<NaviampConnectPlaybackDestination> = mutableState.asStateFlow()

    fun select(trust: NaviampConnectTrustRecord) {
        require(trust.peerDevice.canActAs(NaviampConnectDeviceRole.Target)) {
            "The selected device cannot act as a playback target."
        }
        mutableState.value = NaviampConnectPlaybackDestination.Remote(
            device = trust.toSelectedPlaybackDevice(),
            status = NaviampConnectRemoteOutputStatus.Armed,
        )
    }

    fun connecting(trustedDeviceId: String): Boolean = updateRemote(trustedDeviceId) { remote ->
        remote.copy(status = NaviampConnectRemoteOutputStatus.Connecting)
    }

    fun connected(device: NaviampConnectDevice): Boolean {
        if (!device.canActAs(NaviampConnectDeviceRole.Target)) return false
        return updateRemoteByDeviceId(device.deviceId) { remote ->
            remote.copy(
                device = remote.device.copy(displayName = device.displayName),
                status = NaviampConnectRemoteOutputStatus.Connected,
            )
        }
    }

    fun activatePlaybackAuthority(trustedDeviceId: String): Boolean {
        val current = mutableState.value as? NaviampConnectPlaybackDestination.Remote ?: return false
        if (current.device.trustedDeviceId != trustedDeviceId ||
            current.status != NaviampConnectRemoteOutputStatus.Connected
        ) {
            return false
        }
        mutableState.value = current.copy(playbackAuthorityActive = true)
        return true
    }

    fun reconnecting(): Boolean = updateRemote { remote ->
        remote.copy(status = NaviampConnectRemoteOutputStatus.Reconnecting)
    }

    fun unavailable(): Boolean = updateRemote { remote ->
        remote.copy(status = NaviampConnectRemoteOutputStatus.Unavailable)
    }

    fun incompatible(): Boolean = updateRemote { remote ->
        remote.copy(status = NaviampConnectRemoteOutputStatus.Incompatible)
    }

    fun selectLocal() {
        mutableState.value = NaviampConnectPlaybackDestination.Local
    }

    fun selectedTrustedDeviceId(): String? =
        (mutableState.value as? NaviampConnectPlaybackDestination.Remote)?.device?.trustedDeviceId

    fun isConnectedRemote(): Boolean =
        (mutableState.value as? NaviampConnectPlaybackDestination.Remote)?.status ==
            NaviampConnectRemoteOutputStatus.Connected

    fun hasRemotePlaybackAuthority(): Boolean =
        (mutableState.value as? NaviampConnectPlaybackDestination.Remote)?.let { remote ->
            remote.status == NaviampConnectRemoteOutputStatus.Connected && remote.playbackAuthorityActive
        } == true

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
