package app.naviamp.presentation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.app.settingsSyncAutoExportEnabled
import app.naviamp.app.settingsSyncAutoExportStatus
import app.naviamp.app.settingsSyncExportStatus
import app.naviamp.app.settingsSyncLocationStatus
import app.naviamp.app.settingsSyncMissingLocationStatus
import app.naviamp.app.settingsSyncReconciliationStatus
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncLocalSnapshot
import app.naviamp.ui.NaviampSettingsSyncUi
import app.naviamp.ui.naviampVisualizerFromName

data class NaviampCoreSettingsSyncConfiguration(
    val directoryPath: String? = null,
    val autoExportEnabled: Boolean = false,
) {
    fun normalized(): NaviampCoreSettingsSyncConfiguration {
        val directory = directoryPath?.trim()?.takeIf(String::isNotEmpty)
        return copy(
            directoryPath = directory,
            autoExportEnabled = settingsSyncAutoExportEnabled(autoExportEnabled, directory != null),
        )
    }
}

/** Core-owned serialization over a host's opaque settings value effect. */
class NaviampCoreSettingsSyncConfigurationStore(
    private val values: NaviampCoreSettingsValueStore,
    private val legacy: NaviampCoreLegacySettingsValueStore? = values as? NaviampCoreLegacySettingsValueStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun load(): NaviampCoreSettingsSyncConfiguration {
        val directory = values.read(KeySettingsSyncDirectory)
            ?: values.read(LegacyIosSettingsSyncDirectoryKey)
        val autoExport = (
            values.read(KeySettingsSyncAutoExport)
                ?: values.read(LegacyIosSettingsSyncAutoExportKey)
            )?.toBooleanStrictOrNull()
        if (directory != null || autoExport != null) {
            return NaviampCoreSettingsSyncConfiguration(directory, autoExport ?: false).normalized()
        }
        val old = legacy?.let { legacyValues ->
            listOf(LegacyDesktopSettingsSyncKey, LegacySettingsSyncConfigurationKey)
                .firstNotNullOfOrNull { key ->
                    legacyValues.read(key)
                        ?.let { encoded -> runCatching { json.parseToJsonElement(encoded).jsonObject }.getOrNull() }
                }
        }
        return NaviampCoreSettingsSyncConfiguration(
            directoryPath = old?.get("directoryPath")?.jsonPrimitive?.contentOrNull,
            autoExportEnabled = old?.get("autoExportEnabled")?.jsonPrimitive?.booleanOrNull ?: false,
        ).normalized()
    }

    fun save(configuration: NaviampCoreSettingsSyncConfiguration) {
        val normalized = configuration.normalized()
        values.write(KeySettingsSyncDirectory, normalized.directoryPath.orEmpty())
        values.write(KeySettingsSyncAutoExport, normalized.autoExportEnabled.toString())
    }
}

private const val KeySettingsSyncDirectory = "naviamp.sync.directory"
private const val KeySettingsSyncAutoExport = "naviamp.sync.autoExport"
private const val LegacyDesktopSettingsSyncKey = "settingsSync"
private const val LegacySettingsSyncConfigurationKey = "settingsSyncConfiguration"
private const val LegacyIosSettingsSyncDirectoryKey = "settingsSyncDirectoryReference"
private const val LegacyIosSettingsSyncAutoExportKey = "settingsSyncAutoExportEnabled"

/** Native settings-document effects. Core owns all workflow, status, and presentation decisions. */
interface NaviampCoreSettingsSyncPort {
    fun configuration(): NaviampCoreSettingsSyncConfiguration
    fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration)
    suspend fun readDocument(directoryPath: String): SettingsSyncDocument?
    suspend fun readDocumentFile(filePath: String): SettingsSyncDocument?
    suspend fun writeDocument(directoryPath: String, document: SettingsSyncDocument): String
    suspend fun chooseDirectory(currentPath: String?, title: String): String?
    suspend fun chooseDocument(currentPath: String?, title: String): String?
    fun defaultDirectory(): String
    val available: Boolean
}

data class NaviampCoreSettingsSyncServices(
    val controller: NaviampSettingsSyncController,
    val port: NaviampCoreSettingsSyncPort,
)

/** Owns every settings-sync transaction and publishes its common state. */
class NaviampCoreSettingsSyncController(
    private val stateStore: NaviampCoreStateStore,
    private val services: NaviampCoreSettingsSyncServices,
    private val onDocumentApplied: suspend (SettingsSyncLocalSnapshot) -> Unit = {},
) : NaviampCoreCommandController {
    private var status: String? = null

    init {
        services.controller.setAutoExportEnabled(configuration().autoExportEnabled)
        publish()
    }

    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult =
        if (command is NaviampCoreCommand.SettingsSync) NaviampCoreImmediateCommandResult.Deferred
        else NaviampCoreImmediateCommandResult.Unhandled

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        when (command) {
            is NaviampCoreCommand.SettingsSync.ChangeDirectory -> changeDirectory(command.path)
            is NaviampCoreCommand.SettingsSync.SelectImportDirectory -> selectImportDirectory(command.path)
            is NaviampCoreCommand.SettingsSync.ChangeAutoExport -> changeAutoExport(command.enabled)
            NaviampCoreCommand.SettingsSync.Export -> export()
            NaviampCoreCommand.SettingsSync.Import -> import()
            NaviampCoreCommand.SettingsSync.ImportFile -> selectAndImportFile()
            is NaviampCoreCommand.SettingsSync.ImportFilePath -> importFile(command.path)
            NaviampCoreCommand.SettingsSync.ChooseFolder -> chooseFolder()
            NaviampCoreCommand.SettingsSync.ImportFolder -> selectAndImport()
            NaviampCoreCommand.SettingsSync.ExportFolder -> exportFolder()
            else -> return null
        }
        publish()
        return NaviampCoreCommandResult.Completed
    }

    suspend fun localSettingsChanged() {
        services.controller.markLocalChanged()
        write(services.controller.autoExport()?.documentToWrite, automatic = true)
        publish()
    }

    private fun changeDirectory(path: String?) {
        save(configuration().copy(directoryPath = path))
        status = settingsSyncLocationStatus(configuration().directoryPath != null)
    }

    private suspend fun selectImportDirectory(path: String) {
        save(configuration().copy(directoryPath = path))
        import()
    }

    private suspend fun changeAutoExport(enabled: Boolean) {
        save(configuration().copy(autoExportEnabled = enabled))
        services.controller.setAutoExportEnabled(configuration().autoExportEnabled)
        status = settingsSyncAutoExportStatus(configuration().autoExportEnabled)
        if (configuration().autoExportEnabled) write(services.controller.autoExport()?.documentToWrite, automatic = true)
    }

    private suspend fun export() {
        write(services.controller.exportCurrent(markChanged = true).documentToWrite, automatic = false)
    }

    private suspend fun import() {
        val directory = configuredDirectory() ?: return missingDirectory()
        val document = services.port.readDocument(directory)
            ?: error("No settings sync file found in that folder.")
        status = settingsSyncReconciliationStatus(services.controller.applySyncedDocument(document))
        publishAppliedSnapshot(services.controller.localSnapshot())
    }

    private suspend fun chooseFolder() {
        val selected = pickDirectory("Choose settings sync folder") ?: return
        changeDirectory(selected)
    }

    private suspend fun selectAndImport() {
        val selected = pickDirectory("Import Naviamp settings") ?: return
        selectImportDirectory(selected)
    }

    private suspend fun selectAndImportFile() {
        val selected = services.port.chooseDocument(
            currentPath = configuredDirectory() ?: services.port.defaultDirectory(),
            title = "Import Naviamp settings",
        ) ?: return
        importFile(selected)
    }

    private suspend fun importFile(path: String) {
        val document = services.port.readDocumentFile(path)
            ?: error("The selected file is not a Naviamp settings document.")
        status = settingsSyncReconciliationStatus(services.controller.applySyncedDocument(document))
        publishAppliedSnapshot(services.controller.localSnapshot())
    }

    private suspend fun exportFolder() {
        val selected = pickDirectory("Export Naviamp settings") ?: return
        save(configuration().copy(directoryPath = selected))
        export()
    }

    private suspend fun write(document: SettingsSyncDocument?, automatic: Boolean) {
        document ?: return
        val directory = configuredDirectory()
        if (directory == null) {
            missingDirectory()
            return
        }
        val displayName = services.port.writeDocument(directory, document)
        services.controller.documentWritten(document)
        status = settingsSyncExportStatus(displayName, automatic)
    }

    private suspend fun pickDirectory(title: String): String? = services.port.chooseDirectory(
        currentPath = configuredDirectory() ?: services.port.defaultDirectory(),
        title = title,
    )

    private fun configuration() = services.port.configuration().normalized()

    private fun configuredDirectory() = configuration().directoryPath

    private fun save(configuration: NaviampCoreSettingsSyncConfiguration) {
        services.port.saveConfiguration(configuration.normalized())
    }

    private fun missingDirectory() {
        status = settingsSyncMissingLocationStatus()
    }

    private suspend fun publishAppliedSnapshot(snapshot: SettingsSyncLocalSnapshot) {
        stateStore.updateShell { shell ->
            shell.copy(
                general = shell.general.copy(interfaceSettings = snapshot.interfaceSettings),
                playback = shell.playback.copy(settings = snapshot.playback),
                shellChrome = shell.shellChrome.copy(
                    selectedVisualizer = naviampVisualizerFromName(snapshot.visualizer.selectedVisualizer),
                ),
            )
        }
        onDocumentApplied(snapshot)
    }

    private fun publish() {
        val configuration = configuration()
        stateStore.update { state ->
            state.copy(
                settingsSync = NaviampSettingsSyncUi(
                    directoryPath = configuration.directoryPath,
                    autoExportEnabled = configuration.autoExportEnabled,
                    status = status,
                    available = services.port.available,
                ),
            )
        }
    }
}
