package app.naviamp.desktop.settings

import app.naviamp.domain.source.ConnectionHeaderDefinition
import app.naviamp.domain.source.ConnectionSecondaryUrl
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.normalized
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.domain.cache.PlaybackSessionRepository
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
) : PlaybackSessionRepository {
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
                    nativeToken = connection.nativeToken,
                    displayName = connection.displayName,
                    insecureSkipTlsVerification = connection.tlsSettings.insecureSkipTlsVerification,
                    customCertificatePath = connection.tlsSettings.customCertificatePath,
                    clientCertificateKeyStorePath = connection.tlsSettings.clientCertificateKeyStorePath,
                    clientCertificateKeyStorePassword = connection.tlsSettings.clientCertificateKeyStorePassword,
                    secondaryUrls = connection.secondaryUrls,
                    customHeaders = connection.customHeaders,
                    selectedMusicFolderIds = connection.selectedMusicFolderIds,
                ),
            ),
        )
    }

    fun clearConnection() {
        saveSettings(loadSettings().copy(connection = null))
    }

    fun loadPlaybackSettings(): PlaybackSettings =
        loadSettings().playback.normalized()

    fun loadInterfaceSettings(): InterfaceSettings =
        loadSettings().interfaceSettings.normalized()

    fun saveInterfaceSettings(interfaceSettings: InterfaceSettings) {
        saveSettings(loadSettings().copy(interfaceSettings = interfaceSettings.normalized()))
    }

    fun savePlaybackSettings(playbackSettings: PlaybackSettings) {
        saveSettings(loadSettings().copy(playback = playbackSettings.normalized()))
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

    override fun loadPlaybackSession(sourceId: String?): PlaybackSessionSettings? =
        loadSettings().session

    fun loadPlaybackSession(): PlaybackSessionSettings? =
        loadPlaybackSession(sourceId = null)

    override fun savePlaybackSession(session: PlaybackSessionSettings?, sourceId: String?) {
        saveSettings(loadSettings().copy(session = session))
    }

    fun savePlaybackSession(session: PlaybackSessionSettings?) {
        savePlaybackSession(session = session, sourceId = null)
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

    fun loadSettingsSync(): DesktopSettingsSyncSettings =
        loadSettings().settingsSync

    fun saveSettingsSync(settingsSync: DesktopSettingsSyncSettings) {
        saveSettings(loadSettings().copy(settingsSync = settingsSync.normalized()))
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
    val interfaceSettings: InterfaceSettings = InterfaceSettings(),
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
    val settingsSync: DesktopSettingsSyncSettings = DesktopSettingsSyncSettings(),
)

@Serializable
data class DesktopSettingsSyncSettings(
    val directoryPath: String? = null,
    val autoExportEnabled: Boolean = false,
    val lastLocalUpdateEpochMillis: Long = 0L,
    val lastAppliedSyncUpdateEpochMillis: Long = 0L,
) {
    fun normalized(): DesktopSettingsSyncSettings =
        copy(
            directoryPath = directoryPath?.trim()?.takeIf { it.isNotEmpty() },
            autoExportEnabled = autoExportEnabled && directoryPath?.trim()?.isNotEmpty() == true,
            lastLocalUpdateEpochMillis = lastLocalUpdateEpochMillis.coerceAtLeast(0L),
            lastAppliedSyncUpdateEpochMillis = lastAppliedSyncUpdateEpochMillis.coerceAtLeast(0L),
        )
}

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
    val nativeToken: String? = null,
    val displayName: String? = null,
    val insecureSkipTlsVerification: Boolean = false,
    val customCertificatePath: String? = null,
    val clientCertificateKeyStorePath: String? = null,
    val clientCertificateKeyStorePassword: String? = null,
    val secondaryUrls: List<ConnectionSecondaryUrl> = emptyList(),
    val customHeaders: List<ConnectionHeaderDefinition> = emptyList(),
    val selectedMusicFolderIds: List<String> = emptyList(),
) {
    fun toConnection(): NavidromeConnection =
        NavidromeConnection(
            baseUrl = baseUrl,
            username = username,
            token = token,
            salt = salt,
            nativeToken = nativeToken,
            displayName = displayName,
            tlsSettings = NavidromeTlsSettings(
                insecureSkipTlsVerification = insecureSkipTlsVerification,
                customCertificatePath = customCertificatePath,
                clientCertificateKeyStorePath = clientCertificateKeyStorePath,
                clientCertificateKeyStorePassword = clientCertificateKeyStorePassword,
            ),
            secondaryUrls = secondaryUrls.mapNotNull { it.normalized() },
            customHeaders = customHeaders.mapNotNull { it.normalized() },
            selectedMusicFolderIds = selectedMusicFolderIds,
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
            "interfaceSettings" in keys ||
            "playback" in keys ||
            "visualizer" in keys ||
            "cache" in keys ||
            "window" in keys ||
            "session" in keys ||
            "navigation" in keys ||
            "search" in keys ||
            "settingsSync" in keys
    }.getOrDefault(false)
