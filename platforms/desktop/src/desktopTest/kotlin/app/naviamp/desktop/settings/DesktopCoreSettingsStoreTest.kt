package app.naviamp.desktop.settings

import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.PlaybackSessionSettings
import app.naviamp.domain.Track
import app.naviamp.domain.TrackId
import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.settings.VisualizerSettings
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopCoreSettingsStoreTest {
    @Test
    fun readsTheLegacyPlaybackSessionForOneWayDatabaseMigration() {
        val directory = Files.createTempDirectory("naviamp-core-settings-session")
        val path = directory.resolve("settings.json")
        val session = PlaybackSessionSettings.fromTracks(
            listOf(
                Track(
                    id = TrackId("remembered"),
                    title = "Remembered",
                    artistName = "Artist",
                    albumTitle = null,
                    durationSeconds = null,
                    coverArtId = null,
                    audioInfo = null,
                    replayGain = null,
                ),
            ),
            currentIndex = 0,
        )!!
        path.toFile().writeText(
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.buildJsonObject {
                    put(
                        "session",
                        kotlinx.serialization.json.Json.encodeToJsonElement(
                            PlaybackSessionSettings.serializer(),
                            session,
                        ),
                    )
                },
            ),
        )

        val store = DesktopCoreSettingsStore(path)
        assertEquals("remembered", store.loadLegacyPlaybackSession()?.currentTrack()?.id?.value)

        store.removeLegacyPlaybackSession()

        assertEquals(null, store.loadLegacyPlaybackSession())
    }

    @Test
    fun portablePreferencesRoundTripWithoutDeletingLegacyFields() {
        val directory = Files.createTempDirectory("naviamp-core-settings")
        val path = directory.resolve("settings.json")
        path.writeText("""{"connection":{"baseUrl":"https://music"},"legacy":"kept"}""")
        val store = DesktopCoreSettingsStore(path)

        store.saveInterfaceSettings(InterfaceSettings(showDesktopTooltips = false))
        store.savePlaybackSettings(
            PlaybackSettings(replayGainMode = ReplayGainMode.Track, crossfadeDurationSeconds = 8),
        )
        store.saveCacheSettings(CacheSettings(audioPrefetchDepth = 4))
        store.saveVisualizerSettings(VisualizerSettings("AudioBars"))

        assertEquals(false, store.loadInterfaceSettings().showDesktopTooltips)
        assertEquals(ReplayGainMode.Track, store.loadPlaybackSettings().replayGainMode)
        assertEquals(8, store.loadPlaybackSettings().crossfadeDurationSeconds)
        assertEquals(4, store.loadCacheSettings().audioPrefetchDepth)
        assertEquals("AudioBars", store.loadVisualizerSettings().selectedVisualizer)
        assertTrue(path.readText().contains("\"legacy\": \"kept\""))
        assertTrue(path.readText().contains("\"connection\""))
    }
}
