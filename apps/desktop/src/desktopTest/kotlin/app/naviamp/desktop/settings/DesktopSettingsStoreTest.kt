package app.naviamp.desktop.settings

import app.naviamp.desktop.playback.ReplayGainMode
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
}
