package app.naviamp.desktop.settings

import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.app.settingsSyncAutoExportEnabled
import app.naviamp.app.settingsSyncAutoExportStatus
import app.naviamp.app.settingsSyncExportStatus
import app.naviamp.app.settingsSyncLocationStatus
import app.naviamp.app.settingsSyncMissingLocationStatus
import app.naviamp.app.settingsSyncReconciliationStatus
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.presentation.NaviampCoreSettingsSyncPort
import app.naviamp.ui.NaviampSettingsSyncUi
import java.nio.file.Path

data class DesktopSettingsSyncConfiguration(
    val directoryPath: String? = null,
    val autoExportEnabled: Boolean = false,
) {
    fun normalized(): DesktopSettingsSyncConfiguration {
        val directory = directoryPath?.trim()?.takeIf(String::isNotEmpty)
        return copy(
            directoryPath = directory,
            autoExportEnabled = settingsSyncAutoExportEnabled(autoExportEnabled, directory != null),
        )
    }
}

/**
 * Desktop document/picker implementation of Core's settings-sync effect.
 *
 * Snapshot, timestamp, merge, import, export, and status policy are delegated to shared owners.
 * [saveConfiguration] must persist auto-export into the same runtime state read by [controller].
 */
class DesktopCoreSettingsSyncPort(
    private val controller: NaviampSettingsSyncController,
    private val configuration: () -> DesktopSettingsSyncConfiguration,
    private val saveConfiguration: (DesktopSettingsSyncConfiguration) -> Unit,
    private val directoryPicker: DesktopDirectoryPicker = DesktopNativeDirectoryPicker(),
    private val defaultDirectory: () -> String = { System.getProperty("user.home") },
    private val available: Boolean = true,
) : NaviampCoreSettingsSyncPort {
    private var status: String? = null

    override fun current(): NaviampSettingsSyncUi = ui()

    override suspend fun changeDirectory(path: String?): NaviampSettingsSyncUi {
        val current = configuration()
        save(current.copy(directoryPath = path))
        status = settingsSyncLocationStatus(configuration().directoryPath != null)
        return ui()
    }

    override suspend fun selectImportDirectory(path: String): NaviampSettingsSyncUi {
        save(configuration().copy(directoryPath = path))
        return import()
    }

    override suspend fun changeAutoExport(enabled: Boolean): NaviampSettingsSyncUi {
        save(configuration().copy(autoExportEnabled = enabled))
        status = settingsSyncAutoExportStatus(configuration().autoExportEnabled)
        if (configuration().autoExportEnabled) write(controller.autoExport()?.documentToWrite, auto = true)
        return ui()
    }

    override suspend fun export(): NaviampSettingsSyncUi {
        write(controller.exportCurrent(markChanged = true).documentToWrite, auto = false)
        return ui()
    }

    override suspend fun import(): NaviampSettingsSyncUi {
        val directory = configuredDirectory() ?: return missingDirectory()
        val document = DesktopSettingsSyncDocumentStore(directory).read()
            ?: error("No settings sync file found in that folder.")
        val result = controller.applySyncedDocument(document)
        status = settingsSyncReconciliationStatus(result)
        return ui()
    }

    override suspend fun importFile(): NaviampSettingsSyncUi = selectAndImport()

    override suspend fun chooseFolder(): NaviampSettingsSyncUi {
        val selected = pickDirectory("Choose settings sync folder") ?: return ui()
        return changeDirectory(selected)
    }

    override suspend fun importFolder(): NaviampSettingsSyncUi = selectAndImport()

    override suspend fun exportFolder(): NaviampSettingsSyncUi {
        val selected = pickDirectory("Export Naviamp settings") ?: return ui()
        save(configuration().copy(directoryPath = selected))
        return export()
    }

    private suspend fun selectAndImport(): NaviampSettingsSyncUi {
        val selected = pickDirectory("Import Naviamp settings") ?: return ui()
        return selectImportDirectory(selected)
    }

    private fun write(document: SettingsSyncDocument?, auto: Boolean) {
        document ?: return
        val directory = configuredDirectory()
        if (directory == null) {
            status = settingsSyncMissingLocationStatus()
            return
        }
        val store = DesktopSettingsSyncDocumentStore(directory)
        store.write(document)
        controller.documentWritten(document)
        status = settingsSyncExportStatus(store.displayName, automatic = auto)
    }

    private fun configuredDirectory(): Path? = configuration().normalized().directoryPath?.let(Path::of)

    private fun pickDirectory(title: String): String? = directoryPicker.chooseDirectory(
        currentPath = configuration().normalized().directoryPath ?: defaultDirectory(),
        title = title,
    )

    private fun save(value: DesktopSettingsSyncConfiguration) {
        saveConfiguration(value.normalized())
    }

    private fun missingDirectory(): NaviampSettingsSyncUi {
        status = settingsSyncMissingLocationStatus()
        return ui()
    }

    private fun ui(): NaviampSettingsSyncUi {
        val value = configuration().normalized()
        return NaviampSettingsSyncUi(
            directoryPath = value.directoryPath,
            autoExportEnabled = value.autoExportEnabled,
            status = status,
            available = available,
        )
    }
}
