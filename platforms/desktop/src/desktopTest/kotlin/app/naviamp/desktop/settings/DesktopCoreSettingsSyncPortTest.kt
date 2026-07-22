package app.naviamp.desktop.settings

import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import java.nio.file.Files
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DesktopCoreSettingsSyncPortTest {
    @Test
    fun bridgesCorePolicyToNativeSelectionAndDirectoryDocuments() = runTest {
        val directory = Files.createTempDirectory("naviamp-desktop-settings-sync-test")
        var configuration = DesktopSettingsSyncConfiguration()
        var runtime = SettingsSyncRuntimeState()
        var applied: SettingsSyncDocument? = null
        val controller = NaviampSettingsSyncController(
            deviceId = "desktop-test",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = { 100L },
            snapshot = {
                SettingsSyncLocalSnapshot(
                    interfaceSettings = InterfaceSettings(showDesktopTooltips = false),
                )
            },
            applyDocument = { applied = it },
        )
        val port = DesktopCoreSettingsSyncPort(
            controller = controller,
            configuration = { configuration },
            saveConfiguration = {
                configuration = it
                runtime = runtime.copy(autoExportEnabled = it.autoExportEnabled)
            },
            directoryPicker = DesktopDirectoryPicker { _, _ -> directory.toString() },
            defaultDirectory = { "unused" },
        )

        val exported = port.exportFolder()

        assertEquals(directory.toString(), exported.directoryPath)
        assertEquals("Settings exported to naviamp-settings.json.", exported.status)
        assertTrue(DesktopSettingsSyncFile.syncFile(directory).toFile().isFile)
        assertFalse(DesktopSettingsSyncFile.read(directory)!!.preferences.interfaceSettings.showDesktopTooltips)

        val newer = SettingsSyncDocument(updatedAtEpochMillis = 200L)
        DesktopSettingsSyncFile.write(directory, newer)
        val imported = port.importFile()

        assertEquals(newer, applied)
        assertEquals("Settings imported.", imported.status)

        val auto = port.changeAutoExport(true)
        assertTrue(auto.autoExportEnabled)
        assertEquals("Settings auto-exported to naviamp-settings.json.", auto.status)

        DesktopSettingsSyncFile.syncFile(directory).deleteIfExists()
        directory.deleteIfExists()
    }

    @Test
    fun autoExportCannotBeEnabledWithoutASelectedLocation() = runTest {
        var configuration = DesktopSettingsSyncConfiguration()
        var runtime = SettingsSyncRuntimeState()
        val controller = NaviampSettingsSyncController(
            deviceId = "desktop-test",
            state = { runtime },
            saveState = { runtime = it },
            nowEpochMillis = { 1L },
            snapshot = { SettingsSyncLocalSnapshot() },
            applyDocument = {},
        )
        val port = DesktopCoreSettingsSyncPort(
            controller = controller,
            configuration = { configuration },
            saveConfiguration = {
                configuration = it
                runtime = runtime.copy(autoExportEnabled = it.autoExportEnabled)
            },
        )

        val state = port.changeAutoExport(true)

        assertFalse(state.autoExportEnabled)
        assertEquals("Auto-sync disabled.", state.status)
    }
}
