package app.naviamp.desktop.settings

import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.provider.navidrome.NavidromeConnection
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
                ),
            ),
        )
    }

    fun loadPlaybackSettings(): PlaybackSettings =
        loadSettings().playback

    fun savePlaybackSettings(playbackSettings: PlaybackSettings) {
        saveSettings(loadSettings().copy(playback = playbackSettings))
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
)

@Serializable
data class PlaybackSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
)

@Serializable
data class SavedConnection(
    val baseUrl: String,
    val username: String,
    val token: String,
    val salt: String,
) {
    fun toConnection(): NavidromeConnection =
        NavidromeConnection(
            baseUrl = baseUrl,
            username = username,
            token = token,
            salt = salt,
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
        "connection" in keys || "playback" in keys
    }.getOrDefault(false)
