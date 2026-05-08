package app.naviamp.desktop.settings

import app.naviamp.desktop.playback.ReplayGainMode
import app.naviamp.domain.AudioInfo
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.provider.navidrome.NavidromeConnection
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
        store.savePlaybackSettings(PlaybackSettings(replayGainMode = ReplayGainMode.Album))

        store.saveConnection(
            NavidromeConnection(
                baseUrl = "https://music.example.test",
                username = "user",
                token = "token",
                salt = "salt",
            ),
        )

        assertEquals(ReplayGainMode.Album, store.loadPlaybackSettings().replayGainMode)
        assertEquals("user", store.loadConnection()?.username)
    }

    @Test
    fun savePlaybackSettingsWritesSettingsDocument() {
        val path = createTempDirectory().resolve("settings.json")
        val store = DesktopSettingsStore(path)

        store.savePlaybackSettings(PlaybackSettings(replayGainMode = ReplayGainMode.Track))

        assertEquals(ReplayGainMode.Track, store.loadPlaybackSettings().replayGainMode)
        assertEquals(true, path.readText().contains("replayGainMode"))
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
            artistName = "Artist",
            albumTitle = "Album",
            durationSeconds = 180,
            coverArtId = "cover-1",
            audioInfo = AudioInfo(
                codec = "FLAC",
                bitrateKbps = 1000,
                contentType = "audio/flac",
            ),
            replayGain = null,
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
        assertEquals("FLAC", session?.currentTrack()?.audioInfo?.codec)
        assertEquals(2, session?.toTracks()?.size)
    }
}
