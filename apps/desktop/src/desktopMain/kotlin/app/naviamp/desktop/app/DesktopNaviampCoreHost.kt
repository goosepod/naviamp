package app.naviamp.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.naviamp.desktop.platform.desktopGlobalShortcutRegistrar
import app.naviamp.presentation.NaviampCoreCommand
import app.naviamp.presentation.NaviampCoreApp
import app.naviamp.presentation.NaviampCoreHostShortcutEffect
import app.naviamp.presentation.NaviampCoreExternalUriPort
import app.naviamp.presentation.NaviampCoreEnvironment
import app.naviamp.presentation.rememberNaviampCore
import app.naviamp.presentation.NaviampCoreInitialState
import app.naviamp.presentation.NaviampCoreServices
import app.naviamp.presentation.NaviampCoreProviderSessionPort
import app.naviamp.presentation.NaviampCoreSettingsSyncServices
import app.naviamp.presentation.toCoreActionAvailability
import app.naviamp.presentation.withShellCapabilities
import app.naviamp.domain.playback.AudioOutputDevice
import app.naviamp.ui.NaviampApplicationUpdateChecker
import app.naviamp.ui.NaviampShellCapabilitiesUi
import app.naviamp.ui.LocalNaviampTextInputFocusRegistry
import app.naviamp.ui.rememberNaviampTextInputFocusRegistry
import app.naviamp.domain.settings.resolvedBindings
import java.awt.EventQueue
import java.awt.Frame
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    window: Window,
    modifier: Modifier = Modifier,
) {
    val core = rememberNaviampCore(
        services = environment.services,
        initialState = environment.initialState,
        actionAvailability = environment.actionAvailability,
        onAsyncFailure = environment.onAsyncFailure,
    )
    val state by core.state.collectAsState()
    val scope = rememberCoroutineScope()
    val platform = state.shell.capabilities.desktopShortcutPlatform
    val registrar = remember(platform) { platform?.let(::desktopGlobalShortcutRegistrar) }
    val shortcutSettings = state.shell.general.interfaceSettings.globalKeyboardShortcuts
    val bindings = platform?.let(shortcutSettings::resolvedBindings).orEmpty()
    DisposableEffect(registrar) { onDispose { registrar?.close() } }
    LaunchedEffect(registrar, shortcutSettings.enabled, bindings) {
        if (registrar == null || !shortcutSettings.enabled) {
            withContext(Dispatchers.IO) { registrar?.close() }
            core.updateGlobalShortcutStatuses(emptyMap())
        } else {
            val statuses = withContext(Dispatchers.IO) {
                registrar.register(bindings) { action ->
                    scope.launch {
                        if (core.handleGlobalShortcut(action) == NaviampCoreHostShortcutEffect.BringToFront) {
                            bringDesktopWindowToFront(window)
                        }
                    }
                }
            }
            core.updateGlobalShortcutStatuses(statuses)
        }
    }
    val textInputFocusRegistry = rememberNaviampTextInputFocusRegistry()
    DisposableEffect(window, core, textInputFocusRegistry) {
        var spaceHeld = false
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val dispatcher = KeyEventDispatcher { event ->
            val focusedWindow = focusManager.focusedWindow
            if (textInputFocusRegistry.hasFocusedTextInput || !focusedWindow.belongsTo(window) ||
                event.keyCode != KeyEvent.VK_SPACE || event.modifiersEx != 0
            ) {
                return@KeyEventDispatcher false
            }
            when (event.id) {
                KeyEvent.KEY_PRESSED -> if (!spaceHeld) {
                    spaceHeld = true
                    core.handleGlobalShortcut(app.naviamp.domain.settings.GlobalShortcutAction.PlayPause)
                }
                KeyEvent.KEY_RELEASED -> spaceHeld = false
                else -> return@KeyEventDispatcher false
            }
            event.consume()
            true
        }
        focusManager.addKeyEventDispatcher(dispatcher)
        onDispose { focusManager.removeKeyEventDispatcher(dispatcher) }
    }
    CompositionLocalProvider(LocalNaviampTextInputFocusRegistry provides textInputFocusRegistry) {
        NaviampCoreApp(
            core = core,
            modifier = modifier,
            applicationUpdateChecker = environment.applicationUpdateChecker,
            statsForNerdsPresenter = { diagnostics, close ->
                DesktopStatsForNerdsWindow(diagnostics, close)
            },
        )
    }
}

private fun Window?.belongsTo(owner: Window): Boolean =
    generateSequence(this) { window -> window.owner }.any { window -> window == owner }

private fun bringDesktopWindowToFront(window: Window) {
    EventQueue.invokeLater {
        (window as? Frame)?.let { frame ->
            frame.extendedState = frame.extendedState and Frame.ICONIFIED.inv()
        }
        window.isVisible = true
        window.toFront()
        window.requestFocus()
    }
}
