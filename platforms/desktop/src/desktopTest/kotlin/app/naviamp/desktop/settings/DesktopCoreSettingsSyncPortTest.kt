package app.naviamp.desktop.settings

import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopCoreSettingsSyncPortTest {
    @Test
    fun exposesOnlyNativeConfigurationPickerAndDocumentEffects() = runTest {
        val directory = Files.createTempDirectory("naviamp-desktop-settings-sync-port-test")
        var configuration = NaviampCoreSettingsSyncConfiguration()
        val port = DesktopCoreSettingsSyncPort(
            configurationState = { configuration },
            saveConfigurationState = { configuration = it },
            directoryPicker = DesktopDirectoryPicker { _, _ -> directory.toString() },
            defaultDirectoryPath = { "unused" },
        )
        val document = SettingsSyncDocument(updatedAtEpochMillis = 200L)

        val selected = port.chooseDirectory(null, "Choose")
        port.saveConfiguration(NaviampCoreSettingsSyncConfiguration(selected, autoExportEnabled = true))
        val displayName = port.writeDocument(selected!!, document)

        assertEquals("naviamp-settings.json", displayName)
        assertEquals(document, port.readDocument(selected))
        assertEquals(selected, port.configuration().directoryPath)

        DesktopSettingsSyncFile.syncFile(directory).deleteIfExists()
        directory.deleteIfExists()
    }
}
