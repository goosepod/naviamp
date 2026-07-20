package app.naviamp.desktop

import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.app.settingsSyncAutoExportStatus
import app.naviamp.app.settingsSyncImportStatus
import app.naviamp.app.settingsSyncLocationStatus
import app.naviamp.app.settingsSyncReconciliationStatus
import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.DesktopSettingsSyncDocumentStore
import app.naviamp.desktop.settings.DesktopSettingsSyncSettings
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.desktop.settings.VisualizerSettings
import app.naviamp.domain.settings.SavedInternetRadioStation
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.SettingsSyncOperationKind
import app.naviamp.ui.NaviampSettingsSyncActions
import java.nio.file.Path

internal class DesktopSettingsSyncHost(
    private val settingsStore: DesktopSettingsStore,
    private val controller: NaviampSettingsSyncController,
    private val settings: () -> DesktopSettingsSyncSettings,
    private val setSettings: (DesktopSettingsSyncSettings) -> Unit,
    private val setStatus: (String) -> Unit,
    private val publishError: (String) -> Unit,
) {
    fun actions(): NaviampSettingsSyncActions = NaviampSettingsSyncActions(
        onDirectoryChanged = ::updateDirectory,
        onDirectorySelectedForImport = ::selectDirectoryAndImport,
        onAutoExportChanged = ::updateAutoExport,
        onExport = ::export,
        onImport = ::import,
    )

    fun savePlaybackSettings(value: PlaybackSettings) {
        settingsStore.savePlaybackSettings(value)
        markChangedAndAutoExport()
    }

    fun saveVisualizerSettings(value: VisualizerSettings) {
        settingsStore.saveVisualizerSettings(value)
        markChangedAndAutoExport()
    }

    fun saveRecentRadioStreams(value: List<RecentRadioStream>) {
        settingsStore.saveRecentRadioStreams(value)
        markChangedAndAutoExport()
    }

    fun saveRecentInternetRadioStations(value: List<SavedInternetRadioStation>) {
        settingsStore.saveRecentInternetRadioStations(value)
        markChangedAndAutoExport()
    }

    fun markChangedAndAutoExport() {
        controller.markLocalChanged()
        autoExport()
    }

    fun reconcileAtStartup() {
        val directory = directory() ?: return
        runCatching {
            val providerDocument = DesktopSettingsSyncDocumentStore(directory).read()
            controller.reconcileDocuments(
                localMirrorDocument = null,
                providerDocument = providerDocument,
                syncLocationConfigured = true,
            )
        }.onSuccess { reconciliation ->
            val result = reconciliation.result
            if (result.kind == SettingsSyncOperationKind.Exported) {
                result.documentToWrite?.let { document ->
                    write(document) { fileName -> "Settings sync exported local settings to $fileName." }
                }
            } else {
                setStatus(settingsSyncReconciliationStatus(result))
            }
        }.onFailure { error ->
            publishError(error.message ?: "Could not check settings sync folder.")
        }
    }

    private fun directory(): Path? = settings().directoryPath?.let(Path::of)

    private fun save(value: DesktopSettingsSyncSettings) {
        val normalized = value.normalized()
        setSettings(normalized)
        settingsStore.saveSettingsSync(normalized)
    }

    private fun updateDirectory(path: String?) {
        val current = settings()
        save(
            DesktopSettingsSyncSettings(
                directoryPath = path,
                autoExportEnabled = current.autoExportEnabled && path != null,
                lastLocalUpdateEpochMillis = current.lastLocalUpdateEpochMillis,
                lastAppliedSyncUpdateEpochMillis = current.lastAppliedSyncUpdateEpochMillis,
            ),
        )
        setStatus(settingsSyncLocationStatus(settings().directoryPath != null))
    }

    private fun write(document: SettingsSyncDocument, statusMessage: (String) -> String) {
        val directory = directory()
        if (directory == null) {
            setStatus("Choose a settings sync folder first.")
            return
        }
        val documentStore = DesktopSettingsSyncDocumentStore(directory)
        runCatching {
            documentStore.write(document)
            documentStore.displayName
        }.onSuccess { fileName ->
            controller.documentWritten(document)
            setStatus(statusMessage(fileName))
        }.onFailure { error ->
            publishError(error.message ?: "Could not export settings sync file.")
        }
    }

    private fun export() {
        controller.exportCurrent(markChanged = true).documentToWrite?.let { document ->
            write(document) { fileName -> "Settings exported to $fileName." }
        }
    }

    private fun autoExport() {
        controller.autoExport()?.documentToWrite?.let { document ->
            write(document) { fileName -> "Settings auto-exported to $fileName." }
        }
    }

    private fun updateAutoExport(enabled: Boolean) {
        val current = settings()
        save(current.copy(autoExportEnabled = enabled && current.directoryPath != null))
        setStatus(settingsSyncAutoExportStatus(settings().autoExportEnabled))
        if (settings().autoExportEnabled) autoExport()
    }

    private fun importFrom(directory: Path) {
        runCatching {
            val document = DesktopSettingsSyncDocumentStore(directory).read()
                ?: error("No settings sync file found in that folder.")
            controller.applySyncedDocument(document)
        }.onSuccess { result ->
            setStatus(settingsSyncImportStatus(result.hasServerProfiles))
        }.onFailure { error ->
            publishError(error.message ?: "Could not import settings sync file.")
        }
    }

    private fun import() {
        val directory = directory()
        if (directory == null) {
            setStatus("Choose a settings sync folder first.")
            return
        }
        importFrom(directory)
    }

    private fun selectDirectoryAndImport(path: String) {
        save(settings().copy(directoryPath = path))
        importFrom(Path.of(path))
    }
}
