package app.naviamp.presentation

import app.naviamp.domain.playback.ReplayGainMode
import app.naviamp.domain.settings.AppBackgroundStyle
import app.naviamp.domain.settings.CacheSettings
import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.LyricsSourcePreference
import app.naviamp.domain.settings.TrackSwipeAction
import app.naviamp.domain.settings.normalized
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreLegacySettingsMigrationTest {
    @Test
    fun migratesLegacyFieldsIntoCoreOwnedDocuments() {
        val values = MemoryLegacySettingsValues(
            mutableMapOf(
                "interface_language" to "Spanish",
                "app_background_style" to "AlbumBlur",
                "album_blur_radius_dp" to "99",
                "swipe_queue_left" to "MoveToTop",
                "replay_gain_mode" to "Track",
                "crossfade_duration_seconds" to "7",
                "equalizer_enabled" to "true",
                "equalizer_band_0" to "1.5",
                "lyrics_search_order" to "[\"Download\",\"Provider\"]",
                "audio_prefetch_depth" to "3",
                "max_audio_cache_bytes" to "4096",
                "selected_visualizer" to "AudioBars",
                "recent_radio_streams" to "[]",
                "recent_internet_radio_stations" to "[]",
                "settings_sync_auto_export_enabled" to "true",
                "settings_sync_last_local_update_epoch_millis" to "123",
                "settings_sync_last_applied_update_epoch_millis" to "-8",
            ),
        )

        val migrated = migrateLegacyNaviampSettings(values, values)
        val stored = naviampCoreSettingsValueCatalog(values).storedSettings

        assertEquals(NaviampCoreSettingsMigrationSection.entries.toSet(), migrated)
        assertEquals(InterfaceLanguage.Spanish, stored.loadInterface().language)
        assertEquals(AppBackgroundStyle.AlbumBlur, stored.loadInterface().appBackgroundStyle)
        assertEquals(48, stored.loadInterface().albumBlurRadiusDp)
        assertEquals(TrackSwipeAction.MoveToTop, stored.loadInterface().trackSwipes.queueLeft)
        assertEquals(ReplayGainMode.Track, stored.loadPlayback().replayGainMode)
        assertEquals(7, stored.loadPlayback().crossfadeDurationSeconds)
        assertTrue(stored.loadPlayback().equalizer.enabled)
        assertEquals(1.5f, stored.loadPlayback().equalizer.bandsDb.first())
        assertEquals(
            listOf(
                LyricsSourcePreference.Download,
                LyricsSourcePreference.Provider,
                LyricsSourcePreference.Embedded,
            ),
            stored.loadPlayback().lyricsSearchOrder,
        )
        assertEquals(3, stored.loadCache().audioPrefetchDepth)
        assertEquals(
            CacheSettings(maxAudioCacheBytes = 4096L).normalized().maxAudioCacheBytes,
            stored.loadCache().maxAudioCacheBytes,
        )
        assertEquals("AudioBars", stored.loadVisualizer().selectedVisualizer)
        assertTrue(stored.loadSyncRuntime().autoExportEnabled)
        assertEquals(123L, stored.loadSyncRuntime().lastLocalUpdateEpochMillis)
        assertEquals(0L, stored.loadSyncRuntime().lastAppliedSyncUpdateEpochMillis)
    }

    @Test
    fun existingCoreDocumentsWinAndFreshStoresStayEmpty() {
        val values = MemoryLegacySettingsValues(mutableMapOf("interface_language" to "Spanish"))
        val catalog = naviampCoreSettingsValueCatalog(values)
        catalog.storedSettings.saveInterface(InterfaceSettings(language = InterfaceLanguage.English))

        val migrated = migrateLegacyNaviampSettings(values, values)

        assertFalse(NaviampCoreSettingsMigrationSection.Interface in migrated)
        assertEquals(InterfaceLanguage.English, catalog.storedSettings.loadInterface().language)

        val fresh = MemoryLegacySettingsValues()
        assertTrue(migrateLegacyNaviampSettings(fresh, fresh).isEmpty())
        assertTrue(fresh.entries.isEmpty())
    }
}

private class MemoryLegacySettingsValues(
    val entries: MutableMap<String, String> = mutableMapOf(),
) : NaviampCoreSettingsValueStore, NaviampCoreLegacySettingsValueStore {
    override fun contains(key: String): Boolean = key in entries
    override fun read(key: String): String? = entries[key]
    override fun write(key: String, value: String) {
        entries[key] = value
    }
}
