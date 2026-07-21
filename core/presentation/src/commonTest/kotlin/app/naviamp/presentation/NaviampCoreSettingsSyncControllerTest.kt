package app.naviamp.presentation

import app.naviamp.ui.NaviampSettingsSyncUi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NaviampCoreSettingsSyncControllerTest {
    @Test
    fun routesEverySyncIntentThroughCoreAndPublishesTheResult() = runTest {
        val store = NaviampCoreStateStore()
        val port = RecordingSettingsSyncPort()
        val controller = NaviampCoreSettingsSyncController(store, port)

        controller.execute(NaviampCoreCommand.SettingsSync.ChangeDirectory("/sync"))
        controller.execute(NaviampCoreCommand.SettingsSync.ChangeAutoExport(true))
        controller.execute(NaviampCoreCommand.SettingsSync.Export)
        controller.execute(NaviampCoreCommand.SettingsSync.ImportFile)

        assertEquals(listOf("directory:/sync", "auto:true", "export", "import-file"), port.operations)
        assertEquals("import-file", store.state.value.settingsSync.status)
    }
}

private class RecordingSettingsSyncPort : NaviampCoreSettingsSyncPort {
    val operations = mutableListOf<String>()

    override fun current() = state("initial")
    override suspend fun changeDirectory(path: String?) = record("directory:$path")
    override suspend fun selectImportDirectory(path: String) = record("import-directory:$path")
    override suspend fun changeAutoExport(enabled: Boolean) = record("auto:$enabled")
    override suspend fun export() = record("export")
    override suspend fun import() = record("import")
    override suspend fun importFile() = record("import-file")
    override suspend fun chooseFolder() = record("choose-folder")
    override suspend fun importFolder() = record("import-folder")
    override suspend fun exportFolder() = record("export-folder")

    private fun record(operation: String): NaviampSettingsSyncUi {
        operations += operation
        return state(operation)
    }

    private fun state(status: String) = NaviampSettingsSyncUi(status = status, available = true)
}
