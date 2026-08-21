package app.naviamp.presentation

import app.naviamp.domain.Genre
import app.naviamp.domain.radio.MaxRecentRadioStreams
import app.naviamp.domain.radio.genreRecentRadioStream
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettings
import app.naviamp.domain.settings.SavedTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NaviampCoreSettingsValueStoreTest {
    @Test
    fun ownsPortableSerializationDefaultsAndNormalization() {
        val values = MemorySettingsValues()
        val catalog = naviampCoreSettingsValueCatalog(values)
        val settings = catalog.storedSettings

        assertEquals(InterfaceSettings(), settings.loadInterface())
        settings.saveInterface(InterfaceSettings(albumBlurRadiusDp = 999))
        assertEquals(48, settings.loadInterface().albumBlurRadiusDp)
        catalog.savePlayback(PlaybackSettings(crossfadeDurationSeconds = 999))
        assertEquals(999, settings.loadPlayback().crossfadeDurationSeconds)

        settings.saveRecentRadioStreams(
            (1..55).map { index -> genreRecentRadioStream(Genre("Genre $index")) },
        )
        assertEquals(MaxRecentRadioStreams, settings.loadRecentRadioStreams().size)
        assertEquals("genre:Genre 50", settings.loadRecentRadioStreams().last().id)

        val session = genreRecentRadioStream(Genre("Ambient")).copy(
            id = "genre:Ambient:session:123",
            sourceId = "server-a",
            startedAtEpochMillis = 123L,
            sessionTracks = listOf(SavedTrack("track-1", "Track", artistName = "Artist")),
        )
        settings.saveRecentRadioStreams(listOf(session))
        assertEquals(session, settings.loadRecentRadioStreams().single())

        values.entries["naviamp.interface"] = "not-json"
        assertEquals(InterfaceSettings(), settings.loadInterface())
        assertFalse(values.entries.values.any { "NSUserDefaults" in it })
    }
}

private class MemorySettingsValues : NaviampCoreSettingsValueStore {
    val entries = mutableMapOf<String, String>()
    override fun read(key: String): String? = entries[key]
    override fun write(key: String, value: String) {
        entries[key] = value
    }
}
