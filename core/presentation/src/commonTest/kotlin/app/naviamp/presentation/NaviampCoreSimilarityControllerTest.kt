package app.naviamp.presentation

import app.naviamp.domain.provider.MediaProvider
import app.naviamp.domain.radio.*
import app.naviamp.ui.SimilarityTestState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCoreSimilarityControllerTest {
    @Test
    fun testReportsBothEndpointsWithoutChangingPlaybackOrPreference() = runTest {
        val base = FakeCoreMediaProvider()
        val provider = object : MediaProvider by base {
            override suspend fun similaritySupport() = SimilaritySupport.Advertised
            override suspend fun similarityEndpointTracks(id: app.naviamp.domain.TrackId, endpoint: SimilarityEndpoint, count: Int) =
                if (endpoint == SimilarityEndpoint.Sonic) throw SimilarityRequestException(503)
                else listOf(base.track.copy(id = app.naviamp.domain.TrackId("match")))
        }
        val store = NaviampCoreStateStore()
        val before = store.state.value
        NaviampCoreSimilarityController(store, NaviampCoreMediaProviderSource { provider }) { base.track }.test()
        val test = store.state.value.shell.playback.similarityTest
        assertEquals(SimilarityTestState.Complete, test.state)
        assertEquals(SimilarityResultKind.Failed, test.report?.sonic?.kind)
        assertEquals(SimilarityResultKind.Matches, test.report?.regular?.kind)
        assertEquals(before, store.state.value.copy(shell = store.state.value.shell.copy(
            playback = store.state.value.shell.playback.copy(similarityTest = before.shell.playback.similarityTest),
        )))
    }

    @Test
    fun testDiscardsResultsAfterSourceChanges() = runTest {
        val base = FakeCoreMediaProvider()
        val gate = CompletableDeferred<Unit>()
        val provider = object : MediaProvider by base {
            override suspend fun similaritySupport(): SimilaritySupport { gate.await(); return SimilaritySupport.Advertised }
        }
        var active: MediaProvider? = provider
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreSimilarityController(store, NaviampCoreMediaProviderSource { active }) { base.track }
        val task = async { controller.test() }
        runCurrent()
        assertEquals(SimilarityTestState.Running, store.state.value.shell.playback.similarityTest.state)
        active = null
        gate.complete(Unit)
        task.await()
        assertEquals(SimilarityTestState.Idle, store.state.value.shell.playback.similarityTest.state)
        assertNull(store.state.value.shell.playback.similarityTest.report)
    }

    @Test
    fun testDoesNotOverlapAndCanRetryAfterCancellation() = runTest {
        val base = FakeCoreMediaProvider()
        val gate = CompletableDeferred<Unit>()
        var seeds = 0
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreSimilarityController(store, NaviampCoreMediaProviderSource { base }) {
            seeds++; gate.await(); base.track
        }
        val task = async { controller.test() }
        runCurrent()
        controller.test()
        assertEquals(1, seeds)
        task.cancel()
        task.join()
        assertEquals(SimilarityTestState.Idle, store.state.value.shell.playback.similarityTest.state)
        gate.complete(Unit)
        controller.test()
        assertEquals(2, seeds)
    }

    @Test
    fun sourceResetCancelsOldTestAndAllowsImmediateRetryOnSameServer() = runTest {
        val base = FakeCoreMediaProvider()
        val gate = CompletableDeferred<Unit>()
        var seeds = 0
        val store = NaviampCoreStateStore()
        val controller = NaviampCoreSimilarityController(store, NaviampCoreMediaProviderSource { base }) {
            if (++seeds == 1) gate.await()
            base.track
        }
        val old = async { controller.test() }
        runCurrent()
        controller.resetForSourceChange()
        controller.test()
        old.join()
        assertTrue(old.isCancelled)
        assertEquals(2, seeds)
        assertEquals(SimilarityTestState.Complete, store.state.value.shell.playback.similarityTest.state)
    }

    @Test
    fun testHandlesDisconnectedAndEmptyLibraries() = runTest {
        val store = NaviampCoreStateStore()
        val base = FakeCoreMediaProvider()
        NaviampCoreSimilarityController(store, NaviampCoreMediaProviderSource { null }) { error("must not run") }.test()
        assertEquals(SimilarityTestState.NoSeed, store.state.value.shell.playback.similarityTest.state)
        NaviampCoreSimilarityController(store, NaviampCoreMediaProviderSource { base }) { null }.test()
        assertEquals(SimilarityTestState.NoSeed, store.state.value.shell.playback.similarityTest.state)
    }
}
