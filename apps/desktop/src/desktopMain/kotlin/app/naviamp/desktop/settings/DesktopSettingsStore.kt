package app.naviamp.desktop.settings

import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DesktopSettingsStore(
    private val settingsPath: Path = defaultSettingsPath(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun loadConnection(): SavedConnection? {
        return loadSettings().connection
    }

    fun saveConnection(connection: NavidromeConnection) {
        saveSettings(
            loadSettings().copy(
                connection = SavedConnection(
                    baseUrl = connection.baseUrl,
                    username = connection.username,
                    token = connection.token,
                    salt = connection.salt,
                    displayName = connection.displayName,
                    insecureSkipTlsVerification = connection.tlsSettings.insecureSkipTlsVerification,
                    customCertificatePath = connection.tlsSettings.customCertificatePath,
                    clientCertificateKeyStorePath = connection.tlsSettings.clientCertificateKeyStorePath,
                    clientCertificateKeyStorePassword = connection.tlsSettings.clientCertificateKeyStorePassword,
                ),
            ),
        )
    }

    fun clearConnection() {
        saveSettings(loadSettings().copy(connection = null))
    }

    fun loadPlaybackSettings(): PlaybackSettings =
        loadSettings().playback

    fun savePlaybackSettings(playbackSettings: PlaybackSettings) {
        saveSettings(loadSettings().copy(playback = playbackSettings))
    }

    fun loadVisualizerSettings(): VisualizerSettings =
        loadSettings().visualizer

    fun saveVisualizerSettings(visualizerSettings: VisualizerSettings) {
        saveSettings(loadSettings().copy(visualizer = visualizerSettings))
    }

    fun loadCacheSettings(): CacheSettings =
        loadSettings().cache

    fun saveCacheSettings(cacheSettings: CacheSettings) {
        saveSettings(loadSettings().copy(cache = cacheSettings))
    }

    fun loadWindowSettings(): WindowSettings =
        loadSettings().window

    fun saveWindowSettings(windowSettings: WindowSettings) {
        saveSettings(loadSettings().copy(window = windowSettings))
    }

    fun loadPlaybackSession(): PlaybackSessionSettings? =
        loadSettings().session

    fun savePlaybackSession(session: PlaybackSessionSettings?) {
        saveSettings(loadSettings().copy(session = session))
    }

    fun loadNavigationSettings(): NavigationSettings =
        loadSettings().navigation

    fun saveNavigationSettings(navigationSettings: NavigationSettings) {
        saveSettings(loadSettings().copy(navigation = navigationSettings))
    }

    fun loadSearchSettings(): SearchSettings =
        loadSettings().search

    fun saveSearchSettings(searchSettings: SearchSettings) {
        saveSettings(loadSettings().copy(search = searchSettings))
    }

    fun loadRecentRadioStreams(): List<RecentRadioStream> =
        loadSettings().recentRadioStreams

    fun saveRecentRadioStreams(streams: List<RecentRadioStream>) {
        saveSettings(loadSettings().copy(recentRadioStreams = streams.take(12)))
    }

    fun loadRecentPlaylistIds(): List<String> =
        loadSettings().recentPlaylistIds

    fun saveRecentPlaylistIds(ids: List<String>) {
        saveSettings(loadSettings().copy(recentPlaylistIds = ids.distinct().take(50)))
    }

    fun loadRecentInternetRadioStations(): List<SavedInternetRadioStation> =
        loadSettings().recentInternetRadioStations

    fun saveRecentInternetRadioStations(stations: List<SavedInternetRadioStation>) {
        saveSettings(loadSettings().copy(recentInternetRadioStations = stations.take(12)))
    }

    private fun loadSettings(): DesktopSettings {
        if (!settingsPath.exists()) return DesktopSettings()
        val text = settingsPath.readText()

        val settings = runCatching {
            json.decodeFromString<DesktopSettings>(text)
        }.getOrNull()

        if (settings != null && text.looksLikeDesktopSettings()) return settings

        val legacyConnection = runCatching {
            json.decodeFromString<SavedConnection>(text)
        }.getOrNull()

        return DesktopSettings(connection = legacyConnection)
    }

    private fun saveSettings(settings: DesktopSettings) {
        Files.createDirectories(settingsPath.parent)
        settingsPath.writeText(json.encodeToString(settings))
    }
}

@Serializable
data class DesktopSettings(
    val connection: SavedConnection? = null,
    val playback: PlaybackSettings = PlaybackSettings(),
    val visualizer: VisualizerSettings = VisualizerSettings(),
    val cache: CacheSettings = CacheSettings(),
    val window: WindowSettings = WindowSettings(),
    val session: PlaybackSessionSettings? = null,
    val navigation: NavigationSettings = NavigationSettings(),
    val search: SearchSettings = SearchSettings(),
    val recentRadioStreams: List<RecentRadioStream> = emptyList(),
    val recentPlaylistIds: List<String> = emptyList(),
    val recentInternetRadioStations: List<SavedInternetRadioStation> = emptyList(),
)

@Serializable
data class VisualizerSettings(
    val selectedVisualizer: String = "AudioSphere",
)

@Serializable
data class WindowSettings(
    val widthDp: Float = 950f,
    val heightDp: Float = 640f,
)

@Serializable
data class SavedConnection(
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
    val displayName: String? = null,
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String? = null,
    val clientCertificateKeyStorePath: String? = null,
    val clientCertificateKeyStorePassword: String? = null,
) {
    fun toConnection(): NavidromeConnection =
        NavidromeConnection(
            baseUrl = baseUrl,
            username = username,
            token = token,
            salt = salt,
            displayName = displayName,
            tlsSettings = NavidromeTlsSettings(
                insecureSkipTlsVerification = insecureSkipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
            ),
        )
}

private fun defaultSettingsPath(): Path {
    val os = System.getProperty("os.name").lowercase()
    val home = Path.of(System.getProperty("user.home"))

    val configDir = when {
        os.contains("mac") -> home.resolve("Library").resolve("Application Support").resolve("Naviamp")
        os.contains("win") -> Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString())
            .resolve("Naviamp")
        else -> Path.of(System.getenv("XDG_CONFIG_HOME") ?: home.resolve(".config").toString())
            .resolve("naviamp")
    }

    return configDir.resolve("settings.json")
}

private fun String.looksLikeDesktopSettings(): Boolean =
    runCatching {
        val keys = Json.parseToJsonElement(this).jsonObject.keys
            "connection" in keys ||
            "playback" in keys ||
            "visualizer" in keys ||
            "cache" in keys ||
            "window" in keys ||
            "session" in keys ||
            "navigation" in keys ||
            "search" in keys
    }.getOrDefault(false)
