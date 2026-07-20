package app.naviamp.desktop

import app.naviamp.desktop.settings.DesktopSettingsStore
import app.naviamp.desktop.settings.PlaybackSettings
import app.naviamp.desktop.settings.RecentRadioStream
import app.naviamp.domain.app.NaviampRoute
import app.naviamp.domain.playback.PlaybackEngine
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.SettingsSyncDocument
import app.naviamp.domain.settings.applySettingsSyncDocument
import app.naviamp.ui.NaviampVisualizer
import app.naviamp.ui.naviampVisualizerFromName

internal class DesktopSettingsSyncDocumentApplicator(
    private val settingsStore: DesktopSettingsStore,
    private val storage: DesktopStorageDependencies,
    private val playbackEngine: PlaybackEngine,
    private val setInterfaceSettings: (InterfaceSettings) -> Unit,
    private val setPlaybackSettings: (PlaybackSettings) -> Unit,
    private val selectVisualizer: (NaviampVisualizer) -> Unit,
    private val setRecentRadioStreams: (List<RecentRadioStream>) -> Unit,
    private val connectionForm: DesktopConnectionFormStateHolder,
    private val onServerProfilesImported: () -> Unit,
    private val setRoute: (NaviampRoute) -> Unit,
) {
    fun apply(document: SettingsSyncDocument) {
        val applied = applySettingsSyncDocument(
            document = document,
            playbackEngine = playbackEngine,
            mediaSourceRepository = storage,
            radioDjPresetRepository = storage,
        )
        setInterfaceSettings(applied.interfaceSettings)
        settingsStore.saveInterfaceSettings(applied.interfaceSettings)
        setPlaybackSettings(applied.playbackSettings)
        settingsStore.savePlaybackSettings(applied.playbackSettings.copy(radioDjs = emptyList()))
        settingsStore.saveVisualizerSettings(applied.visualizer)
        selectVisualizer(naviampVisualizerFromName(applied.visualizer.selectedVisualizer))
        setRecentRadioStreams(applied.recentRadioStreams)
        settingsStore.saveRecentRadioStreams(applied.recentRadioStreams)
        settingsStore.saveRecentInternetRadioStations(applied.recentInternetRadioStations)

        val importedProfiles = applied.importedServerProfiles
        if (importedProfiles.importedCount > 0) onServerProfilesImported()
        importedProfiles.firstConnectionForm?.let { form ->
            connectionForm.apply(
                DesktopConnectionFormState(
                    serverUrl = form.serverUrl,
                    connectionName = form.displayName,
                    username = form.username,
                    password = "",
                    insecureSkipTlsVerification = form.skipTlsVerification,
                    customCertificatePath = form.customCertificatePath,
                    clientCertificateKeyStorePath = form.clientCertificatePath,
                    clientCertificateKeyStorePassword = "",
                    secondaryUrls = form.secondaryUrls,
                    customHeaders = form.customHeaders,
                ),
            )
            connectionForm.isOpen = true
            setRoute(NaviampRoute.Settings)
        }
    }
}
