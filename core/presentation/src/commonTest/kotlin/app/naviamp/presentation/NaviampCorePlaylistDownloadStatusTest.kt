package app.naviamp.presentation

import app.naviamp.app.NaviampConnectionPhase
import app.naviamp.app.NaviampConnectionRuntimeState
import app.naviamp.domain.Playlist
import app.naviamp.domain.Track
import app.naviamp.domain.cache.DownloadJobUpdate
import app.naviamp.domain.cache.DownloadStorageLimitExceededMessage
import app.naviamp.domain.provider.MediaProvider
import app.naviamp.ui.NaviampPlaylistDetailActionRequest
import app.naviamp.ui.NaviampPlaylistDetailCommand
import app.naviamp.ui.toSharedMediaItemUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Playlists header must not keep announcing a download that already finished. */
@OptIn(ExperimentalCoroutinesApi::class)
class NaviampCorePlaylistDownloadStatusTest {
    private val starting = "Starting download for Core Playlist..."

    private fun TestScope.core(
        provider: MediaProvider = FakeCoreMediaProvider(),
        mobileData: Boolean = false,
        transfer: NaviampCoreDownloadTransferPort,
    ): NaviampCore {
        val defaults = fakeCoreServices(provider)
        val initial = NaviampCoreInitialState().let { state ->
            state.copy(
                connection = NaviampConnectionRuntimeState(phase = NaviampConnectionPhase.Connected, sourceId = "source"),
                product = state.product.copy(shell = state.product.shell.copy(
                    connectionSettings = state.product.shell.connectionSettings.copy(currentSourceId = "source"))),
            )
        }
        return NaviampCore.create(this, defaults.copy(downloads = defaults.downloads.copy(transfer = transfer,
            network = NaviampCoreMobileNetworkPort { mobileData })), initial,
            onAsyncFailure = { command, cause -> throw IllegalStateException("Core command failed: $command", cause) })
    }

    // Translated messages load resources off the test scheduler, so let real time pass between idles.
    private fun TestScope.settle(core: NaviampCore, rounds: Int = 300, untilChanged: Boolean = true) {
        repeat(rounds) {
            advanceUntilIdle()
            if (untilChanged && core.state.value.shell.playlists.status != starting) return
            Thread.sleep(10)
        }
    }

    private suspend fun NaviampCore.download(provider: FakeCoreMediaProvider = FakeCoreMediaProvider()) = execute(
        NaviampCoreCommand.Playlists.Detail(NaviampPlaylistDetailActionRequest(
            provider.playlist.toSharedMediaItemUi(coverArtUrl = { null }), NaviampPlaylistDetailCommand.Download(null))))

    @Test fun completedDownloadClearsTheStartingStatus() = runTest {
        val gate = CompletableDeferred<Unit>()
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, update ->
            gate.await()
            update(DownloadJobUpdate.Completed)
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        runCurrent()
        assertEquals(starting, core.state.value.shell.playlists.status)

        gate.complete(Unit)
        settle(core)
        assertNull(core.state.value.shell.playlists.status)
        core.close()
    }

    @Test fun failedDownloadReportsTheFailureForThePlaylist() = runTest {
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Disk full") })
        core.download()
        settle(core)
        assertEquals("Could not download Core Playlist.", core.state.value.shell.playlists.status)
        assertEquals("Could not download Core Playlist.", core.state.value.shell.playlistDetail.status)
        core.close()
    }

    @Test fun storageLimitFailureNamesTheReason() = runTest {
        val core = core(transfer = NaviampCoreDownloadTransferPort { request, _, update ->
            update(DownloadJobUpdate.Started)
            update(DownloadJobUpdate.Failed(request.tracks.first().id.value, DownloadStorageLimitExceededMessage))
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        settle(core)
        val expected = "Could not download Core Playlist. The download storage limit was reached."
        assertEquals(expected, core.state.value.shell.playlists.status)
        assertEquals(expected, core.state.value.shell.playlistDetail.status)
        core.close()
    }

    @Test fun blockedStartReportsTheFailureInsteadOfStarting() = runTest {
        val core = core(mobileData = true, transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Started") })
        core.download()
        settle(core)
        assertEquals("Could not download Core Playlist. Downloads over mobile data are turned off.",
            core.state.value.shell.playlists.status)
        assertEquals(emptyList(), core.state.value.shell.downloads.jobs)
        core.close()
    }

    @Test fun aNewerStatusIsNeverReplacedByTheOutcome() = runTest {
        val gate = CompletableDeferred<Unit>()
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, _ ->
            gate.await()
            error("Disk full")
        })
        core.download()
        runCurrent()
        core.execute(NaviampCoreCommand.Playlists.Refresh)
        advanceUntilIdle()
        gate.complete(Unit)
        settle(core, rounds = 50, untilChanged = false)
        assertNull(core.state.value.shell.playlists.status)
        core.close()
    }

    @Test fun cancelledDownloadClearsTheStartingStatus() = runTest {
        val never = CompletableDeferred<Unit>()
        val core = core(transfer = NaviampCoreDownloadTransferPort { _, _, _ ->
            never.await()
            NaviampCoreDownloadTransferResult(refreshDownloads = false)
        })
        core.download()
        runCurrent()
        val job = core.state.value.shell.downloads.jobs.single()
        core.execute(NaviampCoreCommand.Downloads.CancelJob(job.id))
        settle(core)
        assertNull(core.state.value.shell.playlists.status)
        core.close()
    }

    @Test fun unreadablePlaylistNeverLeavesTheStartingStatus() = runTest {
        val provider = object : MediaProvider by FakeCoreMediaProvider() {
            override suspend fun playlistTracks(playlistId: String): List<Track> = error("Connection timed out")
            override suspend fun playlists(limit: Int): List<Playlist> = emptyList()
        }
        val core = core(provider, transfer = NaviampCoreDownloadTransferPort { _, _, _ -> error("Not started") })
        core.download()
        settle(core)
        assertNull(core.state.value.shell.playlists.status)
        assertEquals("Connection timed out", core.state.value.shell.playlistDetail.status)
        core.close()
    }
}
