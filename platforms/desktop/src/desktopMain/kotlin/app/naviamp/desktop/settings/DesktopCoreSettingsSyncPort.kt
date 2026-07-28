package app.naviamp.desktop.settings

import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreSettingsSyncPort
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Desktop native effects for Core-owned settings-sync transactions. */
class DesktopCoreSettingsSyncPort(
    private val configurationState: () -> NaviampCoreSettingsSyncConfiguration,
    private val saveConfigurationState: (NaviampCoreSettingsSyncConfiguration) -> Unit,
    private val directoryPicker: DesktopDirectoryPicker = DesktopNativeDirectoryPicker(),
    private val documentPicker: DesktopDocumentPicker = DesktopNativeDocumentPicker(),
    private val defaultDirectoryPath: () -> String = { System.getProperty("user.home") },
    override val available: Boolean = true,
) : NaviampCoreSettingsSyncPort {
    override fun configuration() = configurationState()

    override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
        saveConfigurationState(configuration)
    }

    override suspend fun readDocument(directoryPath: String) =
        DesktopSettingsSyncDocumentStore(Path.of(directoryPath)).read()

    override suspend fun readDocumentFile(filePath: String) =
        DesktopSettingsSyncFile.readFile(Path.of(filePath))

    override suspend fun writeDocument(
        directoryPath: String,
        document: app.naviamp.domain.settings.SettingsSyncDocument,
    ): String = DesktopSettingsSyncDocumentStore(Path.of(directoryPath)).let { store ->
        store.write(document)
        store.displayName
    }

    override suspend fun chooseDirectory(currentPath: String?, title: String): String? = withContext(Dispatchers.IO) {
        directoryPicker.chooseDirectory(currentPath ?: defaultDirectory(), title)
    }

    override suspend fun chooseDocument(currentPath: String?, title: String): String? = withContext(Dispatchers.IO) {
        documentPicker.chooseDocument(currentPath ?: defaultDirectory(), title)
    }

    override fun defaultDirectory() = defaultDirectoryPath()
}
