package app.naviamp.desktop.settings

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DesktopCoreSettingsSyncPortTest {
    @Test
    fun exposesOnlyNativeConfigurationPickerAndDocumentEffects() = runTest {
        val directory = Files.createTempDirectory("naviamp-desktop-settings-sync-port-test")
        val callerThread = Thread.currentThread().name
        var directoryPickerThread: String? = null
        var documentPickerThread: String? = null
        val settings = TestSettingsValueStore()
        val port = DesktopCoreSettingsSyncPort(
            settingsStore = settings,
            directoryPicker = DesktopDirectoryPicker { _, _ ->
                directoryPickerThread = Thread.currentThread().name
                directory.toString()
            },
            documentPicker = DesktopDocumentPicker { _, _ ->
                documentPickerThread = Thread.currentThread().name
                DesktopSettingsSyncFile.syncFile(directory).toString()
            },
            defaultDirectoryPath = { "unused" },
        )
        val document = SettingsSyncDocument(updatedAtEpochMillis = 200L)

        val selected = port.chooseDirectory(null, "Choose")
        port.saveConfiguration(NaviampCoreSettingsSyncConfiguration(selected, autoExportEnabled = true))
        val displayName = port.writeDocument(selected!!, document)
        val selectedDocument = port.chooseDocument(null, "Import")

        assertEquals("naviamp-settings.json", displayName)
        assertEquals(document, port.readDocument(selected))
        assertEquals(document, port.readDocumentFile(selectedDocument!!))
        assertEquals(selected, port.configuration().directoryPath)
        assertNotEquals(callerThread, directoryPickerThread)
        assertNotEquals(callerThread, documentPickerThread)

        DesktopSettingsSyncFile.syncFile(directory).deleteIfExists()
        directory.deleteIfExists()
    }

    private class TestSettingsValueStore : app.naviamp.presentation.NaviampCoreSettingsValueStore {
        private val values = mutableMapOf<String, String>()
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
    }
}
