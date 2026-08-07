package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.NaviampCoreExternalUriPort
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.NaviampCoreHost
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.NaviampCoreServices
import app.naviamp.presentation.NaviampCoreProviderSessionPort
import app.naviamp.presentation.NaviampCoreSettingsSyncServices
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.presentation.withShellCapabilities
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampShellCapabilitiesUi

/**
 * Complete input boundary for the replacement Desktop host.
 *
 * It contains Core services and genuine host integrations only. Product controllers, route state,
 * action factories, and screen models are deliberately unrepresentable here.
 */
internal typealias DesktopNaviampCoreEnvironment = NaviampCoreEnvironment

/**
 * Installs the real Desktop connection boundary while later native service families are migrated.
 * The supplied catalog remains complete, but its provider source and connection effect are always
 * replaced together so Core cannot observe a session that differs from its browsing provider.
 */
internal fun desktopNaviampCoreEnvironment(
    services: NaviampCoreServices,
    providerSessions: NaviampCoreProviderSessionPort,
    settingsSync: NaviampCoreSettingsSyncServices = services.settings.sync,
    externalUri: NaviampCoreExternalUriPort = DesktopExternalUriPort(),
    initialState: NaviampCoreInitialState = NaviampCoreInitialState(),
    applicationUpdateChecker: NaviampApplicationUpdateChecker? = desktopApplicationUpdateChecker(),
    shellCapabilities: NaviampShellCapabilitiesUi? = null,
    audioOutputDeviceSelectionAvailable: Boolean = false,
    audioOutputDevices: List<AudioOutputDevice> = emptyList(),
    onAsyncFailure: (NaviampCoreCommand, Throwable) -> Unit = { command, cause ->
        throw IllegalStateException("Desktop Core command failed: $command", cause)
    },
): DesktopNaviampCoreEnvironment = NaviampCoreEnvironment(
    services = services.copy(
        content = services.content.copy(
            providerSource = providerSessions.providerSource,
            externalUri = externalUri,
        ),
        connection = providerSessions,
        settings = services.settings.copy(sync = settingsSync),
    ),
    initialState = (shellCapabilities?.let { capabilities ->
        initialState.withShellCapabilities(
            capabilities = capabilities,
            audioOutputDeviceSelectionAvailable = audioOutputDeviceSelectionAvailable,
            audioOutputDevices = audioOutputDevices,
        )
    } ?: initialState).copy(
        connectionInventory = providerSessions.initialInventory(),
    ),
    actionAvailability = DesktopCapabilityPresentation.toCoreActionAvailability(),
    applicationUpdateChecker = applicationUpdateChecker,
    onAsyncFailure = onAsyncFailure,
)

/** The replacement Desktop product surface: construct Core once and mount its shared app unchanged. */
@Composable
internal fun DesktopNaviampCoreHost(
    environment: DesktopNaviampCoreEnvironment,
    modifier: Modifier = Modifier,
) {
    NaviampCoreHost(
        environment = environment,
        modifier = modifier,
        statsForNerdsPresenter = { diagnostics, close ->
            DesktopStatsForNerdsWindow(diagnostics, close)
        },
    )
}
