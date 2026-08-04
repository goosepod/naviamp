package app.naviamp.presentation

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.normalized
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Opaque string persistence effect; hosts implement only their native key/value API. */
interface NaviampCoreSettingsValueStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
}

/** Optional key-removal effect used only after a durable one-way settings migration. */
interface NaviampCoreMutableSettingsValueStore : NaviampCoreSettingsValueStore {
    fun remove(key: String)
}

/** Owns the portable settings keys, serialization, defaults, normalization, and Core mapping. */
data class NaviampCoreSettingsValueCatalog(
    val storedSettings: NaviampCoreStoredSettings,
    val savePlayback: (PlaybackSettings) -> Unit,
)

fun naviampCoreSettingsValueCatalog(
    values: NaviampCoreSettingsValueStore,
    json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
): NaviampCoreSettingsValueCatalog {
    (values as? NaviampCoreLegacySettingsValueStore)?.let { legacy ->
        migrateLegacyNaviampSettings(legacy = legacy, destination = values, json = json)
    }
    return naviampCoreSettingsValueCatalogWithoutMigration(values, json)
}

/** Internal construction seam used by migration to avoid recursively re-entering migration. */
internal fun naviampCoreSettingsValueCatalogWithoutMigration(
    values: NaviampCoreSettingsValueStore,
    json: Json,
): NaviampCoreSettingsValueCatalog {
    return NaviampCoreSettingsValueCatalog(
        savePlayback = { write(values, json, KeyPlayback, it.normalized()) },
        storedSettings = NaviampCoreStoredSettings(
            loadInterface = { read(values, json, KeyInterface, InterfaceSettings()).normalized() },
            saveInterface = { write(values, json, KeyInterface, it.normalized()) },
            loadPlayback = { read(values, json, KeyPlayback, PlaybackSettings()).normalized() },
            loadCache = { read(values, json, KeyCache, CacheSettings()).normalized() },
            saveCache = { write(values, json, KeyCache, it.normalized()) },
            loadVisualizer = { read(values, json, KeyVisualizer, VisualizerSettings()) },
            saveVisualizer = { write(values, json, KeyVisualizer, it) },
            loadRecentRadioStreams = {
                read(values, json, KeyRecentRadio, emptyList<RecentRadioStream>())
            },
            saveRecentRadioStreams = { write(values, json, KeyRecentRadio, it) },
            loadRecentInternetRadioStations = {
                read(values, json, KeyRecentInternetRadio, emptyList<SavedInternetRadioStation>())
            },
            saveRecentInternetRadioStations = { write(values, json, KeyRecentInternetRadio, it) },
            loadSyncRuntime = {
                read(values, json, KeySyncRuntime, SettingsSyncRuntimeState()).normalized()
            },
            saveSyncRuntime = { write(values, json, KeySyncRuntime, it.normalized()) },
            loadRecentPlaylistIds = { read(values, json, KeyRecentPlaylists, emptyList<String>()) },
            saveRecentPlaylistIds = { write(values, json, KeyRecentPlaylists, it) },
        ),
    )
}

private inline fun <reified T> read(
    values: NaviampCoreSettingsValueStore,
    json: Json,
    key: String,
    default: T,
): T = values.read(key)
    ?.let { encoded -> runCatching { json.decodeFromString<T>(encoded) }.getOrNull() }
    ?: default

private inline fun <reified T> write(
    values: NaviampCoreSettingsValueStore,
    json: Json,
    key: String,
    value: T,
) {
    values.write(key, json.encodeToString(value))
}

internal const val KeyInterface = "naviamp.interface"
internal const val KeyPlayback = "naviamp.playback"
internal const val KeyCache = "naviamp.cache"
internal const val KeyVisualizer = "naviamp.visualizer"
internal const val KeyRecentRadio = "naviamp.recentRadio"
internal const val KeyRecentInternetRadio = "naviamp.recentInternetRadio"
internal const val KeySyncRuntime = "naviamp.syncRuntime"
internal const val KeyRecentPlaylists = "naviamp.recentPlaylists"
