package app.naviamp.domain.settings

import app.naviamp.domain.cache.ProviderMediaSourceConnection
import app.naviamp.domain.cache.ProviderMediaSourceRepository
import app.naviamp.domain.network.NetworkMetering
import app.naviamp.domain.network.NetworkMeteringSource
import app.naviamp.domain.radio.RadioDjPreset
import app.naviamp.domain.radio.RadioDjPresetRepository
import app.naviamp.domain.source.MediaSourceIdentity
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioPrefetchDepthSettingsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun depthFollowsTheNetworkAndUnknownUsesTheMeteredDepth() {
        val settings = CacheSettings(unmeteredAudioPrefetchDepth = 15, meteredAudioPrefetchDepth = 3)

        assertEquals(15, settings.audioPrefetchDepthFor(NetworkMetering.Unmetered))
        assertEquals(3, settings.audioPrefetchDepthFor(NetworkMetering.Metered))
        assertEquals(3, settings.audioPrefetchDepthFor(NetworkMetering.Unknown))
        val wifiOnly = CacheSettings(unmeteredAudioPrefetchDepth = 10, meteredAudioPrefetchDepth = 0)
        assertEquals(0, wifiOnly.audioPrefetchDepthFor(NetworkMetering.Metered))
        assertEquals(10, wifiOnly.audioPrefetchDepthFor(NetworkMetering.Unmetered))
        val mobileOnly = CacheSettings(unmeteredAudioPrefetchDepth = 0, meteredAudioPrefetchDepth = 5)
        assertEquals(0, mobileOnly.audioPrefetchDepthFor(NetworkMetering.Unmetered))
    }

    @Test
    fun hostsWithoutMeteringUseTheWifiDepthWhileObservingHostsKeepMobileDepthForUnknown() {
        val settings = CacheSettings(unmeteredAudioPrefetchDepth = 15, meteredAudioPrefetchDepth = 3)

        assertEquals(15, settings.audioPrefetchDepthFor(meteringSource = null))
        assertEquals(3, settings.audioPrefetchDepthFor(NetworkMeteringSource { NetworkMetering.Unknown }))
        assertEquals(3, settings.audioPrefetchDepthFor(NetworkMeteringSource { NetworkMetering.Metered }))
        assertEquals(15, settings.audioPrefetchDepthFor(NetworkMeteringSource { NetworkMetering.Unmetered }))
    }

    @Test
    fun storedSingleDepthMigratesToBothNetworksAndNewValuesWin() {
        val legacy = json.decodeFromString(CacheSettings.serializer(), """{"audioPrefetchDepth":7,"waveformsEnabled":false}""")
        assertEquals(7, legacy.unmeteredAudioPrefetchDepth)
        assertEquals(7, legacy.meteredAudioPrefetchDepth)
        assertEquals(false, legacy.waveformsEnabled)

        val current = json.decodeFromString(
            CacheSettings.serializer(),
            """{"audioPrefetchDepth":7,"unmeteredAudioPrefetchDepth":20,"meteredAudioPrefetchDepth":1}""",
        )
        assertEquals(20, current.unmeteredAudioPrefetchDepth)
        assertEquals(1, current.meteredAudioPrefetchDepth)

        val defaults = json.decodeFromString(CacheSettings.serializer(), "{}")
        assertEquals(DefaultAudioPrefetchDepth, defaults.unmeteredAudioPrefetchDepth)
        assertEquals(DefaultAudioPrefetchDepth, defaults.meteredAudioPrefetchDepth)

        val saved = CacheSettings(unmeteredAudioPrefetchDepth = 25, meteredAudioPrefetchDepth = 0)
        assertEquals(saved, json.decodeFromString(CacheSettings.serializer(), json.encodeToString(CacheSettings.serializer(), saved)))
    }

    @Test
    fun normalizationClampsBothDepths() {
        val settings = CacheSettings(unmeteredAudioPrefetchDepth = 99, meteredAudioPrefetchDepth = -4).normalized()

        assertEquals(25, settings.unmeteredAudioPrefetchDepth)
        assertEquals(0, settings.meteredAudioPrefetchDepth)
    }

    @Test
    fun bothDepthsRoundTripThroughExportImportAndKeepDeviceLocalCacheSettings() {
        val sender = CacheSettings(
            unmeteredAudioPrefetchDepth = 15,
            meteredAudioPrefetchDepth = 0,
            maxAudioCacheBytes = 4L * 1024L * 1024L * 1024L,
        )
        val document = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(cache = sender), 1L, "sender")
        val local = CacheSettings(
            customAudioCacheDirectory = "/local/cache",
            unmeteredAudioPrefetchDepth = 3,
            meteredAudioPrefetchDepth = 3,
        )

        val applied = apply(SettingsSyncJson.decode(SettingsSyncJson.encode(document)), local).cacheSettings

        assertEquals(15, applied.unmeteredAudioPrefetchDepth)
        assertEquals(0, applied.meteredAudioPrefetchDepth)
        assertEquals("/local/cache", applied.customAudioCacheDirectory)
        assertEquals(DefaultAudioCacheBytes, applied.maxAudioCacheBytes, "Storage budgets stay device-local")
        val reexported = buildSettingsSyncDocument(SettingsSyncLocalSnapshot(cache = applied), 2L, "receiver")
        assertEquals(
            document.preferences.audioPrefetch,
            SettingsSyncJson.decode(SettingsSyncJson.encode(reexported)).preferences.audioPrefetch,
        )
    }

    @Test
    fun olderExportsKeepTheMigratedLocalDepthOnBothNetworks() {
        val migratedLocal = json.decodeFromString(CacheSettings.serializer(), """{"audioPrefetchDepth":10}""")

        val applied = apply(SettingsSyncJson.decode("""{"preferences":{"playback":{}}}"""), migratedLocal).cacheSettings

        assertEquals(10, applied.unmeteredAudioPrefetchDepth)
        assertEquals(10, applied.meteredAudioPrefetchDepth)
    }

    @Test
    fun importedDepthsAreNormalized() {
        val raw = SettingsSyncJson.decode("""{"preferences":{"audioPrefetch":{"unmeteredDepth":400,"meteredDepth":-1}}}""")

        val applied = apply(raw, CacheSettings()).cacheSettings

        assertEquals(25, applied.unmeteredAudioPrefetchDepth)
        assertEquals(0, applied.meteredAudioPrefetchDepth)
    }

    private fun apply(document: SettingsSyncDocument, local: CacheSettings) =
        applySettingsSyncDocument(document, NoMediaSources, NoRadioDjs, localCache = local)

    private object NoMediaSources : ProviderMediaSourceRepository {
        override fun upsertProviderMediaSource(
            connection: ProviderMediaSourceConnection,
            cacheNamespace: String,
            providerId: String,
            preferredSourceId: String?,
        ): MediaSourceIdentity = error("No server profiles are imported here")
    }

    private object NoRadioDjs : RadioDjPresetRepository {
        override fun radioDjPresets(): List<RadioDjPreset> = emptyList()
        override fun replaceRadioDjPresets(presets: List<RadioDjPreset>) = Unit
        override fun upsertRadioDjPreset(preset: RadioDjPreset) = Unit
        override fun deleteRadioDjPreset(id: String) = Unit
    }
}
