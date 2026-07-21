package app.naviamp.presentation

import app.naviamp.app.NaviampCacheSettingsController
import app.naviamp.domain.settings.InterfaceSettings
import app.naviamp.domain.settings.PlaybackSettingsMaintenanceController

fun interface NaviampCoreInterfaceSettingsStore {
    fun save(settings: InterfaceSettings)
}

enum class NaviampCoreMaintenanceOperation {
    ClearCache,
    ClearLibrary,
    RefreshLibrary,
    ResetDatabase,
}

data class NaviampCoreMaintenanceResult(val status: String)

/** Narrow I/O boundary. Core chooses the operation, sequencing, and user-facing result. */
fun interface NaviampCoreMaintenancePort {
    suspend fun run(operation: NaviampCoreMaintenanceOperation): NaviampCoreMaintenanceResult
}

/** Owns settings normalization, persistence intent, consequences, overlays, and maintenance state. */
class NaviampCoreSettingsController(
    private val stateStore: NaviampCoreStateStore,
    private val interfaceStore: NaviampCoreInterfaceSettingsStore,
    private val playbackController: PlaybackSettingsMaintenanceController,
    private val cacheController: NaviampCacheSettingsController,
    private val maintenancePort: NaviampCoreMaintenancePort,
) : NaviampCoreCommandController {
    override fun dispatch(command: NaviampCoreCommand): NaviampCoreImmediateCommandResult {
        val settings = command as? NaviampCoreCommand.Settings
            ?: return NaviampCoreImmediateCommandResult.Unhandled
        when (settings) {
            is NaviampCoreCommand.Settings.ChangeInterface -> changeInterface(settings)
            is NaviampCoreCommand.Settings.ChangePlayback -> changePlayback(settings)
            is NaviampCoreCommand.Settings.ChangeCache -> changeCache(settings.settings)
            is NaviampCoreCommand.Settings.ChangeDownloadLocation -> {
                val current = stateStore.state.value.shell.cache.settings
                changeCache(current.copy(customDownloadDirectory = settings.location.path))
            }
            is NaviampCoreCommand.Settings.ChangeAudioCacheLocation -> {
                val current = stateStore.state.value.shell.cache.settings
                changeCache(current.copy(customAudioCacheDirectory = settings.location.path))
            }
            NaviampCoreCommand.Settings.OpenStats -> updateStatsVisibility(true)
            NaviampCoreCommand.Settings.CloseStats -> updateStatsVisibility(false)
            NaviampCoreCommand.Settings.ClearCache,
            NaviampCoreCommand.Settings.ClearLibrary,
            NaviampCoreCommand.Settings.RefreshLibrary,
            NaviampCoreCommand.Settings.ResetDatabase,
            -> return NaviampCoreImmediateCommandResult.Deferred
        }
        return NaviampCoreImmediateCommandResult.Handled()
    }

    override suspend fun execute(command: NaviampCoreCommand): NaviampCoreCommandResult? {
        val operation = when (command) {
            NaviampCoreCommand.Settings.ClearCache -> NaviampCoreMaintenanceOperation.ClearCache
            NaviampCoreCommand.Settings.ClearLibrary -> NaviampCoreMaintenanceOperation.ClearLibrary
            NaviampCoreCommand.Settings.RefreshLibrary -> NaviampCoreMaintenanceOperation.RefreshLibrary
            NaviampCoreCommand.Settings.ResetDatabase -> NaviampCoreMaintenanceOperation.ResetDatabase
            else -> return null
        }
        val result = maintenancePort.run(operation)
        stateStore.update { state ->
            state.copy(overlays = state.overlays.copy(status = result.status))
        }
        return NaviampCoreCommandResult.Completed
    }

    private fun changeInterface(command: NaviampCoreCommand.Settings.ChangeInterface) {
        val settings = command.settings.normalized()
        interfaceStore.save(settings)
        stateStore.updateShell { shell ->
            shell.copy(general = shell.general.copy(interfaceSettings = settings))
        }
    }

    private fun changePlayback(command: NaviampCoreCommand.Settings.ChangePlayback) {
        val settings = if (command.redownload) {
            playbackController.applyPlaybackSettingsAndRedownload(command.settings)
        } else {
            playbackController.applyPlaybackSettings(command.settings)
        }
        stateStore.updateShell { shell ->
            shell.copy(playback = shell.playback.copy(settings = settings))
        }
    }

    private fun changeCache(requested: app.naviamp.domain.settings.CacheSettings) {
        val settings = cacheController.apply(requested)
        stateStore.updateShell { shell ->
            shell.copy(cache = shell.cache.copy(settings = settings))
        }
    }

    private fun updateStatsVisibility(visible: Boolean) {
        stateStore.update { state ->
            state.copy(overlays = state.overlays.copy(statsForNerdsVisible = visible))
        }
    }
}
