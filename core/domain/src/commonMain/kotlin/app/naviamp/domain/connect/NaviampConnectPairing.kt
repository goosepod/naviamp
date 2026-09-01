package app.naviamp.domain.connect

import kotlinx.serialization.Serializable

@Serializable
data class NaviampConnectTrustRecord(
    val trustedDeviceId: String,
    val peerDevice: NaviampConnectDevice,
    val identityFingerprint: String,
    val publicKeyBase64: String,
    val pairedAtEpochMillis: Long,
    val displayName: String = peerDevice.displayName,
    val lastKnownEndpoint: NaviampConnectTrustedEndpoint? = null,
) {
    init {
        require(trustedDeviceId.isNotBlank()) { "A trusted device ID is required." }
        require(identityFingerprint.isNotBlank()) { "A trusted identity fingerprint is required." }
        require(publicKeyBase64.isNotBlank()) { "A trusted public key is required." }
    }
}

/** Last authenticated network route for a trusted peer; it contains no secret material. */
@Serializable
data class NaviampConnectTrustedEndpoint(
    val addresses: List<String>,
    val advertisement: NaviampConnectAdvertisement,
) {
    init {
        require(addresses.isNotEmpty()) { "A trusted endpoint requires at least one address." }
        require(addresses.none(String::isBlank)) { "Trusted endpoint addresses must not be blank." }
    }
}

sealed interface NaviampConnectTargetPairingState {
    data object Disabled : NaviampConnectTargetPairingState

    data class Advertising(
        val advertisement: NaviampConnectAdvertisement,
        val pairingSessionId: String,
        /** Display-only secret. It must be consumed by a PAKE adapter and never serialized. */
        val displayCode: String,
        val failedAttempts: Int = 0,
    ) : NaviampConnectTargetPairingState

    data class AwaitingApproval(
        val advertising: Advertising,
        val controller: NaviampConnectDevice,
    ) : NaviampConnectTargetPairingState

    data class Handshaking(
        val advertising: Advertising,
        val controller: NaviampConnectDevice,
    ) : NaviampConnectTargetPairingState

    data class Paired(val trust: NaviampConnectTrustRecord) : NaviampConnectTargetPairingState

    data class Failed(
        val code: NaviampConnectErrorCode,
        val retryAtEpochMillis: Long? = null,
    ) : NaviampConnectTargetPairingState
}

class NaviampConnectTargetHandshakeStart internal constructor(
    val state: NaviampConnectTargetPairingState.Handshaking,
    val pairingCode: CharArray,
)

/** Shared target-side pairing policy. Cryptographic operations are supplied by a reviewed adapter. */
class NaviampConnectTargetPairingController(
    private val maximumAttempts: Int = 5,
    private val rateLimitMillis: Long = 30_000L,
) {
    init {
        require(maximumAttempts > 0) { "Pairing must allow at least one attempt." }
        require(rateLimitMillis >= 0) { "The pairing rate limit must not be negative." }
    }

    var state: NaviampConnectTargetPairingState = NaviampConnectTargetPairingState.Disabled
        private set

    fun start(
        advertisement: NaviampConnectAdvertisement,
        pairingSessionId: String,
        displayCode: String,
        nowEpochMillis: Long,
    ): NaviampConnectTargetPairingState {
        require(pairingSessionId.isNotBlank()) { "A pairing session ID is required." }
        require(displayCode.isNotBlank()) { "A display code is required." }
        require(advertisement.expiresAtEpochMillis > nowEpochMillis) { "Pairing must expire in the future." }
        state = NaviampConnectTargetPairingState.Advertising(
            advertisement = advertisement,
            pairingSessionId = pairingSessionId,
            displayCode = displayCode,
        )
        return state
    }

    fun requestApproval(
        controller: NaviampConnectDevice,
        pairingSessionId: String,
        nowEpochMillis: Long,
    ): NaviampConnectTargetPairingState {
        val advertising = when (val current = state) {
            is NaviampConnectTargetPairingState.Advertising -> current
            else -> return state
        }
        if (expireIfNeeded(nowEpochMillis)) return state
        if (controller.role != NaviampConnectDeviceRole.Controller ||
            pairingSessionId != advertising.pairingSessionId
        ) {
            return recordFailedAttempt(advertising, nowEpochMillis)
        }
        state = NaviampConnectTargetPairingState.AwaitingApproval(advertising, controller)
        return state
    }

    /** Moves to handshaking while removing the display code from retained state. */
    fun approve(nowEpochMillis: Long): NaviampConnectTargetHandshakeStart? {
        val current = state as? NaviampConnectTargetPairingState.AwaitingApproval ?: return null
        if (expireIfNeeded(nowEpochMillis)) return null
        val code = current.advertising.displayCode.toCharArray()
        val handshaking = NaviampConnectTargetPairingState.Handshaking(
            current.advertising.copy(displayCode = ""),
            current.controller,
        )
        state = handshaking
        return NaviampConnectTargetHandshakeStart(handshaking, code)
    }

    fun reject(): NaviampConnectTargetPairingState {
        val current = state as? NaviampConnectTargetPairingState.AwaitingApproval ?: return state
        state = current.advertising
        return state
    }

    fun handshakeFailed(nowEpochMillis: Long): NaviampConnectTargetPairingState {
        val current = state as? NaviampConnectTargetPairingState.Handshaking ?: return state
        val attempts = current.advertising.failedAttempts + 1
        state = if (attempts >= maximumAttempts) {
            NaviampConnectTargetPairingState.Failed(
                code = NaviampConnectErrorCode.RateLimited,
                retryAtEpochMillis = nowEpochMillis + rateLimitMillis,
            )
        } else {
            NaviampConnectTargetPairingState.Failed(NaviampConnectErrorCode.AuthenticationRequired)
        }
        return state
    }

    fun complete(
        trust: NaviampConnectTrustRecord,
        nowEpochMillis: Long,
    ): NaviampConnectTargetPairingState {
        val current = state as? NaviampConnectTargetPairingState.Handshaking ?: return state
        if (expireIfNeeded(nowEpochMillis)) return state
        require(trust.peerDevice.deviceId == current.controller.deviceId) {
            "The trust record must belong to the approved controller."
        }
        state = NaviampConnectTargetPairingState.Paired(trust)
        return state
    }

    fun expireIfNeeded(nowEpochMillis: Long): Boolean {
        val advertising = when (val current = state) {
            is NaviampConnectTargetPairingState.Advertising -> current
            is NaviampConnectTargetPairingState.AwaitingApproval -> current.advertising
            is NaviampConnectTargetPairingState.Handshaking -> current.advertising
            else -> return false
        }
        if (nowEpochMillis < advertising.advertisement.expiresAtEpochMillis) return false
        state = NaviampConnectTargetPairingState.Failed(NaviampConnectErrorCode.PairingExpired)
        return true
    }

    fun stop() {
        state = NaviampConnectTargetPairingState.Disabled
    }

    private fun recordFailedAttempt(
        advertising: NaviampConnectTargetPairingState.Advertising,
        nowEpochMillis: Long,
    ): NaviampConnectTargetPairingState {
        val attempts = advertising.failedAttempts + 1
        state = if (attempts >= maximumAttempts) {
            NaviampConnectTargetPairingState.Failed(
                code = NaviampConnectErrorCode.RateLimited,
                retryAtEpochMillis = nowEpochMillis + rateLimitMillis,
            )
        } else {
            advertising.copy(failedAttempts = attempts)
        }
        return state
    }
}

sealed interface NaviampConnectControllerPairingState {
    data object Idle : NaviampConnectControllerPairingState
    data class Discovering(val targets: List<NaviampConnectAdvertisement>) : NaviampConnectControllerPairingState
    data class AwaitingCode(
        val target: NaviampConnectAdvertisement,
        val protocolVersion: Int,
    ) : NaviampConnectControllerPairingState
    data class Handshaking(
        val target: NaviampConnectAdvertisement,
        val protocolVersion: Int,
    ) : NaviampConnectControllerPairingState
    data class Paired(val trust: NaviampConnectTrustRecord) : NaviampConnectControllerPairingState
    data class Failed(
        val code: NaviampConnectErrorCode,
        val target: NaviampConnectAdvertisement? = null,
    ) : NaviampConnectControllerPairingState
}

/** Shared controller-side discovery selection and pairing lifecycle. */
class NaviampConnectControllerPairingController(
    private val localProtocolRange: NaviampConnectProtocolRange = NaviampConnectProtocolRange(),
) {
    var state: NaviampConnectControllerPairingState = NaviampConnectControllerPairingState.Idle
        private set

    fun startDiscovery(): NaviampConnectControllerPairingState {
        state = NaviampConnectControllerPairingState.Discovering(emptyList())
        return state
    }

    fun updateDiscoveredTarget(
        advertisement: NaviampConnectAdvertisement,
        nowEpochMillis: Long,
    ): NaviampConnectControllerPairingState {
        val discovering = state as? NaviampConnectControllerPairingState.Discovering ?: return state
        val current = discovering.targets
            .filter { it.instanceId != advertisement.instanceId && it.expiresAtEpochMillis > nowEpochMillis }
        val updated = if (advertisement.expiresAtEpochMillis > nowEpochMillis) current + advertisement else current
        state = NaviampConnectControllerPairingState.Discovering(updated.sortedBy { it.displayName.lowercase() })
        return state
    }

    fun removeExpiredTargets(nowEpochMillis: Long): NaviampConnectControllerPairingState {
        val discovering = state as? NaviampConnectControllerPairingState.Discovering ?: return state
        state = discovering.copy(targets = discovering.targets.filter { it.expiresAtEpochMillis > nowEpochMillis })
        return state
    }

    fun selectTarget(
        instanceId: String,
        nowEpochMillis: Long,
    ): NaviampConnectControllerPairingState {
        val discovering = state as? NaviampConnectControllerPairingState.Discovering ?: return state
        val target = discovering.targets.firstOrNull { it.instanceId == instanceId }
            ?: return fail(NaviampConnectErrorCode.TargetUnavailable)
        if (target.expiresAtEpochMillis <= nowEpochMillis) {
            return fail(NaviampConnectErrorCode.PairingExpired, target)
        }
        val version = negotiateNaviampConnectProtocol(localProtocolRange, target.protocolRange)
            ?: return fail(NaviampConnectErrorCode.IncompatibleProtocol, target)
        state = NaviampConnectControllerPairingState.AwaitingCode(target, version)
        return state
    }

    /** The code is consumed by [beginHandshake] and is never retained in controller state. */
    fun submitCode(
        code: String,
        beginHandshake: (String) -> Boolean,
    ): NaviampConnectControllerPairingState {
        val awaiting = state as? NaviampConnectControllerPairingState.AwaitingCode ?: return state
        if (code.isBlank() || !beginHandshake(code)) {
            return fail(NaviampConnectErrorCode.AuthenticationRequired, awaiting.target)
        }
        state = NaviampConnectControllerPairingState.Handshaking(awaiting.target, awaiting.protocolVersion)
        return state
    }

    fun complete(trust: NaviampConnectTrustRecord): NaviampConnectControllerPairingState {
        val handshaking = state as? NaviampConnectControllerPairingState.Handshaking ?: return state
        require(trust.peerDevice.role == NaviampConnectDeviceRole.Target) {
            "A controller trust record must identify a target."
        }
        require(trust.identityFingerprint == handshaking.target.identityFingerprint) {
            "The paired target fingerprint changed during pairing."
        }
        state = NaviampConnectControllerPairingState.Paired(trust)
        return state
    }

    fun fail(
        code: NaviampConnectErrorCode,
        target: NaviampConnectAdvertisement? = null,
    ): NaviampConnectControllerPairingState {
        state = NaviampConnectControllerPairingState.Failed(code, target)
        return state
    }

    fun reset() {
        state = NaviampConnectControllerPairingState.Idle
    }
}
