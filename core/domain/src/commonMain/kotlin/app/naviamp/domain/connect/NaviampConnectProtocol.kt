package app.naviamp.domain.connect

import app.naviamp.domain.playback.PlaybackProfile
import app.naviamp.domain.playback.PlaybackProfileTargetType
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val NaviampConnectCurrentProtocolVersion: Int = 1
const val NaviampConnectMinimumProtocolVersion: Int = 1
const val NaviampConnectServiceType: String = "_naviamp-connect._tcp"

@Serializable
data class NaviampConnectProtocolRange(
    val minimum: Int = NaviampConnectMinimumProtocolVersion,
    val maximum: Int = NaviampConnectCurrentProtocolVersion,
) {
    init {
        require(minimum > 0) { "The minimum protocol version must be positive." }
        require(maximum >= minimum) { "The maximum protocol version must not precede the minimum." }
    }
}

fun negotiateNaviampConnectProtocol(
    local: NaviampConnectProtocolRange,
    remote: NaviampConnectProtocolRange,
): Int? {
    val minimum = maxOf(local.minimum, remote.minimum)
    val maximum = minOf(local.maximum, remote.maximum)
    return maximum.takeIf { it >= minimum }
}

@Serializable
enum class NaviampConnectDeviceRole {
    Controller,
    Target,
}

/**
 * Stable capabilities of a Naviamp installation, independent of the role it takes in one session.
 *
 * [NaviampConnectDeviceRole] remains part of the session transcript because command direction and
 * key derivation require an unambiguous controller and target. It must not be used as a permanent
 * classification of a phone or Desktop installation.
 */
@Serializable
enum class NaviampConnectDeviceCapability {
    ControlPlayback,
    PlaybackTarget,
}

fun NaviampConnectDeviceRole.requiredDeviceCapability(): NaviampConnectDeviceCapability = when (this) {
    NaviampConnectDeviceRole.Controller -> NaviampConnectDeviceCapability.ControlPlayback
    NaviampConnectDeviceRole.Target -> NaviampConnectDeviceCapability.PlaybackTarget
}

@Serializable
enum class NaviampConnectCapability {
    TransportControls,
    Seeking,
    Favorites,
    Repeat,
    Shuffle,
    QueueRead,
    QueueSelect,
    QueueEdit,
    QueueReorder,
    QueueClear,
    CatalogPlayback,
    InternetRadio,
    DisplayControl,
    QueueHandoff,
    ConnectionProvisioning,
}

@Serializable
data class NaviampConnectDevice(
    val deviceId: String,
    val displayName: String,
    /** The role this device has in the current pairing or authenticated session. */
    val role: NaviampConnectDeviceRole,
    val deviceCapabilities: Set<NaviampConnectDeviceCapability> = setOf(role.requiredDeviceCapability()),
) {
    init {
        require(deviceId.isNotBlank()) { "A Connect device ID is required." }
        require(displayName.isNotBlank()) { "A Connect display name is required." }
        require(role.requiredDeviceCapability() in deviceCapabilities) {
            "A Connect device must support its active session role."
        }
    }

    fun canActAs(role: NaviampConnectDeviceRole): Boolean =
        role.requiredDeviceCapability() in deviceCapabilities
}

/** Public, non-secret half of a durable Connect device identity. */
@Serializable
data class NaviampConnectPublicIdentity(
    val deviceId: String,
    val identityFingerprint: String,
    val publicKeyBase64: String,
) {
    init {
        require(deviceId.isNotBlank()) { "A public identity requires a device ID." }
        require(identityFingerprint.isNotBlank()) { "A public identity requires a fingerprint." }
        require(publicKeyBase64.isNotBlank()) { "A public identity requires a public key." }
    }
}

/**
 * The complete metadata permitted in unauthenticated DNS-SD discovery.
 *
 * Provider identity, account details, library names, credentials, and media URLs deliberately have
 * no representation here.
 */
@Serializable
data class NaviampConnectAdvertisement(
    val instanceId: String,
    val displayName: String,
    val protocolRange: NaviampConnectProtocolRange,
    val deviceCapabilities: Set<NaviampConnectDeviceCapability> =
        setOf(NaviampConnectDeviceCapability.PlaybackTarget),
    val capabilities: Set<NaviampConnectCapability>,
    val port: Int,
    val identityFingerprint: String,
    val expiresAtEpochMillis: Long,
) {
    init {
        require(instanceId.isNotBlank()) { "A discovery instance ID is required." }
        require(displayName.isNotBlank()) { "A discovery display name is required." }
        require(port in 1..65_535) { "A discovery port must be valid." }
        require(identityFingerprint.isNotBlank()) { "An identity fingerprint is required." }
        require(NaviampConnectDeviceCapability.PlaybackTarget in deviceCapabilities) {
            "A Connect target advertisement requires playback-target capability."
        }
    }
}

/** Canonical provider identity used to decide whether a queue may be handed to a target. */
@Serializable
data class NaviampConnectSourceIdentity(
    val providerId: String,
    val canonicalServerOrigin: String,
    val accountIdentity: String,
    val libraryIds: List<String> = emptyList(),
) {
    fun normalized(): NaviampConnectSourceIdentity = copy(
        providerId = providerId.trim().lowercase(),
        canonicalServerOrigin = canonicalServerOrigin.trim().trimEnd('/').lowercase(),
        accountIdentity = accountIdentity.trim().lowercase(),
        libraryIds = libraryIds.map(String::trim).filter(String::isNotEmpty).distinct().sorted(),
    )

    fun isCompatibleWith(other: NaviampConnectSourceIdentity): Boolean = normalized() == other.normalized()
}

@Serializable
data class NaviampConnectEnvelope(
    val protocolVersion: Int,
    val sessionId: String? = null,
    val sequence: Long,
    val requestId: String? = null,
    val responseToRequestId: String? = null,
    val message: NaviampConnectMessage,
) {
    init {
        require(protocolVersion > 0) { "The protocol version must be positive." }
        require(sequence >= 0) { "The message sequence must not be negative." }
    }
}

@Serializable
sealed interface NaviampConnectMessage

@Serializable
@SerialName("hello")
data class NaviampConnectHello(
    val device: NaviampConnectDevice,
    val identity: NaviampConnectPublicIdentity,
    val protocolRange: NaviampConnectProtocolRange,
    val capabilities: Set<NaviampConnectCapability>,
    val trustedDeviceId: String? = null,
) : NaviampConnectMessage {
    init {
        require(identity.deviceId == device.deviceId) { "The hello identity must belong to the device." }
    }
}

/** Requests a fresh encrypted session using credentials established by an earlier approved pairing. */
@Serializable
@SerialName("resume_hello")
data class NaviampConnectResumeHello(
    val device: NaviampConnectDevice,
    val identity: NaviampConnectPublicIdentity,
    val protocolRange: NaviampConnectProtocolRange,
    /** Fresh controller contribution that prevents replay of an earlier target resume offer. */
    val controllerNonce: String,
) : NaviampConnectMessage {
    init {
        require(identity.deviceId == device.deviceId) { "The resume identity must belong to the device." }
        require(device.role == NaviampConnectDeviceRole.Controller) { "Only a controller may resume a target session." }
        require(controllerNonce.isNotBlank()) { "A resume hello requires a fresh controller nonce." }
        require(controllerNonce.length <= 120) { "The resume controller nonce is too large." }
    }
}

/** Fresh target-selected session binding for a trusted reconnect. */
@Serializable
@SerialName("resume_offer")
data class NaviampConnectResumeOffer(
    val sessionId: String,
    /** Echo of the fresh challenge from the initiating controller. */
    val controllerNonce: String,
    val protocolVersion: Int,
    val target: NaviampConnectDevice,
    val identity: NaviampConnectPublicIdentity,
) : NaviampConnectMessage {
    init {
        require(sessionId.isNotBlank()) { "A resume offer requires a session ID." }
        require(sessionId.encodeToByteArray().size <= 256) { "The resume session ID is too large." }
        require(controllerNonce.isNotBlank()) { "A resume offer requires the controller nonce." }
        require(controllerNonce.length <= 120) { "The resume controller nonce is too large." }
        require(protocolVersion > 0) { "A resume offer requires a protocol version." }
        require(target.role == NaviampConnectDeviceRole.Target) { "A resume offer must identify a target." }
        require(identity.deviceId == target.deviceId) { "The resume identity must belong to the target." }
    }
}

@Serializable
@SerialName("pairing_offer")
data class NaviampConnectPairingOffer(
    val pairingSessionId: String,
    val target: NaviampConnectDevice,
    val identity: NaviampConnectPublicIdentity,
) : NaviampConnectMessage {
    init {
        require(pairingSessionId.isNotBlank()) { "A pairing offer requires a session ID." }
        require(target.role == NaviampConnectDeviceRole.Target) { "A pairing offer must identify a target." }
        require(identity.deviceId == target.deviceId) { "The pairing identity must belong to the target." }
    }
}

@Serializable
@SerialName("welcome")
data class NaviampConnectWelcome(
    val sessionId: String,
    val protocolVersion: Int,
    val target: NaviampConnectDevice,
    val capabilities: Set<NaviampConnectCapability>,
    val snapshot: NaviampConnectTargetSnapshot,
    /** This code-authenticated session may perform initial setup of an empty target. */
    val initialSetupAllowed: Boolean = false,
) : NaviampConnectMessage

/** An opaque PAKE exchange payload. A short code is never placed in a wire message. */
@Serializable
@SerialName("pairing_handshake")
data class NaviampConnectPairingHandshake(
    val pairingSessionId: String,
    val step: Int,
    val payloadBase64: String,
) : NaviampConnectMessage

/** Sent only inside the PAKE-authenticated channel. */
@Serializable
@SerialName("pairing_identity_proof")
data class NaviampConnectPairingIdentityProof(
    val identity: NaviampConnectPublicIdentity,
    val signatureBase64: String,
) : NaviampConnectMessage {
    init {
        require(signatureBase64.isNotBlank()) { "An identity proof requires a signature." }
    }
}

/** Final encrypted acknowledgement that the peer's durable identity proof was accepted. */
@Serializable
@SerialName("pairing_confirmation")
data class NaviampConnectPairingConfirmation(
    val verifiedIdentityFingerprint: String,
) : NaviampConnectMessage {
    init {
        require(verifiedIdentityFingerprint.isNotBlank()) { "A pairing confirmation requires a fingerprint." }
    }
}

@Serializable
@SerialName("pairing_complete")
data class NaviampConnectPairingComplete(
    val trustedDeviceId: String,
    val target: NaviampConnectDevice,
) : NaviampConnectMessage

@Serializable
@SerialName("command")
data class NaviampConnectCommandRequest(
    val command: NaviampConnectCommand,
    val expectedRevision: Long? = null,
) : NaviampConnectMessage

@Serializable
@SerialName("acknowledgement")
data class NaviampConnectAcknowledgement(
    val revision: Long,
) : NaviampConnectMessage

@Serializable
@SerialName("snapshot")
data class NaviampConnectSnapshotMessage(
    val snapshot: NaviampConnectTargetSnapshot,
) : NaviampConnectMessage

/** Sent by a target immediately before a newly authenticated controller takes ownership. */
@Serializable
@SerialName("session_replaced")
data object NaviampConnectSessionReplaced : NaviampConnectMessage

@Serializable
@SerialName("error")
data class NaviampConnectErrorMessage(
    val code: NaviampConnectErrorCode,
    val message: String,
    val retryable: Boolean = false,
    val currentRevision: Long? = null,
) : NaviampConnectMessage

@Serializable
@SerialName("ping")
data class NaviampConnectPing(val sentAtEpochMillis: Long) : NaviampConnectMessage

@Serializable
@SerialName("pong")
data class NaviampConnectPong(val sentAtEpochMillis: Long) : NaviampConnectMessage

@Serializable
enum class NaviampConnectErrorCode {
    IncompatibleProtocol,
    AuthenticationRequired,
    PairingExpired,
    PairingRejected,
    RateLimited,
    ReplayRejected,
    InvalidRequest,
    UnsupportedCapability,
    RevisionConflict,
    SourceMismatch,
    MediaUnavailable,
    TargetUnavailable,
    InternalFailure,
}

@Serializable
sealed interface NaviampConnectCommand

@Serializable
@SerialName("play")
data object NaviampConnectPlay : NaviampConnectCommand

@Serializable
@SerialName("pause")
data object NaviampConnectPause : NaviampConnectCommand

@Serializable
@SerialName("toggle_play_pause")
data object NaviampConnectTogglePlayPause : NaviampConnectCommand

@Serializable
@SerialName("previous")
data object NaviampConnectPrevious : NaviampConnectCommand

@Serializable
@SerialName("next")
data object NaviampConnectNext : NaviampConnectCommand

@Serializable
@SerialName("stop")
data object NaviampConnectStop : NaviampConnectCommand

@Serializable
@SerialName("seek")
data class NaviampConnectSeek(val positionMillis: Long) : NaviampConnectCommand {
    init {
        require(positionMillis >= 0) { "A seek position must not be negative." }
    }
}

@Serializable
@SerialName("set_favorite")
data class NaviampConnectSetFavorite(
    val mediaId: String,
    val favorite: Boolean,
) : NaviampConnectCommand

@Serializable
@SerialName("set_repeat")
data class NaviampConnectSetRepeat(val mode: NaviampConnectRepeatMode) : NaviampConnectCommand

@Serializable
@SerialName("set_shuffle")
data class NaviampConnectSetShuffle(val enabled: Boolean) : NaviampConnectCommand

@Serializable
@SerialName("select_queue_occurrence")
data class NaviampConnectSelectQueueOccurrence(val occurrenceId: String) : NaviampConnectCommand

@Serializable
@SerialName("move_queue_occurrence")
data class NaviampConnectMoveQueueOccurrence(
    val occurrenceId: String,
    val beforeOccurrenceId: String? = null,
) : NaviampConnectCommand

@Serializable
@SerialName("remove_queue_occurrence")
data class NaviampConnectRemoveQueueOccurrence(val occurrenceId: String) : NaviampConnectCommand

@Serializable
@SerialName("clear_up_next")
data object NaviampConnectClearUpNext : NaviampConnectCommand

@Serializable
@SerialName("request_snapshot")
data object NaviampConnectRequestSnapshot : NaviampConnectCommand

@Serializable
@SerialName("show_surface")
data class NaviampConnectShowSurface(val surface: NaviampConnectTargetSurface) : NaviampConnectCommand

@Serializable
@SerialName("start_media")
data class NaviampConnectStartMedia(
    val mediaType: NaviampConnectMediaType,
    val mediaId: String,
    val startRadio: Boolean = false,
    val shuffle: Boolean = false,
    val sourceIdentity: NaviampConnectSourceIdentity,
) : NaviampConnectCommand

@Serializable
@SerialName("queue_media")
data class NaviampConnectQueueMedia(
    val mediaType: NaviampConnectMediaType,
    val mediaId: String,
    val placement: NaviampConnectQueuePlacement,
    val sourceIdentity: NaviampConnectSourceIdentity,
) : NaviampConnectCommand {
    init {
        require(mediaType != NaviampConnectMediaType.InternetRadioStation) {
            "Internet radio stations cannot be appended to a track queue."
        }
        require(mediaId.isNotBlank()) { "Queued media requires an ID." }
    }
}

@Serializable
enum class NaviampConnectQueuePlacement {
    AddToQueue,
    PlayNext,
    PlayNextTrack,
}

@Serializable
data class NaviampConnectProvisioningEndpoint(
    val url: String,
    val label: String = "",
)

@Serializable
data class NaviampConnectProvisioningHeader(
    val name: String,
    val value: String,
    val secret: Boolean = false,
)

/** Sensitive connection data permitted only inside the authenticated encrypted session. */
@Serializable
data class NaviampConnectProvisioningProfile(
    val providerId: String,
    val displayName: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val skipTlsVerification: Boolean = false,
    val secondaryUrls: List<NaviampConnectProvisioningEndpoint> = emptyList(),
    val customHeaders: List<NaviampConnectProvisioningHeader> = emptyList(),
    val selectedLibraryIds: List<String> = emptyList(),
) {
    init {
        require(providerId.isNotBlank()) { "A provisioning provider ID is required." }
        require(serverUrl.isNotBlank()) { "A provisioning server URL is required." }
        require(username.isNotBlank()) { "A provisioning account is required." }
        require(password.isNotBlank()) { "A provisioning credential is required." }
    }

    override fun toString(): String =
        "NaviampConnectProvisioningProfile(providerId=$providerId, displayName=$displayName, " +
            "serverUrl=$serverUrl, username=$username, password=<redacted>, " +
            "skipTlsVerification=$skipTlsVerification, secondaryUrls=${secondaryUrls.size}, " +
            "customHeaders=${customHeaders.size}, selectedLibraryIds=$selectedLibraryIds)"
}

@Serializable
data class NaviampConnectPortableSettings(
    val interfaceSettings: InterfaceSettings,
    val playbackSettings: PlaybackSettings,
)

@Serializable
@SerialName("offer_connection_provisioning")
data class NaviampConnectOfferConnectionProvisioning(
    val profile: NaviampConnectProvisioningProfile,
    val portableSettings: NaviampConnectPortableSettings? = null,
    val initialSetup: Boolean = false,
    val setupId: String? = null,
) : NaviampConnectCommand

@Serializable
@SerialName("connection_provisioning_result")
data class NaviampConnectConnectionProvisioningResult(
    val succeeded: Boolean,
    val message: String,
    val setupId: String? = null,
) : NaviampConnectMessage

@Serializable
@SerialName("handoff_queue")
data class NaviampConnectHandoffQueue(
    val sourceIdentity: NaviampConnectSourceIdentity,
    val queue: NaviampConnectQueueSnapshot,
    val positionMillis: Long,
    val repeatMode: NaviampConnectRepeatMode,
    val shuffled: Boolean,
    val playing: Boolean = true,
    val playbackProfileIntent: String? = null,
) : NaviampConnectCommand {
    init {
        require(positionMillis >= 0) { "A handoff position must not be negative." }
    }
}

@Serializable
enum class NaviampConnectMediaType {
    Track,
    Album,
    Artist,
    Playlist,
    InternetRadioStation,
}

@Serializable
enum class NaviampConnectTargetSurface {
    NowPlaying,
    Lyrics,
    Queue,
}

fun NaviampConnectCommand.requiredCapability(): NaviampConnectCapability? = when (this) {
    NaviampConnectPlay,
    NaviampConnectPause,
    NaviampConnectTogglePlayPause,
    NaviampConnectPrevious,
    NaviampConnectNext,
    NaviampConnectStop,
    -> NaviampConnectCapability.TransportControls
    is NaviampConnectSeek -> NaviampConnectCapability.Seeking
    is NaviampConnectSetFavorite -> NaviampConnectCapability.Favorites
    is NaviampConnectSetRepeat -> NaviampConnectCapability.Repeat
    is NaviampConnectSetShuffle -> NaviampConnectCapability.Shuffle
    is NaviampConnectSelectQueueOccurrence -> NaviampConnectCapability.QueueSelect
    is NaviampConnectMoveQueueOccurrence -> NaviampConnectCapability.QueueReorder
    is NaviampConnectRemoveQueueOccurrence -> NaviampConnectCapability.QueueEdit
    NaviampConnectClearUpNext -> NaviampConnectCapability.QueueClear
    NaviampConnectRequestSnapshot -> NaviampConnectCapability.QueueRead
    is NaviampConnectShowSurface -> NaviampConnectCapability.DisplayControl
    is NaviampConnectStartMedia -> when (mediaType) {
        NaviampConnectMediaType.InternetRadioStation -> NaviampConnectCapability.InternetRadio
        else -> NaviampConnectCapability.CatalogPlayback
    }
    is NaviampConnectQueueMedia -> NaviampConnectCapability.QueueEdit
    is NaviampConnectOfferConnectionProvisioning -> NaviampConnectCapability.ConnectionProvisioning
    is NaviampConnectHandoffQueue -> NaviampConnectCapability.QueueHandoff
}

@Serializable
data class NaviampConnectTargetSnapshot(
    val revision: Long,
    val target: NaviampConnectDevice,
    val capabilities: Set<NaviampConnectCapability>,
    val sourceIdentity: NaviampConnectSourceIdentity? = null,
    val playback: NaviampConnectPlaybackSnapshot = NaviampConnectPlaybackSnapshot(),
    val queue: NaviampConnectQueueSnapshot = NaviampConnectQueueSnapshot(),
) {
    init {
        require(revision >= 0) { "A target revision must not be negative." }
        require(target.role == NaviampConnectDeviceRole.Target) { "A target snapshot requires a target device." }
    }
}

@Serializable
data class NaviampConnectPlaybackSnapshot(
    val state: NaviampConnectPlaybackState = NaviampConnectPlaybackState.Idle,
    val currentOccurrenceId: String? = null,
    val positionMillis: Long = 0,
    val durationMillis: Long? = null,
    val repeatMode: NaviampConnectRepeatMode = NaviampConnectRepeatMode.Off,
    val shuffled: Boolean = false,
    val volumePercent: Int? = null,
    val visibleSurface: NaviampConnectTargetSurface = NaviampConnectTargetSurface.NowPlaying,
) {
    init {
        require(positionMillis >= 0) { "Playback position must not be negative." }
        require(durationMillis == null || durationMillis >= 0) { "Playback duration must not be negative." }
        require(volumePercent == null || volumePercent in 0..100) { "Volume must be between 0 and 100." }
    }
}

@Serializable
enum class NaviampConnectPlaybackState {
    Idle,
    Buffering,
    Playing,
    Paused,
    Failed,
}

@Serializable
enum class NaviampConnectRepeatMode {
    Off,
    All,
    One,
}

@Serializable
data class NaviampConnectQueueSnapshot(
    val occurrences: List<NaviampConnectQueueOccurrence> = emptyList(),
    val currentIndex: Int = -1,
    val playNextCount: Int = 0,
    val groups: List<NaviampConnectQueueGroup> = emptyList(),
) {
    init {
        require(
            (occurrences.isEmpty() && currentIndex == -1) || currentIndex in occurrences.indices,
        ) { "A queue current index must identify an occurrence, or be -1 for an empty queue." }
        require(occurrences.map { it.occurrenceId }.distinct().size == occurrences.size) {
            "Queue occurrence IDs must be unique."
        }
        val upcomingCount = (occurrences.size - currentIndex - 1).coerceAtLeast(0)
        require(playNextCount in 0..upcomingCount) { "Play Next count exceeds the upcoming queue." }
        require(groups.all { it.startIndex >= 0 && it.endIndexExclusive in 1..occurrences.size && it.startIndex < it.endIndexExclusive }) {
            "Queue group bounds must identify non-empty occurrence ranges."
        }
    }
}

@Serializable
data class NaviampConnectQueueOccurrence(
    val occurrenceId: String,
    val mediaId: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val durationMillis: Long? = null,
    val artworkId: String? = null,
    val favorite: Boolean = false,
) {
    init {
        require(occurrenceId.isNotBlank()) { "A queue occurrence ID is required." }
        require(mediaId.isNotBlank()) { "A provider media ID is required." }
    }
}

@Serializable
data class NaviampConnectQueueGroup(
    val groupId: String,
    val label: String? = null,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val targetType: PlaybackProfileTargetType,
    val targetId: String,
    val playbackProfile: PlaybackProfile = PlaybackProfile(),
) {
    init {
        require(groupId.isNotBlank()) { "A queue group ID is required." }
        require(targetId.isNotBlank()) { "A queue group target ID is required." }
    }
}

object NaviampConnectWireCodec {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun encode(envelope: NaviampConnectEnvelope): String = json.encodeToString(envelope)

    fun decode(payload: String): NaviampConnectEnvelope = json.decodeFromString(payload)
}
