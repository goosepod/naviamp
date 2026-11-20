package app.naviamp.presentation

import app.naviamp.ui.NaviampRepeatMode
import app.naviamp.ui.NaviampExternalPlaybackLabels
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampExternalPlaybackSurfaceTest {
    @Test fun activationIsNotResumePermissionAndRecommendationsRequireAnEligibleSystemDeactivation() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        f.surface.audio(NaviampExternalPlaybackAudioEvent.SessionBecameActive)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.Resume))
        runCurrent()
        assertTrue(f.calls.isEmpty())
        f.surface.audio(NaviampExternalPlaybackAudioEvent.SessionBecameInactive(NaviampAudioSessionDeactivationSource.System))
        runCurrent()
        assertEquals(listOf("pause"), f.calls)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.SessionBecameActive)
        runCurrent()
        assertEquals(listOf("pause"), f.calls)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.Resume))
        runCurrent()
        assertEquals(listOf("pause", "play"), f.calls)
        f.session.close()
    }

    @Test fun applicationUnknownAndDisconnectedDeactivationCannotAutoplayFromARecommendation() = runTest {
        for ((source, reason) in listOf(
            NaviampAudioSessionDeactivationSource.Application to null,
            NaviampAudioSessionDeactivationSource.Unknown to null,
            NaviampAudioSessionDeactivationSource.System to NaviampAudioSessionInterruptionReason.OutputDisconnected,
        )) {
            val f = Fixture(backgroundScope)
            runCurrent()
            f.surface.audio(NaviampExternalPlaybackAudioEvent.SessionBecameInactive(source, reason))
            f.surface.audio(NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.Resume))
            runCurrent()
            assertEquals(if (source == NaviampAudioSessionDeactivationSource.Application) emptyList() else listOf("pause"), f.calls)
            f.session.close()
        }
    }

    @Test fun appDeactivationDeniedAndUnknownRecommendationsClearPendingSystemResume() = runTest {
        for (cancel in listOf<NaviampExternalPlaybackAudioEvent>(
            NaviampExternalPlaybackAudioEvent.SessionBecameInactive(NaviampAudioSessionDeactivationSource.Application),
            NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.StayPaused),
            NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.Unknown),
        )) {
            val f = Fixture(backgroundScope)
            runCurrent()
            f.surface.audio(NaviampExternalPlaybackAudioEvent.SessionBecameInactive(NaviampAudioSessionDeactivationSource.System,
                NaviampAudioSessionInterruptionReason.Other))
            runCurrent()
            f.surface.audio(cancel)
            f.surface.audio(NaviampExternalPlaybackAudioEvent.ResumptionRecommended(NaviampAudioSessionResumptionRecommendation.Resume))
            runCurrent()
            assertEquals(listOf("pause"), f.calls)
            f.session.close()
        }
    }

    @Test fun earlyEndWaitsForTheRealPauseAcknowledgmentAndCannotResumeAReplacedQueueItem() = runTest {
        val state = MutableStateFlow(snapshot())
        val calls = mutableListOf<String>()
        val control = object : NaviampExternalPlaybackControl by Control(state, calls) {
            override fun pause() { calls += "pause" } // asynchronous engine acknowledgment
        }
        val surface = Surface()
        val session = NaviampExternalPlaybackSurfaceSession(backgroundScope, control, state, {}, { null }, surface,
            { throw it }, loadLabels = { labels() })
        runCurrent()
        surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
        runCurrent()
        assertEquals(listOf("pause"), calls)
        state.value = state.value.copy(state = NaviampExternalPlaybackState.Paused)
        runCurrent()
        assertEquals(listOf("pause", "play"), calls)
        surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        runCurrent()
        state.value = state.value.copy(currentQueueIndex = 1, state = NaviampExternalPlaybackState.Paused)
        runCurrent()
        surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
        runCurrent()
        assertEquals(listOf("pause", "play", "pause"), calls)
        session.close()
    }

    @Test fun nativeToggleUsesTheSharedTransportAndIsRejectedAfterCapabilityLoss() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        assertTrue(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Toggle))
        assertTrue(f.calls.isEmpty())
        runCurrent()
        assertEquals(listOf("toggle"), f.calls)
        f.state.value = f.state.value.copy(canPlayPause = false)
        assertFalse(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Toggle))
        f.session.close()
        assertFalse(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Toggle))
    }

    @Test fun explicitTransportIntentDuringInterruptionCancelsAutomaticResume() = runTest {
        for (command in listOf(NaviampExternalPlaybackSurfaceCommand.Play, NaviampExternalPlaybackSurfaceCommand.Pause,
            NaviampExternalPlaybackSurfaceCommand.Toggle, NaviampExternalPlaybackSurfaceCommand.Stop,
            NaviampExternalPlaybackSurfaceCommand.Previous, NaviampExternalPlaybackSurfaceCommand.Next)) {
            val f = Fixture(backgroundScope)
            runCurrent()
            f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
            runCurrent()
            assertTrue(f.surface.command(command))
            runCurrent()
            val explicitCalls = f.calls.toList()
            f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
            runCurrent()
            assertEquals(explicitCalls, f.calls, "A late recommendation must not override $command")
            f.session.close()
        }
    }

    @Test fun naturalProgressDoesNotRepublishNativeMetadataButPauseAndSeekDo() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        assertEquals(1, f.surface.publications.size)
        repeat(100) { f.state.value = f.state.value.copy(positionMillis = (it + 1) * 500L); runCurrent() }
        assertEquals(1, f.surface.publications.size)
        f.state.value = f.state.value.copy(state = NaviampExternalPlaybackState.Paused)
        runCurrent()
        f.state.value = f.state.value.copy(positionMillis = 42_000, seekGeneration = 1)
        runCurrent()
        assertEquals(3, f.surface.publications.size)
        f.session.close()
    }

    @Test fun commandsValidateCapabilitiesAndNumericBoundsAndExecuteOnTheOwnerScope() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        val send = f.surface.command
        for (seconds in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 181.0, Long.MAX_VALUE.toDouble())) {
            assertFalse(send(NaviampExternalPlaybackSurfaceCommand.Seek(seconds)))
        }
        assertTrue(send(NaviampExternalPlaybackSurfaceCommand.Seek(1.25)))
        assertTrue(send(NaviampExternalPlaybackSurfaceCommand.Shuffle(true)))
        assertTrue(send(NaviampExternalPlaybackSurfaceCommand.Repeat(NaviampRepeatMode.Track)))
        assertTrue(send(NaviampExternalPlaybackSurfaceCommand.Favorite))
        assertTrue(send(NaviampExternalPlaybackSurfaceCommand.Next))
        assertTrue(send(NaviampExternalPlaybackSurfaceCommand.Previous))
        assertTrue(f.calls.isEmpty(), "Native callbacks queue intents, rather than invoking Core on an OS thread")
        runCurrent()
        assertEquals(listOf("seek:1250", "shuffle:true", "repeat:Track", "favorite", "next", "previous"), f.calls)
        f.state.value = f.state.value.copy(canSeek = false, canShuffle = false, canRepeat = false, canFavorite = false)
        runCurrent()
        assertFalse(send(NaviampExternalPlaybackSurfaceCommand.Seek(1.0)))
        assertFalse(send(NaviampExternalPlaybackSurfaceCommand.Shuffle(false)))
        assertFalse(send(NaviampExternalPlaybackSurfaceCommand.Repeat(NaviampRepeatMode.Off)))
        assertFalse(send(NaviampExternalPlaybackSurfaceCommand.Favorite))
        assertFalse(NaviampExternalPlaybackSurfaceAction.Seek in f.surface.publications.last().actions)
        f.session.close()
    }

    @Test fun commandsQueuedBeforeSourceLossOrCloseCannotActOnTheNewState() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        assertTrue(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Play))
        f.state.value = NaviampExternalPlaybackSnapshot()
        runCurrent()
        assertTrue(f.calls.isEmpty())
        f.state.value = snapshot()
        runCurrent()
        assertTrue(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Toggle))
        f.session.close()
        runCurrent()
        assertTrue(f.calls.isEmpty())
        assertFalse(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Pause))
        assertTrue(f.surface.publications.last().actions.isNotEmpty()) // the queued command was valid at receipt
    }

    @Test fun interruptionResumesOnlyWhenAllowedAndRouteLossCancelsResume() = runTest {
        val f = Fixture(backgroundScope)
        runCurrent()
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        runCurrent()
        assertEquals(listOf("pause"), f.calls)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
        runCurrent()
        assertEquals(listOf("pause", "play"), f.calls)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        runCurrent()
        f.surface.audio(NaviampExternalPlaybackAudioEvent.OutputDisconnected)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
        runCurrent()
        // Losing the output cancels the engine's resume even after the session has paused.
        assertEquals(listOf("pause", "play", "pause", "pause"), f.calls)
        f.session.close()
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
        runCurrent()
        assertEquals(4, f.calls.size)
    }

    @Test fun anAlreadyPausedOrDisallowedInterruptionNeverAutoplays() = runTest {
        val f = Fixture(backgroundScope)
        f.state.value = f.state.value.copy(state = NaviampExternalPlaybackState.Paused)
        runCurrent()
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(true))
        runCurrent()
        assertTrue(f.calls.isEmpty())
        f.state.value = f.state.value.copy(state = NaviampExternalPlaybackState.Playing)
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionBegan)
        runCurrent()
        f.surface.audio(NaviampExternalPlaybackAudioEvent.InterruptionEnded(false))
        runCurrent()
        assertEquals(listOf("pause"), f.calls)
        f.session.close()
    }

    @Test fun staleArtworkThatIgnoresCancellationCannotDecodeOrPublishOverANewerTrack() = runTest {
        val old = CompletableDeferred<ByteArray?>()
        val f = Fixture(backgroundScope) { url -> if (url == "old") withContext(NonCancellable) { old.await() } else byteArrayOf(2) }
        f.state.value = snapshot("old")
        runCurrent()
        f.state.value = snapshot("new").copy(current = snapshot("new").current!!.copy(mediaId = "second"))
        runCurrent()
        assertEquals("image-2", f.surface.publications.last().artwork)
        val count = f.surface.publications.size
        old.complete(byteArrayOf(1))
        runCurrent()
        assertEquals(listOf(2), f.surface.decodes)
        assertEquals(count, f.surface.publications.size)
        f.session.close()
    }

    @Test fun urlReplacementOrRemovalClearsOldArtworkBeforeTheNextFetchCompletes() = runTest {
        val next = CompletableDeferred<ByteArray?>()
        val f = Fixture(backgroundScope) { url -> if (url == "first") byteArrayOf(1) else next.await() }
        f.state.value = snapshot("first")
        runCurrent()
        assertEquals("image-1", f.surface.publications.last().artwork)
        f.state.value = snapshot("second")
        runCurrent()
        assertNull(f.surface.publications.last().artwork)
        f.state.value = snapshot(null)
        runCurrent()
        assertNull(f.surface.publications.last().artwork)
        next.complete(byteArrayOf(2))
        runCurrent()
        assertEquals(listOf(1), f.surface.decodes)
        f.session.close()
    }

    @Test fun failedOrEmptyArtworkIsNonfatalAndDoesNotCreateAnAutomaticRetryLoop() = runTest {
        var requests = 0
        val f = Fixture(backgroundScope) { requests++; if (it == "bad") error("image unavailable") else byteArrayOf() }
        f.state.value = snapshot("bad")
        runCurrent()
        repeat(10) { f.state.value = f.state.value.copy(positionMillis = it * 500L); runCurrent() }
        assertEquals(1, requests)
        f.state.value = snapshot("empty")
        runCurrent()
        assertEquals(2, requests)
        assertTrue(f.surface.decodes.isEmpty())
        assertTrue(f.failures.isEmpty())
        assertTrue(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Pause))
        runCurrent()
        assertEquals(listOf("pause"), f.calls)
        f.session.close()
    }

    @Test fun closeDuringAnUncooperativeLoadInvalidatesAllNativeWorkAndCallbacks() = runTest {
        val bytes = CompletableDeferred<ByteArray?>()
        val f = Fixture(backgroundScope) { withContext(NonCancellable) { bytes.await() } }
        f.state.value = snapshot("image")
        runCurrent()
        val count = f.surface.publications.size
        f.session.close(); f.session.close()
        bytes.complete(byteArrayOf(1))
        runCurrent()
        assertEquals(1, f.surface.closes)
        assertTrue(f.surface.decodes.isEmpty())
        assertEquals(count, f.surface.publications.size)
        assertFalse(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Play))
    }

    @Test fun cancellationBeforeTheCollectorStartsStillClosesTheRegisteredSurface() = runTest {
        val parent = SupervisorJob()
        val f = Fixture(CoroutineScope(StandardTestDispatcher(testScheduler) + parent))
        parent.cancel()
        runCurrent()
        assertEquals(1, f.surface.attaches)
        assertEquals(1, f.surface.closes)
        assertTrue(f.surface.publications.isEmpty())
        assertFalse(f.surface.command(NaviampExternalPlaybackSurfaceCommand.Play))
    }

    @Test fun nativeRegistrationFailureRollsBackAndLeavesTheParentUsable() = runTest {
        val surface = Surface().apply { failAttach = true }
        assertFailsWith<IllegalStateException> { Fixture(backgroundScope, surface = surface) }
        runCurrent()
        assertEquals(1, surface.closes)
        assertFalse(surface.command(NaviampExternalPlaybackSurfaceCommand.Play))
        assertTrue(backgroundScope.isActive)
    }

    @Test fun publicationFailureClosesTheSurfaceAndReportsToTheOwningHost() = runTest {
        val surface = Surface().apply { failPublish = true }
        val f = Fixture(backgroundScope, surface = surface)
        runCurrent()
        assertEquals(1, f.failures.size)
        assertEquals("publication failed", f.failures.single().message)
        assertEquals(1, surface.closes)
        assertFalse(surface.command(NaviampExternalPlaybackSurfaceCommand.Play))
        assertTrue(backgroundScope.isActive)
    }

    @Test fun registrationOfCloseHappensBeforeNativeAttachment() = runTest {
        val surface = Surface()
        val retained = mutableListOf<() -> Unit>()
        val state = MutableStateFlow(snapshot())
        val session = NaviampExternalPlaybackSurfaceSession(backgroundScope, Control(state, mutableListOf()), state,
            {}, { null }, surface, { throw it }, registerClose = { assertEquals(0, surface.attaches); retained += it }, loadLabels = { labels() })
        retained.single()()
        runCurrent()
        assertEquals(1, surface.closes)
        session.close()
    }
}

private fun snapshot(url: String? = null) = NaviampExternalPlaybackSnapshot(
    state = NaviampExternalPlaybackState.Playing,
    current = NaviampExternalMediaItem("track", "Title", "Artist", artworkUrl = url),
    positionMillis = 0, durationMillis = 180_000, canPlayPause = true, canSeek = true,
    hasPrevious = true, hasNext = true, canFavorite = true, canShuffle = true, canRepeat = true,
)

private class Fixture(scope: CoroutineScope, val surface: Surface = Surface(), load: suspend (String) -> ByteArray? = { null }) {
    val state = MutableStateFlow(snapshot())
    val calls = mutableListOf<String>()
    val failures = mutableListOf<Throwable>()
    val session = NaviampExternalPlaybackSurfaceSession(scope, Control(state, calls), state,
        { calls += "favorite" }, load, surface, failures::add, loadLabels = { labels() })
}

private class Control(val state: MutableStateFlow<NaviampExternalPlaybackSnapshot>, val calls: MutableList<String>) : NaviampExternalPlaybackControl {
    override fun snapshot() = state.value
    override fun play() { calls += "play"; state.value = state.value.copy(state = NaviampExternalPlaybackState.Playing) }
    override fun pause() { calls += "pause"; state.value = state.value.copy(state = NaviampExternalPlaybackState.Paused) }
    override fun playPause() { calls += "toggle" }
    override fun stop() { calls += "stop" }
    override fun previous() { calls += "previous" }
    override fun next() { calls += "next" }
    override fun seekTo(positionMillis: Long) { calls += "seek:$positionMillis" }
    override fun seekBy(deltaMillis: Long, advancePastEnd: Boolean) { calls += "seekBy:$deltaMillis" }
    override fun setShuffleActive(active: Boolean) { calls += "shuffle:$active" }
    override fun setRepeatMode(mode: NaviampRepeatMode) { calls += "repeat:$mode" }
    override fun setVolumePercent(percent: Int) { calls += "volume:$percent" }
}

private data class Publication(val snapshot: NaviampExternalPlaybackSnapshot, val actions: Set<NaviampExternalPlaybackSurfaceAction>, val artwork: String?)
private class Surface : NaviampExternalPlaybackSurface<String> {
    lateinit var command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean
    lateinit var audio: (NaviampExternalPlaybackAudioEvent) -> Unit
    var attaches = 0
    var closes = 0
    var failAttach = false
    var failPublish = false
    val decodes = mutableListOf<Int>()
    val publications = mutableListOf<Publication>()
    override fun attach(command: (NaviampExternalPlaybackSurfaceCommand) -> Boolean, audioEvent: (NaviampExternalPlaybackAudioEvent) -> Unit) {
        this.command = command; audio = audioEvent; attaches++
        if (failAttach) error("attachment failed")
    }
    override fun decodeArtwork(bytes: ByteArray): String { decodes += bytes[0].toInt(); return "image-${bytes[0]}" }
    override fun publish(snapshot: NaviampExternalPlaybackSnapshot, actions: Set<NaviampExternalPlaybackSurfaceAction>, artwork: String?, labels: NaviampExternalPlaybackLabels) {
        if (failPublish) error("publication failed")
        publications += Publication(snapshot, actions, artwork)
    }
    override fun close() { closes++ }
}

private fun labels() = NaviampExternalPlaybackLabels("Queue", emptyMap())
