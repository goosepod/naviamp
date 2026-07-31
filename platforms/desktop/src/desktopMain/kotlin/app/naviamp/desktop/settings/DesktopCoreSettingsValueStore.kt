package app.naviamp.desktop.settings

import app.naviamp.presentation.NaviampCoreLegacySettingsValueStore
import app.naviamp.presentation.NaviampCoreMutableSettingsValueStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/** Atomic JVM filesystem string storage; Core owns every key, model, default, and migration. */
class DesktopCoreSettingsValueStore(
    private val path: Path = defaultDesktopCoreSettingsPath(),
) : NaviampCoreMutableSettingsValueStore, NaviampCoreLegacySettingsValueStore {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    override fun contains(key: String): Boolean = key in document()

    override fun read(key: String): String? = document()[key]?.let { value ->
        (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: value.toString()
    }

    @Synchronized
    override fun write(key: String, value: String) {
        writeDocument(JsonObject(document() + (key to JsonPrimitive(value))))
    }

    @Synchronized
    override fun remove(key: String) {
        if (key in document()) writeDocument(JsonObject(document() - key))
    }

    private fun document(): JsonObject = runCatching {
        if (path.exists()) json.parseToJsonElement(path.readText()).jsonObject else JsonObject(emptyMap())
    }.getOrDefault(JsonObject(emptyMap()))

    private fun writeDocument(updated: JsonObject) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        temporary.writeText(json.encodeToString(JsonObject.serializer(), updated))
        runCatching {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun defaultDesktopDataDirectory(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("mac") || os.contains("darwin") -> home.resolve("Library/Application Support/Naviamp")
        os.contains("win") ->
            Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString()).resolve("Naviamp")
        else -> home.resolve(".local/share/naviamp")
    }
}

fun defaultDesktopCoreSettingsPath(): Path {
    val home = Path.of(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()
    val directory = when {
        os.contains("mac") || os.contains("darwin") -> home.resolve("Library/Application Support/Naviamp")
        os.contains("win") ->
            Path.of(System.getenv("APPDATA") ?: home.resolve("AppData/Roaming").toString()).resolve("Naviamp")
        else -> Path.of(System.getenv("XDG_CONFIG_HOME") ?: home.resolve(".config").toString()).resolve("naviamp")
    }
    return directory.resolve("settings.json")
}
