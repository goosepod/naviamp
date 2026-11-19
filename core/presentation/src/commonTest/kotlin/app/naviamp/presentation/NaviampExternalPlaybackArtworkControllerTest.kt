package app.naviamp.presentation

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NaviampExternalPlaybackArtworkControllerTest {
    private fun media() = NaviampExternalPlaybackSnapshot(current = NaviampExternalMediaItem("same-track", "Track", "Artist", artworkUrl = "same-url"),
        resumeIdentity = NaviampExternalPlaybackResumeIdentity("account-a", 1, 1, 1, 1, "same-track", 0, false))
    @Test fun switchingAccountsWithTheSameMediaIdAndUrlClearsAndReloadsArtwork() = runTest {
        var value = media(); val changes = mutableListOf<ByteArray?>(); var loads = 0
        val owner = NaviampExternalPlaybackArtworkController(backgroundScope, { value }, { byteArrayOf((++loads).toByte()) }, changes::add)
        owner.update(value); runCurrent(); assertContentEquals(byteArrayOf(1), changes.last())
        value = value.copy(resumeIdentity = value.resumeIdentity!!.copy(sourceId = "account-b", sourceGeneration = 2))
        owner.update(value); assertNull(changes.last()); runCurrent()
        assertEquals(2, loads); assertContentEquals(byteArrayOf(2), changes.last()); owner.close()
    }
    @Test fun lateBytesCannotCrossASourceTransitionOrGlobalShutdown() = runTest {
        var value = media(); val gate = CompletableDeferred<Unit>(); val changes = mutableListOf<ByteArray?>(); var loads = 0
        val owner = NaviampExternalPlaybackArtworkController(backgroundScope, { value }, {
            loads++; if (loads == 1) withContext(NonCancellable) { gate.await() }; byteArrayOf(loads.toByte())
        }, changes::add)
        owner.update(value); runCurrent()
        value = value.copy(resumeIdentity = value.resumeIdentity!!.copy(sourceGeneration = 2))
        owner.update(value); runCurrent(); val published = changes.size
        gate.complete(Unit); runCurrent(); assertEquals(published, changes.size)
        owner.close(); value = media(); owner.update(value); runCurrent(); assertEquals(published, changes.size)
    }
    @Test fun progressIntentAndMetadataUpdatesDoNotReloadUnchangedArtwork() = runTest {
        var value = media(); var loads = 0; var snapshotReads = 0
        val owner = NaviampExternalPlaybackArtworkController(backgroundScope, { snapshotReads++; value }, { loads++; byteArrayOf(1) }, {})
        owner.update(value); runCurrent()
        val initialSnapshotReads = snapshotReads
        repeat(100) {
            value = value.copy(positionMillis = it * 1_000L, current = value.current!!.copy(title = "Title $it"),
                resumeIdentity = value.resumeIdentity!!.copy(intentGeneration = it.toLong()))
            owner.update(value)
        }
        runCurrent(); assertEquals(1, loads)
        assertEquals(initialSnapshotReads, snapshotReads)
        val planner = NaviampExternalPlaybackPublicationPlanner()
        planner.plan(media())
        val plan = planner.plan(media().copy(resumeIdentity = media().resumeIdentity!!.copy(sourceGeneration = 2)))
        assertTrue(plan.sessionContent); assertTrue(plan.playbackState); assertTrue(plan.browseCatalog); assertTrue(plan.notification)
        owner.close()
    }
    @Test fun failedOrEmptyArtworkAndRemovedMediaKeepTheClearedState() = runTest {
        for (failed in listOf(false, true)) {
            var value = media(); val changes = mutableListOf<ByteArray?>()
            val owner = NaviampExternalPlaybackArtworkController(backgroundScope, { value }, { if (failed) error("image failure") else byteArrayOf() }, changes::add)
            owner.update(value); runCurrent(); assertEquals(listOf<ByteArray?>(null), changes)
            value = value.copy(current = null); owner.update(value); assertEquals(listOf<ByteArray?>(null, null), changes)
            owner.close()
        }
    }
}
