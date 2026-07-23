package app.naviamp.desktop.settings

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.settings.RecentRadioStream
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.VisualizerSettings
import app.naviamp.domain.settings.normalized
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Desktop filesystem adapter for the portable preference models used by Core.
 *
 * It updates only its owned keys in the existing settings document so legacy/session fields survive
 * the parallel-host cutover. Product defaults, normalization, and application remain in Core.
 */
class DesktopCoreSettingsStore(private val path: Path) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun loadInterfaceSettings(): InterfaceSettings =
        read("interfaceSettings", InterfaceSettings.serializer(), InterfaceSettings()).normalized()

    fun saveInterfaceSettings(value: InterfaceSettings) =
        write("interfaceSettings", InterfaceSettings.serializer(), value.normalized())

    fun loadPlaybackSettings(): PlaybackSettings =
        read("playback", PlaybackSettings.serializer(), PlaybackSettings()).normalized()

    fun savePlaybackSettings(value: PlaybackSettings) =
        write("playback", PlaybackSettings.serializer(), value.normalized())

    /** One-way migration input for the pre-Core Desktop settings document. */
    fun loadLegacyPlaybackSession(): PlaybackSessionSettings? =
        readOrNull("session", PlaybackSessionSettings.serializer())

    fun removeLegacyPlaybackSession() = remove("session")

    fun loadCacheSettings(): CacheSettings =
        read("cache", CacheSettings.serializer(), CacheSettings()).normalized()

    fun saveCacheSettings(value: CacheSettings) =
        write("cache", CacheSettings.serializer(), value.normalized())

    fun loadVisualizerSettings(): VisualizerSettings =
        read("visualizer", VisualizerSettings.serializer(), VisualizerSettings())

    fun saveVisualizerSettings(value: VisualizerSettings) =
        write("visualizer", VisualizerSettings.serializer(), value)

    fun loadRecentPlaylistIds(): List<String> =
        read("recentPlaylistIds", ListSerializer(String.serializer()), emptyList())

    fun saveRecentPlaylistIds(ids: List<String>) =
        write("recentPlaylistIds", ListSerializer(String.serializer()), ids.distinct().take(50))

    fun loadRecentRadioStreams(): List<RecentRadioStream> =
        read("recentRadioStreams", ListSerializer(RecentRadioStream.serializer()), emptyList())

    fun saveRecentRadioStreams(streams: List<RecentRadioStream>) =
        write("recentRadioStreams", ListSerializer(RecentRadioStream.serializer()), streams)

    fun loadRecentInternetRadioStations(): List<SavedInternetRadioStation> =
        read(
            "recentInternetRadioStations",
            ListSerializer(SavedInternetRadioStation.serializer()),
            emptyList(),
        )

    fun saveRecentInternetRadioStations(stations: List<SavedInternetRadioStation>) =
        write(
            "recentInternetRadioStations",
            ListSerializer(SavedInternetRadioStation.serializer()),
            stations,
        )

    private fun document(): JsonObject = runCatching {
        if (path.exists()) json.parseToJsonElement(path.readText()).jsonObject else JsonObject(emptyMap())
    }.getOrDefault(JsonObject(emptyMap()))

    @Synchronized
    private fun <T> read(key: String, serializer: KSerializer<T>, fallback: T): T =
        document()[key]?.let { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() } ?: fallback

    @Synchronized
    private fun <T> readOrNull(key: String, serializer: KSerializer<T>): T? =
        document()[key]?.let { runCatching { json.decodeFromJsonElement(serializer, it) }.getOrNull() }

    @Synchronized
    private fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        val updated = JsonObject(document() + (key to json.encodeToJsonElement(serializer, value)))
        writeDocument(updated)
    }

    @Synchronized
    private fun remove(key: String) {
        writeDocument(JsonObject(document() - key))
    }

    private fun writeDocument(updated: JsonObject) {
        Files.createDirectories(path.parent)
        val temporary = path.resolveSibling("${path.fileName}.tmp")
        temporary.writeText(json.encodeToString(JsonObject.serializer(), updated))
        runCatching {
            Files.move(
                temporary,
                path,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
        }
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
