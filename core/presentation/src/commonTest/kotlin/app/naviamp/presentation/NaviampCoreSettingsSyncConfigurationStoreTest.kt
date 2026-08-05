package app.naviamp.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreSettingsSyncConfigurationStoreTest {
    @Test
    fun ownsNormalizationAndPortableSerialization() {
        val values = SyncConfigurationMemoryValues()
        val store = NaviampCoreSettingsSyncConfigurationStore(values)

        store.save(NaviampCoreSettingsSyncConfiguration("  /Music/Sync  ", autoExportEnabled = true))

        assertEquals(NaviampCoreSettingsSyncConfiguration("/Music/Sync", true), store.load())
    }

    @Test
    fun readsTheSupersededDesktopAggregateValueAndFailsClosedWithoutALocation() {
        val migrated = NaviampCoreSettingsSyncConfigurationStore(
            SyncConfigurationMemoryValues(
                mutableMapOf(
                    "settingsSyncConfiguration" to
                        """{"directoryPath":" /legacy ","autoExportEnabled":true}""",
                ),
            ),
        ).load()
        val missing = NaviampCoreSettingsSyncConfigurationStore(
            SyncConfigurationMemoryValues(
                mutableMapOf(
                    "settingsSyncConfiguration" to """{"autoExportEnabled":true}""",
                ),
            ),
        ).load()

        assertEquals("/legacy", migrated.directoryPath)
        assertTrue(migrated.autoExportEnabled)
        assertFalse(missing.autoExportEnabled)
    }

    @Test
    fun prefersTheActualDesktopSyncLocationOverAStaleAggregateConfiguration() {
        val configuration = NaviampCoreSettingsSyncConfigurationStore(
            SyncConfigurationMemoryValues(
                mutableMapOf(
                    "settingsSync" to
                        """{"directoryPath":"/Users/test/syncthing","autoExportEnabled":true}""",
                    "settingsSyncConfiguration" to
                        """{"directoryPath":"/Users/test/Documents","autoExportEnabled":true}""",
                ),
            ),
        ).load()

        assertEquals("/Users/test/syncthing", configuration.directoryPath)
        assertTrue(configuration.autoExportEnabled)
    }

    @Test
    fun readsTheSupersededIosValues() {
        val configuration = NaviampCoreSettingsSyncConfigurationStore(
            SyncConfigurationMemoryValues(
                mutableMapOf(
                    "settingsSyncDirectoryReference" to "ios-bookmark:retained",
                    "settingsSyncAutoExportEnabled" to "true",
                ),
            ),
        ).load()

        assertEquals("ios-bookmark:retained", configuration.directoryPath)
        assertTrue(configuration.autoExportEnabled)
    }
}

private class SyncConfigurationMemoryValues(
    private val entries: MutableMap<String, String> = mutableMapOf(),
) : NaviampCoreSettingsValueStore, NaviampCoreLegacySettingsValueStore {
    override fun contains(key: String): Boolean = key in entries
    override fun read(key: String): String? = entries[key]
    override fun write(key: String, value: String) {
        entries[key] = value
    }
}
