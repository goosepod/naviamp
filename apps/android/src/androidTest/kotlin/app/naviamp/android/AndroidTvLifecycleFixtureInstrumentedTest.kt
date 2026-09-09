package app.naviamp.android

import androidx.test.platform.app.InstrumentationRegistry
import app.naviamp.domain.settings.ConnectionFormState
import app.naviamp.presentation.*
import app.naviamp.ui.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.test.assertTrue

/** Opt-in setup for an empty disposable emulator and scripts/android-tv-fixture.py. */
class AndroidTvLifecycleFixtureInstrumentedTest {
    @Test fun preparePlayback() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tvLocalFixture") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val core = withContext(Dispatchers.Main) { AndroidNaviampApplicationRuntime.get(context).core }
        check(core.state.value.shell.connectionSettings.currentSourceId == null) {
            "Requires an empty disposable test installation"
        }
        withContext(Dispatchers.Main) {
            core.dispatch(NaviampCoreCommand.Connection.New)
            core.dispatch(NaviampCoreCommand.Connection.ChangeForm(ConnectionFormState(
                displayName = "TV fixture", serverUrl = "http://127.0.0.1:18080", username = "fixture", password = "fixture",
            )))
            core.dispatch(NaviampCoreCommand.Connection.Connect)
        }
        withTimeout(60_000) {
            while (!core.state.value.shell.connectionSettings.connection.connected) delay(200)
        }
        withContext(Dispatchers.Main) {
            core.dispatch(NaviampCoreCommand.Settings.ChangeInterface(
                core.state.value.shell.general.interfaceSettings.copy(startPlayingOnLaunch = true)))
            core.dispatch(NaviampCoreCommand.Settings.ChangeCache(
                app.naviamp.domain.settings.CacheSettings(audioCachingEnabled = false)))
            core.dispatch(NaviampCoreCommand.Library.ChangeView(NaviampLibraryView.Songs))
            core.dispatch(NaviampCoreCommand.Library.Refresh)
        }
        withTimeout(60_000) {
            while (core.state.value.shell.library.songs.tracks.isEmpty()) delay(200)
        }
        withContext(Dispatchers.Main) {
            core.dispatch(NaviampCoreCommand.Library.TrackAction(SharedTrackRowActionRequest(
                core.state.value.shell.library.songs.tracks.first(), SharedTrackRowAction.Select)))
        }
        withTimeout(60_000) {
            while ((core.externalPlaybackBridge().snapshot().positionMillis ?: 0) < 5_000) delay(200)
        }
        assertTrue((core.externalPlaybackBridge().snapshot().positionMillis ?: 0) >= 5_000)
        delay(2_000) // Allow the shared session persistence observer to flush.
    }
}
