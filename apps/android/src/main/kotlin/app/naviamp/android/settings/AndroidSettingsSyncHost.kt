package app.naviamp.android

import android.content.Context
import android.net.Uri
import app.naviamp.app.NaviampApplicationStatusLevel
import app.naviamp.app.NaviampSettingsSyncController
import app.naviamp.app.NaviampSettingsSyncHostController
import app.naviamp.domain.settings.SettingsSyncDocument

/** Android document-provider and persisted-state adapter for the shared settings-sync workflow. */
internal class AndroidSettingsSyncHost(
    context: Context,
    controller: NaviampSettingsSyncController,
    mirrorStore: AndroidSettingsSyncMirrorStore,
    private val settings: () -> AndroidSettingsSyncSettings,
    private val saveSettings: (AndroidSettingsSyncSettings) -> Unit,
    setStatus: (String) -> Unit,
    publishStatus: (String, NaviampApplicationStatusLevel) -> Unit,
) {
    private val appContext = context.applicationContext
    private val workflow = NaviampSettingsSyncHostController(
        controller = controller,
        mirrorStore = mirrorStore,
        providerStore = {
            settings().treeUri
                ?.let(Uri::parse)
                ?.let { uri -> AndroidSettingsSyncDocumentStore(appContext, uri) }
        },
        autoExportEnabled = { settings().autoExportEnabled },
        saveAutoExportEnabled = { enabled ->
            saveSettings(settings().copy(autoExportEnabled = enabled))
        },
        onMirrorWritten = ::markMirrorWritten,
        onProviderPullSucceeded = ::markProviderPullSucceeded,
        onProviderPushSucceeded = ::markProviderPushSucceeded,
        onProviderSyncFailed = ::markProviderSyncFailed,
        setStatus = setStatus,
        publishStatusEffect = publishStatus,
    )

    fun markChangedAndAutoExport() = workflow.markChangedAndAutoExport()

    fun syncNow(statusPrefix: String = "Settings sync") = workflow.syncNow(statusPrefix)

    fun exportToProvider() = workflow.exportToProvider()

    fun updateAutoExport(enabled: Boolean) = workflow.updateAutoExport(enabled)

    private fun markMirrorWritten(document: SettingsSyncDocument) {
        saveSettings(
            settings().copy(
                lastMirrorUpdateEpochMillis = document.updatedAtEpochMillis,
                lastProviderError = null,
            ),
        )
    }

    private fun markProviderPullSucceeded() {
        saveSettings(
            settings().copy(
                lastProviderPullEpochMillis = AndroidSystemClock.nowEpochMillis(),
                lastProviderError = null,
            ),
        )
    }

    private fun markProviderPushSucceeded() {
        saveSettings(
            settings().copy(
                lastProviderPushEpochMillis = AndroidSystemClock.nowEpochMillis(),
                lastProviderError = null,
            ),
        )
    }

    private fun markProviderSyncFailed(message: String) {
        saveSettings(settings().copy(lastProviderError = message))
    }
}
