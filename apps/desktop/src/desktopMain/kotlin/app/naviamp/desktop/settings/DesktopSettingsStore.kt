package app.naviamp.desktop.settings

import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
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

    fun clearConnection() {
        saveSettings(loadSettings().copy(connection = null))
    }

    fun loadPlaybackSettings(): PlaybackSettings =
        loadSettings().playback

    fun savePlaybackSettings(playbackSettings: PlaybackSettings) {
        saveSettings(loadSettings().copy(playback = playbackSettings))
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
    val window: WindowSettings = WindowSettings(),
    val session: PlaybackSessionSettings? = null,
    val navigation: NavigationSettings = NavigationSettings(),
    val search: SearchSettings = SearchSettings(),
)

@Serializable
data class PlaybackSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val crossfadeDurationSeconds: Int = 0,
    val volumePercent: Int = 100,
    val debugLoggingEnabled: Boolean = false,
)

@Serializable
data class WindowSettings(
    val widthDp: Float = 950f,
    val heightDp: Float = 640f,
)

@Serializable
data class NavigationSettings(
    val route: String = "Home",
    val lastContentRoute: String = "Home",
)

@Serializable
data class SearchSettings(
    val query: String = "",
)

@Serializable
data class PlaybackSessionSettings(
    val tracks: List<SavedTrack> = emptyList(),
    val currentIndex: Int = -1,
    val positionSeconds: Double? = null,
) {
    fun currentTrack(): Track? =
        tracks.getOrNull(currentIndex)?.toTrack()

    fun toTracks(): List<Track> =
        tracks.map { it.toTrack() }

    companion object {
        fun fromTracks(
            tracks: List<Track>,
            currentIndex: Int,
            positionSeconds: Double? = null,
        ): PlaybackSessionSettings? {
            if (tracks.isEmpty() || currentIndex !in tracks.indices) return null
            return PlaybackSessionSettings(
                tracks = tracks.map { SavedTrack.fromTrack(it) },
                currentIndex = currentIndex,
                positionSeconds = positionSeconds?.takeIf { it > 0.0 },
            )
        }
    }
}

@Serializable
data class SavedTrack(
    val id: String,
    val title: String,
    val artistId: String? = null,
    val artistName: String,
    val albumId: String? = null,
    val albumTitle: String? = null,
    val albumReleaseYear: Int? = null,
    val durationSeconds: Int? = null,
    val coverArtId: String? = null,
    val audioInfo: SavedAudioInfo? = null,
    val favoritedAtIso8601: String? = null,
    val userRating: Int? = null,
) {
    fun toTrack(): Track =
        Track(
            id = TrackId(id),
            title = title,
            artistId = artistId?.let { ArtistId(it) },
            artistName = artistName,
            albumId = albumId?.let { AlbumId(it) },
            albumTitle = albumTitle,
            albumReleaseYear = albumReleaseYear,
            durationSeconds = durationSeconds,
            coverArtId = coverArtId,
            audioInfo = audioInfo?.toAudioInfo(),
            replayGain = null,
            favoritedAtIso8601 = favoritedAtIso8601,
            userRating = userRating,
        )

    companion object {
        fun fromTrack(track: Track): SavedTrack =
            SavedTrack(
                id = track.id.value,
                title = track.title,
                artistId = track.artistId?.value,
                artistName = track.artistName,
                albumId = track.albumId?.value,
                albumTitle = track.albumTitle,
                albumReleaseYear = track.albumReleaseYear,
                durationSeconds = track.durationSeconds,
                coverArtId = track.coverArtId,
                audioInfo = track.audioInfo?.let { SavedAudioInfo.fromAudioInfo(it) },
                favoritedAtIso8601 = track.favoritedAtIso8601,
                userRating = track.userRating,
            )
    }
}

@Serializable
data class SavedAudioInfo(
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val contentType: String? = null,
    val bitDepth: Int? = null,
    val samplingRateHz: Int? = null,
) {
    fun toAudioInfo(): AudioInfo =
        AudioInfo(
            codec = codec,
            bitrateKbps = bitrateKbps,
            contentType = contentType,
            bitDepth = bitDepth,
            samplingRateHz = samplingRateHz,
        )

    companion object {
        fun fromAudioInfo(audioInfo: AudioInfo): SavedAudioInfo =
            SavedAudioInfo(
                codec = audioInfo.codec,
                bitrateKbps = audioInfo.bitrateKbps,
                contentType = audioInfo.contentType,
                bitDepth = audioInfo.bitDepth,
                samplingRateHz = audioInfo.samplingRateHz,
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

private fun String.looksLikeDesktopSettings(): Boolean =
    runCatching {
        val keys = Json.parseToJsonElement(this).jsonObject.keys
        "connection" in keys ||
            "playback" in keys ||
            "window" in keys ||
            "session" in keys ||
            "navigation" in keys ||
            "search" in keys
    }.getOrDefault(false)
