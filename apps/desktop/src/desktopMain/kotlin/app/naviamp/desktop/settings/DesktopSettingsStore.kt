package app.naviamp.desktop.settings

import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.domain.Album
import app.naviamp.domain.AlbumId
import app.naviamp.domain.Artist
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
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
data class PlaybackSettings(
    val replayGainMode: ReplayGainMode = ReplayGainMode.Off,
    val crossfadeDurationSeconds: Int = 0,
    val volumePercent: Int = 100,
    val debugLoggingEnabled: Boolean = false,
    val lrclibLyricsEnabled: Boolean = false,
    val previousButtonBehavior: PreviousButtonBehavior = PreviousButtonBehavior.RestartThenPrevious,
    val upNextSelectionBehavior: UpNextSelectionBehavior = UpNextSelectionBehavior.MoveSelectedToCurrent,
)

@Serializable
enum class PreviousButtonBehavior {
    AlwaysPrevious,
    RestartThenPrevious,
}

@Serializable
enum class UpNextSelectionBehavior {
    MoveSelectedToCurrent,
    SkipToSelected,
}

@Serializable
data class CacheSettings(
    val audioCachingEnabled: Boolean = true,
    val audioPrefetchDepth: Int = 10,
    val maxAudioCacheBytes: Long = 2L * 1024L * 1024L * 1024L,
    val maxDownloadBytes: Long = 10L * 1024L * 1024L * 1024L,
) {
    fun normalized(): CacheSettings =
        copy(
            audioPrefetchDepth = audioPrefetchDepth.coerceIn(0, 25),
            maxAudioCacheBytes = maxAudioCacheBytes.coerceIn(256L * 1024L * 1024L, 20L * 1024L * 1024L * 1024L),
            maxDownloadBytes = maxDownloadBytes.coerceIn(512L * 1024L * 1024L, 100L * 1024L * 1024L * 1024L),
        )
}

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
data class RecentRadioStream(
    val id: String,
    val label: String,
    val kind: RecentRadioKind,
    val artist: SavedArtist? = null,
    val album: SavedAlbum? = null,
    val track: SavedTrack? = null,
    val genre: String? = null,
    val fromYear: Int? = null,
    val toYear: Int? = null,
)

@Serializable
enum class RecentRadioKind {
    Library,
    RandomAlbum,
    Genre,
    Decade,
    Artist,
    Album,
    Track,
}

@Serializable
data class SavedArtist(
    val id: String,
    val name: String,
) {
    fun toArtist(): Artist =
        Artist(id = ArtistId(id), name = name)

    companion object {
        fun fromArtist(artist: Artist): SavedArtist =
            SavedArtist(id = artist.id.value, name = artist.name)
    }
}

@Serializable
data class SavedAlbum(
    val id: String,
    val title: String,
    val artistName: String,
    val coverArtId: String? = null,
    val recentlyAddedAtIso8601: String? = null,
    val releaseYear: Int? = null,
) {
    fun toAlbum(): Album =
        Album(
            id = AlbumId(id),
            title = title,
            artistName = artistName,
            coverArtId = coverArtId,
            recentlyAddedAtIso8601 = recentlyAddedAtIso8601,
            releaseYear = releaseYear,
        )

    companion object {
        fun fromAlbum(album: Album): SavedAlbum =
            SavedAlbum(
                id = album.id.value,
                title = album.title,
                artistName = album.artistName,
                coverArtId = album.coverArtId,
                recentlyAddedAtIso8601 = album.recentlyAddedAtIso8601,
                releaseYear = album.releaseYear,
            )
    }
}

@Serializable
data class SavedInternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
) {
    fun toStation(): InternetRadioStation =
        InternetRadioStation(
            id = id,
            name = name,
            streamUrl = streamUrl,
            homePageUrl = homePageUrl,
        )

    companion object {
        fun fromStation(station: InternetRadioStation): SavedInternetRadioStation =
            SavedInternetRadioStation(
                id = station.id,
                name = station.name,
                streamUrl = station.streamUrl,
                homePageUrl = station.homePageUrl,
            )
    }
}

@Serializable
data class PlaybackSessionSettings(
    val tracks: List<SavedTrack> = emptyList(),
    val currentIndex: Int = -1,
    val positionSeconds: Double? = null,
    val internetRadioStation: SavedInternetRadioStation? = null,
) {
    fun currentTrack(): Track? =
        tracks.getOrNull(currentIndex)?.toTrack() ?: internetRadioStation?.toTrack()

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

        fun fromInternetRadioStation(station: InternetRadioStation): PlaybackSessionSettings =
            PlaybackSessionSettings(
                internetRadioStation = SavedInternetRadioStation.fromStation(station),
            )
    }
}

private fun SavedInternetRadioStation.toTrack(): Track =
    Track(
        id = TrackId("internet-radio:$id"),
        title = name,
        artistName = "Internet Radio",
        albumTitle = homePageUrl ?: streamUrl,
        durationSeconds = null,
        coverArtId = null,
        audioInfo = null,
        replayGain = null,
    )

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
                "cache" in keys ||
                "window" in keys ||
            "session" in keys ||
            "navigation" in keys ||
            "search" in keys
    }.getOrDefault(false)
