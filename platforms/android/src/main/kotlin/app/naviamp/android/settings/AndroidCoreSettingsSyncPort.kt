package app.naviamp.android

import android.content.Context
import android.net.Uri
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncJson
import app.naviamp.presentation.NaviampCoreSettingsSyncConfiguration
import app.naviamp.presentation.NaviampCoreSettingsSyncPort

fun interface AndroidCoreUriPicker {
    suspend fun choose(currentUri: String?, title: String): String?
}

/** ContentResolver and Activity-result effects for Core-owned settings-sync transactions. */
class AndroidCoreSettingsSyncPort(
    context: Context,
    private val settingsStore: AndroidSettingsStore,
    private val directoryPicker: AndroidCoreUriPicker,
    private val documentPicker: AndroidCoreUriPicker,
) : NaviampCoreSettingsSyncPort {
    private val appContext = context.applicationContext

    override fun configuration(): NaviampCoreSettingsSyncConfiguration =
        settingsStore.loadSettingsSync().let { persisted ->
            NaviampCoreSettingsSyncConfiguration(
                directoryPath = persisted.treeUri,
                autoExportEnabled = persisted.autoExportEnabled,
            )
        }

    override fun saveConfiguration(configuration: NaviampCoreSettingsSyncConfiguration) {
        val current = settingsStore.loadSettingsSync()
        settingsStore.saveSettingsSync(
            current.copy(
                treeUri = configuration.directoryPath,
                autoExportEnabled = configuration.autoExportEnabled,
            ),
        )
    }

    override suspend fun readDocument(directoryPath: String): SettingsSyncDocument? =
        AndroidSettingsSyncFile.read(appContext, Uri.parse(directoryPath))

    override suspend fun readDocumentFile(filePath: String): SettingsSyncDocument? {
        val text = appContext.contentResolver.openInputStream(Uri.parse(filePath))
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return null
        return SettingsSyncJson.decode(text)
    }

    override suspend fun writeDocument(
        directoryPath: String,
        document: SettingsSyncDocument,
    ): String {
        AndroidSettingsSyncFile.write(appContext, Uri.parse(directoryPath), document)
        return app.naviamp.domain.settings.SettingsSyncFileName
    }

    override suspend fun chooseDirectory(currentPath: String?, title: String): String? =
        directoryPicker.choose(currentPath, title)

    override suspend fun chooseDocument(currentPath: String?, title: String): String? =
        documentPicker.choose(currentPath, title)

    override fun defaultDirectory(): String = configuration().directoryPath.orEmpty()

    override val available: Boolean = true
}
