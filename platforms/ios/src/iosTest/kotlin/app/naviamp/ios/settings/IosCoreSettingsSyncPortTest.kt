package app.naviamp.ios.settings

import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreSettingsValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosCoreSettingsSyncPortTest {
    @Test
    fun persistsNormalizedCoreConfigurationWithoutOwningSettingsPolicy() {
        val values = MemorySettingsValueStore()
        val port = IosCoreSettingsSyncPort(values) { null }

        assertNull(port.configuration().directoryPath)
        port.saveConfiguration(
            NaviampCoreSettingsSyncConfiguration(
                directoryPath = "  ios-bookmark:abc  ",
                autoExportEnabled = true,
            ),
        )

        assertEquals("ios-bookmark:abc", port.configuration().directoryPath)
        assertEquals(true, port.configuration().autoExportEnabled)
    }
}

private class MemorySettingsValueStore : NaviampCoreSettingsValueStore {
    private val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }
}
