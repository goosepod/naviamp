package app.naviamp.presentation

import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.domain.settings.SettingsSyncPreferences
import app.naviamp.domain.settings.SettingsSyncRuntimeState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaviampCoreSettingsSyncControllerTest {
    @Test
    fun coreOwnsDirectoryExportImportAndStatusPolicy() = runTest {
        val store = NaviampCoreStateStore()
        val port = RecordingSettingsSyncPort()
        var runtime = SettingsSyncRuntimeState()
        var applied: SettingsSyncDocument? = null
        var snapshot = SettingsSyncLocalSnapshot()
        var publishedSnapshot: SettingsSyncLocalSnapshot? = null
        val controller = NaviampCoreSettingsSyncController(
            store,
            NaviampCoreSettingsSyncServices(
                controller = NaviampSettingsSyncController(
                    deviceId = "test",
                    state = { runtime },
                    saveState = { runtime = it },
                    nowEpochMillis = { 100L },
                    snapshot = { snapshot },
                    applyDocument = {
                        applied = it
                        snapshot = snapshot.copy(interfaceSettings = it.preferences.interfaceSettings)
                    },
                ),
                port = port,
            ),
            onDocumentApplied = { publishedSnapshot = it },
        )

        controller.execute(NaviampCoreCommand.SettingsSync.ChangeDirectory("/sync"))
        controller.execute(NaviampCoreCommand.SettingsSync.ChangeAutoExport(true))
        controller.execute(NaviampCoreCommand.SettingsSync.Export)
        port.document = SettingsSyncDocument(
            updatedAtEpochMillis = 200L,
            preferences = SettingsSyncPreferences(
                interfaceSettings = InterfaceSettings(showDesktopTooltips = false),
            ),
        )
        controller.execute(NaviampCoreCommand.SettingsSync.Import)

        assertEquals(listOf("write:/sync", "write:/sync", "read:/sync"), port.operations)
        assertEquals(port.document, applied)
        assertEquals("Settings imported.", store.state.value.settingsSync.status)
        assertEquals("/sync", store.state.value.settingsSync.directoryPath)
        assertTrue(store.state.value.settingsSync.autoExportEnabled)
        assertTrue(runtime.autoExportEnabled)
        assertEquals(false, store.state.value.shell.general.interfaceSettings.showDesktopTooltips)
        assertEquals(snapshot, publishedSnapshot)
    }

    @Test
    fun corePreventsAutoExportWithoutLocationAndOwnsNativePickerSequences() = runTest {
        val store = NaviampCoreStateStore()
        val port = RecordingSettingsSyncPort().apply { selectedDirectory = "/picked" }
        var runtime = SettingsSyncRuntimeState()
        val controller = NaviampCoreSettingsSyncController(
            store,
            NaviampCoreSettingsSyncServices(
                controller = NaviampSettingsSyncController(
                    deviceId = "test",
                    state = { runtime },
                    saveState = { runtime = it },
                    nowEpochMillis = { 100L },
                    snapshot = { SettingsSyncLocalSnapshot() },
                    applyDocument = {},
                ),
                port = port,
            ),
        )

        controller.execute(NaviampCoreCommand.SettingsSync.ChangeAutoExport(true))
        assertFalse(store.state.value.settingsSync.autoExportEnabled)
        assertEquals("Auto-sync disabled.", store.state.value.settingsSync.status)

        controller.execute(NaviampCoreCommand.SettingsSync.ExportFolder)
        assertEquals("/picked", store.state.value.settingsSync.directoryPath)
        assertEquals("Settings exported to naviamp-settings.json.", store.state.value.settingsSync.status)
        assertTrue(port.operations.contains("pick:Export Naviamp settings"))
    }

    @Test
    fun localPortableSettingChangesAutoExportWhenConfigured() = runTest {
        val store = NaviampCoreStateStore()
        val port = RecordingSettingsSyncPort()
        var runtime = SettingsSyncRuntimeState()
        val controller = NaviampCoreSettingsSyncController(
            store,
            NaviampCoreSettingsSyncServices(
                controller = NaviampSettingsSyncController(
                    deviceId = "test",
                    state = { runtime },
                    saveState = { runtime = it },
                    nowEpochMillis = { 100L },
                    snapshot = { SettingsSyncLocalSnapshot() },
                    applyDocument = {},
                ),
                port = port,
            ),
        )
        controller.execute(NaviampCoreCommand.SettingsSync.ChangeDirectory("/sync"))
        controller.execute(NaviampCoreCommand.SettingsSync.ChangeAutoExport(true))
        port.operations.clear()

        controller.localSettingsChanged()

        assertEquals(listOf("write:/sync"), port.operations)
        assertEquals("Settings auto-exported to naviamp-settings.json.", store.state.value.settingsSync.status)
        assertEquals(101L, runtime.lastLocalUpdateEpochMillis)
    }

    @Test
    fun importFileUsesDocumentPickerWithoutChangingTheSyncDirectory() = runTest {
        val store = NaviampCoreStateStore()
        val port = RecordingSettingsSyncPort().apply {
            selectedDocument = "/backup/naviamp-settings.json"
            document = SettingsSyncDocument(updatedAtEpochMillis = 200L)
        }
        var runtime = SettingsSyncRuntimeState()
        val controller = NaviampCoreSettingsSyncController(
            store,
            NaviampCoreSettingsSyncServices(
                controller = NaviampSettingsSyncController(
                    deviceId = "test",
                    state = { runtime },
                    saveState = { runtime = it },
                    nowEpochMillis = { 100L },
                    snapshot = { SettingsSyncLocalSnapshot() },
                    applyDocument = {},
                ),
                port = port,
            ),
        )

        controller.execute(NaviampCoreCommand.SettingsSync.ImportFile)

        assertEquals(
            listOf("pick-file:Import Naviamp settings", "read-file:/backup/naviamp-settings.json"),
            port.operations,
        )
        assertEquals(null, store.state.value.settingsSync.directoryPath)
        assertEquals("Settings imported.", store.state.value.settingsSync.status)
    }
}

private class RecordingSettingsSyncPort : NaviampCoreSettingsSyncPort {
    private var saved = NaviampCoreSettingsSyncConfiguration()
    val operations = mutableListOf<String>()
    var document: SettingsSyncDocument? = null
    var selectedDirectory: String? = null
    var selectedDocument: String? = null

    override fun configuration() = saved

    override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
        saved = configuration
    }

    override suspend fun readDocument(directoryPath: String): SettingsSyncDocument? {
        operations += "read:$directoryPath"
        return document
    }

    override suspend fun readDocumentFile(filePath: String): SettingsSyncDocument? {
        operations += "read-file:$filePath"
        return document
    }

    override suspend fun writeDocument(directoryPath: String, document: SettingsSyncDocument): String {
        operations += "write:$directoryPath"
        this.document = document
        return "naviamp-settings.json"
    }

    override suspend fun chooseDirectory(currentPath: String?, title: String): String? {
        operations += "pick:$title"
        return selectedDirectory
    }

    override suspend fun chooseDocument(currentPath: String?, title: String): String? {
        operations += "pick-file:$title"
        return selectedDocument
    }

    override fun defaultDirectory() = "/home"
    override val available = true
}
