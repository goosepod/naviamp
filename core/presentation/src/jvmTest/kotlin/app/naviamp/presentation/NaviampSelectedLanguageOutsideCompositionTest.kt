package app.naviamp.presentation

import app.naviamp.domain.settings.InterfaceLanguage
import app.naviamp.ui.NaviampExternalPlaybackAction
import app.naviamp.ui.NaviampExternalPlaybackLabels
import app.naviamp.ui.NaviampExternalTransportLabel
import app.naviamp.ui.loadNaviampExternalPlaybackLabels
import app.naviamp.ui.loadNaviampVehiclePlaybackLabels
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** Native media surfaces resolve text without any application composition. */
class NaviampSelectedLanguageOutsideCompositionTest {
    @Test fun mediaSurfaceAndVehicleTextFollowTheSelectedLanguageInsteadOfTheSystemLocale() = withSystemLocale(Locale.GERMAN) {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val core = NaviampCore.create(scope, fakeCoreServices(), initialState = initialLanguage(InterfaceLanguage.English))
            val surface = RecordingSurface()
            try {
                core.externalPlaybackBridge().attachSurface(scope, surface, { null }, { throw it })
                // Android restores the system LocaleList on configuration changes while no activity runs.
                Locale.setDefault(Locale.GERMAN)

                val notification = loadNaviampExternalPlaybackLabels()
                assertEquals("Queue", notification.queueTitle)
                assertEquals("Play", notification.transport[NaviampExternalTransportLabel.Play])
                assertEquals("Start Radio", loadNaviampVehiclePlaybackLabels().radio)
                assertEquals("Queue", surface.awaitLabels { it.queueTitle == "Queue" }.queueTitle)

                val settings = core.state.value.shell.general.interfaceSettings
                core.dispatch(NaviampCoreCommand.Settings.ChangeInterface(settings.copy(language = InterfaceLanguage.Spanish)))

                // Text already published to the native surface refreshes without new playback state.
                val refreshed = surface.awaitLabels { it.queueTitle == "Cola" }
                assertEquals("Iniciar radio", refreshed.actions[NaviampExternalPlaybackAction.StartRadio])
                assertEquals("Iniciar radio", loadNaviampVehiclePlaybackLabels().radio)

                core.dispatch(NaviampCoreCommand.Settings.ChangeInterface(settings.copy(language = InterfaceLanguage.System)))
                assertEquals("Warteschlange", surface.awaitLabels { it.queueTitle == "Warteschlange" }.queueTitle)
            } finally {
                core.close()
                scope.cancel()
            }
        }
    }

    @Test fun closingTheCoreReturnsResourceLookupsToTheSystemLanguage() = withSystemLocale(Locale.GERMAN) {
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val core = NaviampCore.create(scope, fakeCoreServices(), initialState = initialLanguage(InterfaceLanguage.English))
            assertEquals("Queue", loadNaviampExternalPlaybackLabels().queueTitle)
            core.close()
            scope.cancel()
            assertEquals("Warteschlange", loadNaviampExternalPlaybackLabels().queueTitle)
        }
    }
}

private fun initialLanguage(language: InterfaceLanguage): NaviampCoreInitialState {
    val initial = NaviampCoreInitialState()
    val shell = initial.product.shell
    return initial.copy(product = initial.product.copy(shell = shell.copy(general = shell.general.copy(
        interfaceSettings = shell.general.interfaceSettings.copy(language = language),
    ))))
}

private fun withSystemLocale(locale: Locale, block: () -> Unit) {
    val original = Locale.getDefault()
    Locale.setDefault(locale)
    try { block() } finally { Locale.setDefault(original) }
}

private class RecordingSurface : NaviampExternalPlaybackSurface<Unit> {
    @Volatile private var published: List<NaviampExternalPlaybackLabels> = emptyList()

    override fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean, audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit) = Unit
    override fun decodeArtwork(bytes: ByteArray) = Unit
    override fun publish(snapshot: NaviampExternalPlaybackSnapshot, actions: Set<NaviampExternalPlaybackSurfaceAction>, artwork: Unit?, labels: NaviampExternalPlaybackLabels) {
        published = published + labels
    }
    override fun close() = Unit

    suspend fun awaitLabels(matches: (NaviampExternalPlaybackLabels) -> Boolean): NaviampExternalPlaybackLabels = withTimeout(10_000) {
        while (published.lastOrNull()?.let(matches) != true) delay(10)
        published.last()
    }
}
