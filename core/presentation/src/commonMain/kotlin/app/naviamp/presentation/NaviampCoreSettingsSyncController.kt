package app.naviamp.presentation

import app.naviamp.ui.NaviampSettingsSyncUi

/**
 * Document and picker boundary for settings sync.
 *
 * Core owns command interpretation and the published UI state. Implementations bridge the shared
 * settings-sync application service to platform document stores and native pickers.
 */
interface NaviampCoreSettingsSyncPort {
    fun current(): NaviampSettingsSyncUi
    suspend fun changeDirectory(path: String?): NaviampSettingsSyncUi
    suspend fun selectImportDirectory(path: String): NaviampSettingsSyncUi
    suspend fun changeAutoExport(enabled: Boolean): NaviampSettingsSyncUi
    suspend fun export(): NaviampSettingsSyncUi
    suspend fun import(): NaviampSettingsSyncUi
    suspend fun importFile(): NaviampSettingsSyncUi
    suspend fun chooseFolder(): NaviampSettingsSyncUi
    suspend fun importFolder(): NaviampSettingsSyncUi
    suspend fun exportFolder(): NaviampSettingsSyncUi
}

/** Owns every settings-sync UI command and publishes its resulting common state. */
class NaviampCoreSettingsSyncController(
    private val stateStore: NaviampCoreStateStore,
    private val port: NaviampCoreSettingsSyncPort,
) : NaviampCoreCommandController {
    init {
        publish(port.current())
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult =
        if (command is NaviampCoreCommand.SettingsSync) NaviampCoreImmediateCommandResult.Deferred
        else NaviampCoreImmediateCommandResult.Unhandled

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        val state = when (command) {
            is NaviampCoreCommand.SettingsSync.ChangeDirectory -> port.changeDirectory(command.path)
            is NaviampCoreCommand.SettingsSync.SelectImportDirectory -> port.selectImportDirectory(command.path)
            is NaviampCoreCommand.SettingsSync.ChangeAutoExport -> port.changeAutoExport(command.enabled)
            NaviampCoreCommand.SettingsSync.Export -> port.export()
            NaviampCoreCommand.SettingsSync.Import -> port.import()
            NaviampCoreCommand.SettingsSync.ImportFile -> port.importFile()
            NaviampCoreCommand.SettingsSync.ChooseFolder -> port.chooseFolder()
            NaviampCoreCommand.SettingsSync.ImportFolder -> port.importFolder()
            NaviampCoreCommand.SettingsSync.ExportFolder -> port.exportFolder()
            else -> return null
        }
        publish(state)
        return NaviampCoreCommandResult.Completed
    }

    private fun publish(settingsSync: NaviampSettingsSyncUi) {
        stateStore.update { state -> state.copy(settingsSync = settingsSync) }
    }
}
