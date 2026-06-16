package app.naviamp.desktop.settings

import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.AlbumId
import app.naviamp.domain.ArtistId
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.InternetRadioStation
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.provider.navidrome.NavidromeConnection
import app.naviamp.provider.navidrome.NavidromeTlsSettings
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSettingsStoreTest {
    @Test
    fun loadConnectionReadsLegacyConnectionFile() {
        val path = createTempDirectory().resolve("settings.json")
        path.writeText(
            """
            {
              "baseUrl": "https://music.example.test",
              "username": "user",
              "token": "token",
              "salt": "salt"
            }
            """.trimIndent(),
        )

        val connection = DesktopSettingsStore(path).loadConnection()

        assertEquals("https://music.example.test", connection?.baseUrl)
        assertEquals("user", connection?.username)
        assertEquals("token", connection?.token)
        assertEquals("salt", connection?.salt)
    }

    @Test
    fun saveConnectionPreservesPlaybackSettings() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)
        store.savePlaybackSettings(
            PlaybackSettings(
                replayGainMode = ReplayGainMode.Album,
                gaplessEnabled = false,
                crossfadeDurationSeconds = 5,
                volumePercent = 72,
                debugLoggingEnabled = true,
                removePlayedTracksFromQueue = true,
            ),
        )

        store.saveConnection(
            NavidromeConnection(
                baseUrl = "https://music.example.test",
                username = "user",
                token = "token",
                salt = "salt",
            ),
        )

        assertEquals(ReplayGainMode.Album, store.loadPlaybackSettings().replayGainMode)
        assertEquals(false, store.loadPlaybackSettings().gaplessEnabled)
        assertEquals(5, store.loadPlaybackSettings().crossfadeDurationSeconds)
        assertEquals(72, store.loadPlaybackSettings().volumePercent)
        assertEquals(true, store.loadPlaybackSettings().debugLoggingEnabled)
        assertEquals(true, store.loadPlaybackSettings().removePlayedTracksFromQueue)
        assertEquals("user", store.loadConnection()?.username)
    }

    @Test
    fun saveConnectionRoundTripsTlsSettings() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)

        store.saveConnection(
            NavidromeConnection(
                baseUrl = "https://music.example.test",
                username = "user",
                token = "token",
                salt = "salt",
                tlsSettings = NavidromeTlsSettings(
                    insecureSkipTlsVerification = true,
                    customCertificatePath = "/certs/navidrome.pem",
                    clientCertificateKeyStorePath = "/certs/client.p12",
                    clientCertificateKeyStorePassword = "secret",
                ),
            ),
        )

        val tlsSettings = store.loadConnection()?.toConnection()?.tlsSettings
        assertEquals(true, tlsSettings?.insecureSkipTlsVerification)
        assertEquals("/certs/navidrome.pem", tlsSettings?.customCertificatePath)
        assertEquals("/certs/client.p12", tlsSettings?.clientCertificateKeyStorePath)
        assertEquals("secret", tlsSettings?.clientCertificateKeyStorePassword)
    }

    @Test
    fun savePlaybackSettingsWritesSettingsDocument() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)

        store.savePlaybackSettings(
            PlaybackSettings(
                replayGainMode = ReplayGainMode.Track,
                gaplessEnabled = false,
                crossfadeDurationSeconds = 8,
                volumePercent = 64,
                debugLoggingEnabled = true,
                removePlayedTracksFromQueue = true,
            ),
        )

        assertEquals(ReplayGainMode.Track, store.loadPlaybackSettings().replayGainMode)
        assertEquals(false, store.loadPlaybackSettings().gaplessEnabled)
        assertEquals(8, store.loadPlaybackSettings().crossfadeDurationSeconds)
        assertEquals(64, store.loadPlaybackSettings().volumePercent)
        assertEquals(true, store.loadPlaybackSettings().debugLoggingEnabled)
        assertEquals(true, store.loadPlaybackSettings().removePlayedTracksFromQueue)
        assertEquals(true, path.readText().contains("replayGainMode"))
        assertEquals(true, path.readText().contains("gaplessEnabled"))
        assertEquals(true, path.readText().contains("crossfadeDurationSeconds"))
        assertEquals(true, path.readText().contains("volumePercent"))
        assertEquals(true, path.readText().contains("debugLoggingEnabled"))
        assertEquals(true, path.readText().contains("removePlayedTracksFromQueue"))
    }

    @Test
    fun saveWindowSettingsPreservesConnection() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)
        store.saveConnection(
            NavidromeConnection(
                baseUrl = "https://music.example.test",
                username = "user",
                token = "token",
                salt = "salt",
            ),
        )

        store.saveWindowSettings(WindowSettings(widthDp = 720f, heightDp = 480f))

        assertEquals(720f, store.loadWindowSettings().widthDp)
        assertEquals(480f, store.loadWindowSettings().heightDp)
        assertEquals("user", store.loadConnection()?.username)
    }

    @Test
    fun savePlaybackSessionRoundTripsCurrentQueue() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)
        val first = Track(
            id = TrackId("first"),
            title = "First Track",
            artistId = ArtistId("artist-1"),
            artistName = "Artist",
            albumId = AlbumId("album-1"),
            albumTitle = "Album",
            albumReleaseYear = 1986,
            durationSeconds = 180,
            coverArtId = "cover-1",
            audioInfo = AudioInfo(
                codec = "FLAC",
                bitrateKbps = 1000,
                contentType = "audio/flac",
                bitDepth = 16,
                samplingRateHz = 44100,
            ),
            replayGain = null,
            favoritedAtIso8601 = "2026-05-09T13:45:00Z",
            userRating = 4,
        )
        val second = first.copy(
            id = TrackId("second"),
            title = "Second Track",
        )

        store.savePlaybackSession(
            PlaybackSessionSettings.fromTracks(
                tracks = listOf(first, second),
                currentIndex = 1,
            ),
        )

        val session = store.loadPlaybackSession()
        assertEquals("Second Track", session?.currentTrack()?.title)
        assertEquals("artist-1", session?.currentTrack()?.artistId?.value)
        assertEquals("album-1", session?.currentTrack()?.albumId?.value)
        assertEquals(1986, session?.currentTrack()?.albumReleaseYear)
        assertEquals("FLAC", session?.currentTrack()?.audioInfo?.codec)
        assertEquals(16, session?.currentTrack()?.audioInfo?.bitDepth)
        assertEquals(44100, session?.currentTrack()?.audioInfo?.samplingRateHz)
        assertEquals("2026-05-09T13:45:00Z", session?.currentTrack()?.favoritedAtIso8601)
        assertEquals(4, session?.currentTrack()?.userRating)
        assertEquals(2, session?.toTracks()?.size)
    }

    @Test
    fun savePlaybackSessionRoundTripsInternetRadioStation() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)

        store.savePlaybackSession(
            PlaybackSessionSettings.fromInternetRadioStation(
                InternetRadioStation(
                    id = "station-1",
                    name = "Station One",
                    streamUrl = "https://example.com/stream.mp3",
                    homePageUrl = "https://example.com",
                ),
            ),
        )

        val session = store.loadPlaybackSession()
        assertEquals("Station One", session?.internetRadioStation?.name)
        assertEquals("internet-radio:station-1", session?.currentTrack()?.id?.value)
        assertEquals("https://example.com/stream.mp3", session?.internetRadioStation?.streamUrl)
    }

    @Test
    fun saveNavigationSettingsPreservesPlaybackSession() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)
        store.savePlaybackSettings(
            PlaybackSettings(
                replayGainMode = ReplayGainMode.Track,
                crossfadeDurationSeconds = 3,
            ),
        )

        store.saveNavigationSettings(NavigationSettings(route = "Player", lastContentRoute = "Search"))

        assertEquals("Player", store.loadNavigationSettings().route)
        assertEquals("Search", store.loadNavigationSettings().lastContentRoute)
        assertEquals(ReplayGainMode.Track, store.loadPlaybackSettings().replayGainMode)
        assertEquals(3, store.loadPlaybackSettings().crossfadeDurationSeconds)
    }

    @Test
    fun saveSearchSettingsPreservesNavigation() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)
        store.saveNavigationSettings(NavigationSettings(route = "Search"))

        store.saveSearchSettings(SearchSettings(query = "new order"))

        assertEquals("new order", store.loadSearchSettings().query)
        assertEquals("Search", store.loadNavigationSettings().route)
    }
}
