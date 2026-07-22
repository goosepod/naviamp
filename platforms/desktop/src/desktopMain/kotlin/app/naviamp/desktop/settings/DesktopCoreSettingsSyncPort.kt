package app.naviamp.desktop.settings

import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreSettingsSyncPort
import java.nio.file.Path

/** Desktop native effects for Core-owned settings-sync transactions. */
class DesktopCoreSettingsSyncPort(
    private val configurationState: () -> NaviampCoreSettingsSyncConfiguration,
    private val saveConfigurationState: (NaviampCoreSettingsSyncConfiguration) -> Unit,
    private val directoryPicker: DesktopDirectoryPicker = DesktopNativeDirectoryPicker(),
    private val defaultDirectoryPath: () -> String = { System.getProperty("user.home") },
    override val available: Boolean = true,
) : NaviampCoreSettingsSyncPort {
    override fun configuration() = configurationState()

    override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
        saveConfigurationState(configuration)
    }

    override suspend fun readDocument(directoryPath: String) =
        DesktopSettingsSyncDocumentStore(Path.of(directoryPath)).read()

    override suspend fun writeDocument(
        directoryPath: String,
        document: app.naviamp.domain.settings.SettingsSyncDocument,
    ): String = DesktopSettingsSyncDocumentStore(Path.of(directoryPath)).let { store ->
        store.write(document)
        store.displayName
    }

    override suspend fun chooseDirectory(currentPath: String?, title: String): String? =
        directoryPicker.chooseDirectory(currentPath ?: defaultDirectory(), title)

    override fun defaultDirectory() = defaultDirectoryPath()
}
