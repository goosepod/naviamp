package app.naviamp.ios.settings

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.normalized
import app.naviamp.presentation.NaviampCoreStoredSettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/** Thin NSUserDefaults byte/string effect for Core's portable settings models. */
class IosCoreSettingsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun storedSettings(): NaviampCoreStoredSettings = NaviampCoreStoredSettings(
        loadInterface = ::loadInterface,
        saveInterface = ::saveInterface,
        loadPlayback = ::loadPlayback,
        loadCache = ::loadCache,
        saveCache = ::saveCache,
        loadVisualizer = { read(KeyVisualizer, VisualizerSettings()) },
        saveVisualizer = { write(KeyVisualizer, it) },
        loadRecentRadioStreams = { read(KeyRecentRadio, emptyList<RecentRadioStream>()) },
        saveRecentRadioStreams = { write(KeyRecentRadio, it) },
        loadRecentInternetRadioStations = { read(KeyRecentInternetRadio, emptyList<SavedInternetRadioStation>()) },
        saveRecentInternetRadioStations = { write(KeyRecentInternetRadio, it) },
        loadSyncRuntime = { read(KeySyncRuntime, SettingsSyncRuntimeState()).normalized() },
        saveSyncRuntime = { write(KeySyncRuntime, it.normalized()) },
        loadRecentPlaylistIds = { read(KeyRecentPlaylists, emptyList<String>()) },
        saveRecentPlaylistIds = { write(KeyRecentPlaylists, it) },
    )

    fun loadInterface(): InterfaceSettings = read(KeyInterface, InterfaceSettings()).normalized()
    fun saveInterface(settings: InterfaceSettings) = write(KeyInterface, settings.normalized())
    fun loadPlayback(): PlaybackSettings = read(KeyPlayback, PlaybackSettings()).normalized()
    fun savePlayback(settings: PlaybackSettings) = write(KeyPlayback, settings.normalized())
    fun loadCache(): CacheSettings = read(KeyCache, CacheSettings()).normalized()
    fun saveCache(settings: CacheSettings) = write(KeyCache, settings.normalized())

    private inline fun <reified T> read(key: String, default: T): T =
        defaults.stringForKey(key)
            ?.let { encoded -> runCatching { json.decodeFromString<T>(encoded) }.getOrNull() }
            ?: default

    private inline fun <reified T> write(key: String, value: T) {
        defaults.setObject(json.encodeToString(value), forKey = key)
    }
}

private const val KeyInterface = "naviamp.interface"
private const val KeyPlayback = "naviamp.playback"
private const val KeyCache = "naviamp.cache"
private const val KeyVisualizer = "naviamp.visualizer"
private const val KeyRecentRadio = "naviamp.recentRadio"
private const val KeyRecentInternetRadio = "naviamp.recentInternetRadio"
private const val KeySyncRuntime = "naviamp.syncRuntime"
private const val KeyRecentPlaylists = "naviamp.recentPlaylists"
