package app.naviamp.presentation

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Returns this installation's provider device identifier, creating it on first use.
 *
 * Media servers such as Jellyfin bind each access token to the client's device identifier and
 * revoke a user's earlier tokens for that identifier when the user signs in again. A shared
 * constant per platform therefore made a phone and a TV signed into the same account sign each
 * other out. The identifier is random per installation, device-local, and never exported or
 * synced; the host supplies a value store that is excluded from device backup where the OS would
 * otherwise copy it to another device.
 */
@OptIn(ExperimentalUuidApi::class)
fun naviampProviderDeviceId(
    values: NaviampCoreSettingsValueStore,
    platformPrefix: String,
    newOpaqueId: () -> String = { Uuid.random().toHexString() },
): String {
    require(platformPrefix.isValidProviderDeviceIdPart()) { "Invalid provider device identifier prefix." }
    values.read(KeyProviderDeviceId)
        ?.takeIf { it.startsWith("$platformPrefix-") && it.isValidProviderDeviceIdPart() }
        ?.let { return it }
    val opaque = newOpaqueId()
    require(opaque.isNotEmpty() && opaque.isValidProviderDeviceIdPart()) { "Invalid provider device identifier." }
    return "$platformPrefix-$opaque".also { values.write(KeyProviderDeviceId, it) }
}

private fun String.isValidProviderDeviceIdPart(): Boolean =
    isNotEmpty() && length <= MaxProviderDeviceIdLength && all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' || it == '_' }

private const val MaxProviderDeviceIdLength = 128

/** Device-local; deliberately absent from settings export, import, and sync. */
internal const val KeyProviderDeviceId = "naviamp.device.providerDeviceId"
