package app.naviamp.domain.settings

import app.naviamp.domain.network.NetworkMetering
import app.naviamp.domain.network.NetworkMeteringSource
import app.naviamp.domain.playback.MaxAudioPrefetchDepth
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

fun CacheSettings.audioPrefetchDepthFor(metering: NetworkMetering): Int =
    when (metering) {
        NetworkMetering.Unmetered -> unmeteredAudioPrefetchDepth
        NetworkMetering.Metered, NetworkMetering.Unknown -> meteredAudioPrefetchDepth
    }.coerceIn(0, MaxAudioPrefetchDepth)

/**
 * A host that cannot observe metering at all (Desktop) is on Wi-Fi or Ethernet in practice, so it
 * uses that depth. A host that observes metering but cannot classify the current network might be
 * on a billed connection, so its Unknown keeps the conservative mobile-data depth.
 */
fun CacheSettings.audioPrefetchDepthFor(meteringSource: NetworkMeteringSource?): Int =
    audioPrefetchDepthFor(meteringSource?.current() ?: NetworkMetering.Unmetered)

/** The portable part of [CacheSettings]; storage locations and budgets stay device-local. */
@Serializable
data class SettingsSyncAudioPrefetch(
    val unmeteredDepth: Int = DefaultAudioPrefetchDepth,
    val meteredDepth: Int = DefaultAudioPrefetchDepth,
) {
    fun normalized(): SettingsSyncAudioPrefetch =
        copy(
            unmeteredDepth = unmeteredDepth.coerceIn(0, MaxAudioPrefetchDepth),
            meteredDepth = meteredDepth.coerceIn(0, MaxAudioPrefetchDepth),
        )
}

fun CacheSettings.toSettingsSyncAudioPrefetch(): SettingsSyncAudioPrefetch =
    SettingsSyncAudioPrefetch(
        unmeteredDepth = unmeteredAudioPrefetchDepth,
        meteredDepth = meteredAudioPrefetchDepth,
    ).normalized()

fun CacheSettings.withSyncedAudioPrefetch(synced: SettingsSyncAudioPrefetch?): CacheSettings {
    val portable = synced?.normalized() ?: return normalized()
    return copy(
        unmeteredAudioPrefetchDepth = portable.unmeteredDepth,
        meteredAudioPrefetchDepth = portable.meteredDepth,
    ).normalized()
}

/** Settings saved before the per-network split carry one depth that applies to both networks. */
internal object CacheSettingsSerializer :
    JsonTransformingSerializer<CacheSettings>(CacheSettings.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val stored = element as? JsonObject ?: return element
        val legacyDepth = stored[LegacyAudioPrefetchDepthKey] ?: return element
        val migrated = stored.toMutableMap()
        migrated.remove(LegacyAudioPrefetchDepthKey)
        migrated.getOrPut(UnmeteredAudioPrefetchDepthKey) { legacyDepth }
        migrated.getOrPut(MeteredAudioPrefetchDepthKey) { legacyDepth }
        return JsonObject(migrated)
    }
}

private const val LegacyAudioPrefetchDepthKey = "audioPrefetchDepth"
private const val UnmeteredAudioPrefetchDepthKey = "unmeteredAudioPrefetchDepth"
private const val MeteredAudioPrefetchDepthKey = "meteredAudioPrefetchDepth"
