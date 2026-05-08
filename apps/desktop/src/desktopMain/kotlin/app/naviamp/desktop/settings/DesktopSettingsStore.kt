package app.naviamp.desktop.settings

import app.naviamp.provider.navidrome.NavidromeConnection
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
        if (!settingsPath.exists()) return null

        return runCatching {
            json.decodeFromString<SavedConnection>(settingsPath.readText())
        }.getOrNull()
    }

    fun saveConnection(connection: NavidromeConnection) {
        Files.createDirectories(settingsPath.parent)
        settingsPath.writeText(
            json.encodeToString(
                SavedConnection(
                    baseUrl = connection.baseUrl,
                    username = connection.username,
                    token = connection.token,
                    salt = connection.salt,
                ),
            ),
        )
    }
}

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
