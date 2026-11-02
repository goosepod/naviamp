package app.naviamp.presentation

import app.naviamp.app.*
import app.naviamp.ui.NowPlayingPlaybackAction
import app.naviamp.ui.NowPlayingPlaybackActionRequest
import app.naviamp.domain.queue.PlaybackQueue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreCastAvailabilityTest {
    @Test
    fun unavailableSenderLeavesStartupBrowsingAndLocalPlaybackUsable() = runTest {
        val provider = FakeCoreMediaProvider()
        var localStarts = 0
        val playback = object : NaviampCorePlaybackEffectPort by fakeCoreServices().playback.effects {
            override fun startOrRestore(): Boolean { localStarts++; return true }
        }
        var pickerCalls = 0
        var discoveryStarts = 0
        val session = Session(available = false)
        val core = NaviampCore.create(backgroundScope, fakeCoreServices(provider, playback).copy(
            cast = castServices(session, { pickerCalls++ }).copy(discovery = object : NaviampCastDiscoveryEffect {
                override fun start(serviceType: String, listener: NaviampCastDiscoveryListener) { discoveryStarts++ }
                override fun stop() = Unit
            }),
        ))
        runCurrent()
        assertFalse(core.castAvailable)
        assertNull(core.castPickerState)
        core.showCastPicker()
        assertEquals(0, pickerCalls)
        assertEquals(0, discoveryStarts)
        assertEquals(1, session.starts)
        assertEquals(1, session.stops)
        assertEquals(NaviampPlaybackOutputSelection.Local, core.playbackOutputs.state.value)

        core.execute(NaviampCoreCommand.Search.ChangeQuery("Core"))
        core.execute(NaviampCoreCommand.Search.Submit)
        assertEquals(provider.track.id.value, core.state.value.shell.search.results.tracks.single().id)
        core.updateLivePlayback { it.copy(currentTrack = provider.track, queue = PlaybackQueue(listOf(provider.track), 0)) }
        core.execute(NaviampCoreCommand.NowPlaying.Playback(NowPlayingPlaybackActionRequest(NowPlayingPlaybackAction.PlayCurrent)))
        runCurrent()
        assertEquals("Core", core.state.value.shell.search.query)
        assertTrue(localStarts > 0)
        core.shutdown()
        assertEquals(1, session.stops)
        assertEquals(0, session.disconnects)
    }

    @Test
    fun supportedSenderStillInitializesOnceAndExposesNativePicker() = runTest {
        val session = Session(available = true)
        var pickerCalls = 0
        val core = NaviampCore.create(backgroundScope, fakeCoreServices().copy(
            cast = castServices(session) { pickerCalls++ },
        ))
        assertTrue(core.castAvailable)
        assertEquals(1, session.starts)
        core.showCastPicker()
        assertEquals(1, pickerCalls)
        core.shutdown()
        assertEquals(1, session.stops)
        assertEquals(1, session.disconnects)
    }

    private fun castServices(session: Session, picker: () -> Unit) = NaviampCoreCastServices(
        mediaStore = FakeCastMediaStore(),
        session = session,
        server = object : NaviampCastHttpServerEffect {
            override suspend fun start(handler: suspend (NaviampCastHttpRequest, NaviampCastHttpResponse) -> Unit): String =
                error("Startup must not open a media server")
            override suspend fun stop() = Unit
        },
        tokens = NaviampCastSecureTokenSource { error("Startup must not issue media tokens") },
        showRoutePicker = picker,
    )

    private class Session(private val available: Boolean) : NaviampCastSessionEffect {
        var starts = 0
        var stops = 0
        var disconnects = 0
        override fun start(listener: NaviampCastSessionListener) {
            starts++
            check(available) { "Sender module unavailable" }
        }
        override fun stop() { stops++ }
        override fun disconnect() { disconnects++ }
    }
}
